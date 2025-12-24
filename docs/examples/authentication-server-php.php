<?php
/**
 * Dogecoin Wallet Authentication Server
 * 
 * This server generates challenge QR codes and verifies signatures
 * to grant access to physical or digital resources.
 * 
 * @author Dogecoin Wallet Team
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Load allowed addresses from file
function loadAllowedAddresses() {
    $config = json_decode(file_get_contents('allowed_addresses.json'), true);
    return $config['allowed_addresses'] ?? [];
}

// Generate unique challenge
function generateChallenge() {
    $timestamp = time();
    $random = bin2hex(random_bytes(16));
    $challenge = $timestamp . '-' . $random;
    $challengeHash = hash('sha256', $challenge);
    
    // Store challenge in session or database (for production)
    session_start();
    $_SESSION['challenge'] = $challenge;
    $_SESSION['challenge_hash'] = $challengeHash;
    $_SESSION['timestamp'] = $timestamp;
    
    return [
        'challenge' => $challengeHash,
        'message' => $challenge
    ];
}

// Verify Dogecoin signature
function verifySignature($address, $message, $signature) {
    // Use dogecoin-signature-tool PHP library or similar
    // This is a simplified example
    
    $messagePrefix = "Dogecoin Signed Message:\n";
    $fullMessage = $messagePrefix . $message;
    
    // Decode base64 signature
    $sigBytes = base64_decode($signature);
    if ($sigBytes === false || strlen($sigBytes) !== 65) {
        return false;
    }
    
    // Extract recovery ID (first byte)
    $recId = ord($sigBytes[0]) - 27;
    if ($recId < 0 || $recId > 3) {
        return false;
    }
    
    // Extract R and S (next 64 bytes)
    $r = substr($sigBytes, 1, 32);
    $s = substr($sigBytes, 33, 32);
    
    // Verify signature using secp256k1
    // In production, use a proper library like:
    // - dogecoin-signature-tool
    // - bitcoin-php
    // - secp256k1-php
    
    // Simplified verification (use proper library in production)
    return verifyDogecoinSignature($address, $fullMessage, $r, $s, $recId);
}

function verifyDogecoinSignature($address, $message, $r, $s, $recId) {
    // This is a placeholder - use proper verification library
    // Example using secp256k1 or dogecoin-signature-tool
    
    // Hash message
    $messageHash = hash('sha256', hash('sha256', $message, true), true);
    
    // Recover public key from signature
    // Use secp256k1 library to recover public key
    // Then verify it matches the address
    
    // For production, use: composer require dogecoin/dogecoin-signature-tool
    return true; // Placeholder
}

// Handle requests
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

if ($_SERVER['REQUEST_METHOD'] === 'GET' && $path === '/generate-challenge') {
    $response = generateChallenge();
    echo json_encode($response);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && $path === '/verify-signature') {
    $input = json_decode(file_get_contents('php://input'), true);
    
    if (!isset($input['address']) || !isset($input['message']) || !isset($input['signature'])) {
        http_response_code(400);
        echo json_encode(['error' => 'Missing required fields']);
        exit;
    }
    
    $address = $input['address'];
    $message = $input['message'];
    $signature = $input['signature'];
    
    // Check if address is allowed
    $allowedAddresses = loadAllowedAddresses();
    if (!in_array($address, $allowedAddresses)) {
        http_response_code(403);
        echo json_encode(['error' => 'Address not allowed']);
        exit;
    }
    
    // Verify signature
    if (!verifySignature($address, $message, $signature)) {
        http_response_code(401);
        echo json_encode(['error' => 'Invalid signature']);
        exit;
    }
    
    // Grant access
    $response = [
        'status' => 'granted',
        'address' => $address,
        'action' => 'access_granted'
    ];
    
    echo json_encode($response);
    
    // Perform action (open door, validate ticket, etc.)
    performAction($address);
    exit;
}

// Serve QR code page
if ($_SERVER['REQUEST_METHOD'] === 'GET' && $path === '/') {
    ?>
    <!DOCTYPE html>
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
                        // Perform action (e.g., open door, unlock content)
                    } else {
                        document.getElementById('result').innerHTML = 
                            '<h2 style="color:red">Access Denied: ' + (data.error || 'Invalid') + '</h2>';
                    }
                })
                .catch(err => {
                    document.getElementById('result').innerHTML = 
                        '<h2 style="color:red">Error: ' + err.message + '</h2>';
                });
            }
        </script>
    </body>
    </html>
    <?php
    exit;
}

function performAction($address) {
    // Implement your action here:
    // - Open door via GPIO/API
    // - Validate ticket in database
    // - Unlock digital content
    // - Turn on light
    error_log("Access granted for address: " . $address);
}
?>

