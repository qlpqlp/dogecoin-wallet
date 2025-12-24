package main

/*
#cgo CFLAGS: -I./libdogecoin/include/dogecoin
#cgo LDFLAGS: -L./libdogecoin/.libs -ldogecoin
#include <libdogecoin.h>
#include <stdlib.h>
*/
import "C"
import (
    "crypto/rand"
    "crypto/sha256"
    "encoding/base64"
    "encoding/hex"
    "encoding/json"
    "fmt"
    "io/ioutil"
    "log"
    "net/http"
    "time"
    "unsafe"
)

// Allowed addresses stored in file
type Config struct {
    AllowedAddresses []string `json:"allowed_addresses"`
}

var config Config

func main() {
    // Load allowed addresses
    loadConfig()
    
    http.HandleFunc("/generate-challenge", generateChallenge)
    http.HandleFunc("/verify-signature", verifySignature)
    http.HandleFunc("/", serveQR)
    
    log.Println("Authentication server starting on :8080")
    log.Fatal(http.ListenAndServe(":8080", nil))
}

func loadConfig() {
    data, err := ioutil.ReadFile("allowed_addresses.json")
    if err != nil {
        log.Fatal("Error reading config:", err)
    }
    json.Unmarshal(data, &config)
    log.Printf("Loaded %d allowed addresses", len(config.AllowedAddresses))
}

func generateChallenge(w http.ResponseWriter, r *http.Request) {
    // Generate unique challenge (timestamp + random)
    challenge := fmt.Sprintf("%d-%s", time.Now().Unix(), generateRandomString(16))
    
    // Hash the challenge for security
    hash := sha256.Sum256([]byte(challenge))
    challengeHash := hex.EncodeToString(hash[:])
    
    response := map[string]string{
        "challenge": challengeHash,
        "message": challenge, // For display
    }
    
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(response)
}

func verifySignature(w http.ResponseWriter, r *http.Request) {
    var req struct {
        Address   string `json:"address"`
        Message   string `json:"message"`
        Signature string `json:"signature"`
    }
    
    if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
        http.Error(w, "Invalid request", http.StatusBadRequest)
        return
    }
    
    // Check if address is allowed
    if !isAddressAllowed(req.Address) {
        http.Error(w, "Address not allowed", http.StatusForbidden)
        return
    }
    
    // Verify signature
    sigBytes, err := base64.StdEncoding.DecodeString(req.Signature)
    if err != nil {
        http.Error(w, "Invalid signature format", http.StatusBadRequest)
        return
    }
    
    // Verify using dogecoin signature verification
    isValid, err := verifyDogecoinSignature(req.Address, req.Message, sigBytes)
    if err != nil || !isValid {
        http.Error(w, "Invalid signature", http.StatusUnauthorized)
        return
    }
    
    // Grant access
    response := map[string]interface{}{
        "status": "granted",
        "address": req.Address,
        "action": "access_granted", // e.g., "door_open", "ticket_valid", "content_unlocked"
    }
    
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(response)
    
    // Perform action (open door, validate ticket, etc.)
    performAction(req.Address)
}

func verifyDogecoinSignature(address, message string, sigBytes []byte) (bool, error) {
    // Use libdogecoin to verify the signature
    // libdogecoin provides verify_message() function
    // See: https://github.com/dogecoinfoundation/libdogecoin
    
    messagePrefix := "Dogecoin Signed Message:\n"
    fullMessage := messagePrefix + message
    
    // Convert Go strings to C strings
    cAddress := C.CString(address)
    cMessage := C.CString(fullMessage)
    cSignature := C.CString(base64.StdEncoding.EncodeToString(sigBytes))
    defer C.free(unsafe.Pointer(cAddress))
    defer C.free(unsafe.Pointer(cMessage))
    defer C.free(unsafe.Pointer(cSignature))
    
    // Call libdogecoin verify_message function
    // Note: Check libdogecoin.h for the exact function name and signature
    // The function may be named verify_message, verify_dogecoin_message, etc.
    // See: https://github.com/dogecoinfoundation/libdogecoin/blob/main/include/dogecoin/libdogecoin.h
    // Returns 1 if valid, 0 if invalid
    result := C.verify_message(cAddress, cMessage, cSignature)
    
    return result == 1, nil
}

func isAddressAllowed(address string) bool {
    for _, allowed := range config.AllowedAddresses {
        if allowed == address {
            return true
        }
    }
    return false
}

func performAction(address string) {
    // Implement your action here:
    // - Open door via GPIO/API
    // - Validate ticket in database
    // - Unlock digital content
    // - Turn on light
    log.Printf("Access granted for address: %s", address)
}

func generateRandomString(length int) string {
    bytes := make([]byte, length)
    rand.Read(bytes)
    return hex.EncodeToString(bytes)
}

func serveQR(w http.ResponseWriter, r *http.Request) {
    // Generate QR code HTML page
    html := `<!DOCTYPE html>
<html>
<head>
    <title>Authentication Terminal</title>
    <script src="https://cdn.jsdelivr.net/npm/qrcode@1.5.3/build/qrcode.min.js"></script>
</head>
<body>
    <h1>Scan QR Code to Authenticate</h1>
    <div id="qrcode"></div>
    <h2>Or Enter Signature Manually:</h2>
    <textarea id="signature" rows="5" cols="50"></textarea><br>
    <input type="text" id="address" placeholder="Your Dogecoin Address"><br>
    <button onclick="verify()">Verify Signature</button>
    <div id="result"></div>
    
    <script>
        let currentChallenge = null;
        
        fetch('/generate-challenge')
            .then(r => r.json())
            .then(data => {
                currentChallenge = data;
                QRCode.toCanvas(document.getElementById('qrcode'), data.challenge);
            });
        
        function verify() {
            const signature = document.getElementById('signature').value;
            const address = document.getElementById('address').value;
            
            fetch('/verify-signature', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    address: address,
                    message: currentChallenge.message,
                    signature: signature
                })
            })
            .then(r => r.json())
            .then(data => {
                if (data.status === 'granted') {
                    document.getElementById('result').innerHTML = 
                        '<h2 style="color:green">Access Granted!</h2>';
                } else {
                    document.getElementById('result').innerHTML = 
                        '<h2 style="color:red">Access Denied</h2>';
                }
            });
        }
    </script>
</body>
</html>`
    w.Header().Set("Content-Type", "text/html")
    w.Write([]byte(html))
}

