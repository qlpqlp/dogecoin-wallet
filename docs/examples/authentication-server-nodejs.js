const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const QRCode = require('qrcode');

const app = express();
app.use(express.json());

// Load allowed addresses from file
function loadAllowedAddresses() {
    const config = JSON.parse(fs.readFileSync('allowed_addresses.json', 'utf8'));
    return config.allowed_addresses || [];
}

// Store challenges (in production, use Redis or database)
const challenges = new Map();

// Generate unique challenge
function generateChallenge() {
    const timestamp = Date.now();
    const random = crypto.randomBytes(16).toString('hex');
    const challenge = `${timestamp}-${random}`;
    const challengeHash = crypto.createHash('sha256').update(challenge).digest('hex');
    
    // Store challenge
    challenges.set(challengeHash, {
        message: challenge,
        timestamp: timestamp,
        expires: timestamp + 300000 // 5 minutes
    });
    
    return {
        challenge: challengeHash,
        message: challenge
    };
}

// Verify Dogecoin signature
async function verifySignature(address, message, signature) {
    // Use dogecoin-signature-tool or bitcoinjs-message
    // npm install dogecoin-signature-tool
    
    const messagePrefix = "Dogecoin Signed Message:\n";
    const fullMessage = messagePrefix + message;
    
    try {
        // Decode base64 signature
        const sigBytes = Buffer.from(signature, 'base64');
        if (sigBytes.length !== 65) {
            return false;
        }
        
        // Use dogecoin-signature-tool for verification
        // const { verifyMessage } = require('dogecoin-signature-tool');
        // return verifyMessage(address, fullMessage, signature);
        
        // Simplified verification (use proper library in production)
        return await verifyDogecoinSignature(address, fullMessage, signature);
    } catch (error) {
        console.error('Verification error:', error);
        return false;
    }
}

async function verifyDogecoinSignature(address, message, signature) {
    // Use proper verification library
    // Example: npm install dogecoin-signature-tool
    // const { verifyMessage } = require('dogecoin-signature-tool');
    // return verifyMessage(address, message, signature);
    
    // Placeholder - implement with proper library
    return true;
}

// Routes
app.get('/generate-challenge', (req, res) => {
    const challenge = generateChallenge();
    res.json(challenge);
});

app.post('/verify-signature', async (req, res) => {
    const { address, message, signature } = req.body;
    
    if (!address || !message || !signature) {
        return res.status(400).json({ error: 'Missing required fields' });
    }
    
    // Check if address is allowed
    const allowedAddresses = loadAllowedAddresses();
    if (!allowedAddresses.includes(address)) {
        return res.status(403).json({ error: 'Address not allowed' });
    }
    
    // Verify signature
    const isValid = await verifySignature(address, message, signature);
    if (!isValid) {
        return res.status(401).json({ error: 'Invalid signature' });
    }
    
    // Grant access
    res.json({
        status: 'granted',
        address: address,
        action: 'access_granted'
    });
    
    // Perform action (open door, validate ticket, etc.)
    performAction(address);
});

// Serve QR code page
app.get('/', async (req, res) => {
    const challenge = generateChallenge();
    const qrCodeDataURL = await QRCode.toDataURL(challenge.challenge);
    
    const html = `
    <!DOCTYPE html>
    <html>
    <head>
        <title>Authentication Terminal</title>
    </head>
    <body>
        <h1>Scan QR Code to Authenticate</h1>
        <img src="${qrCodeDataURL}" alt="QR Code">
        <h2>Or Enter Signature Manually:</h2>
        <textarea id="signature" rows="5" cols="50"></textarea><br>
        <input type="text" id="address" placeholder="Your Dogecoin Address"><br>
        <button onclick="verify()">Verify Signature</button>
        <div id="result"></div>
        
        <script>
            let currentChallenge = ${JSON.stringify(challenge)};
            
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
    </html>`;
    
    res.send(html);
});

function performAction(address) {
    // Implement your action here:
    // - Open door via GPIO/API
    // - Validate ticket in database
    // - Unlock digital content
    // - Turn on light
    console.log(`Access granted for address: ${address}`);
}

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
    console.log(`Authentication server running on port ${PORT}`);
    console.log(`Loaded ${loadAllowedAddresses().length} allowed addresses`);
});

