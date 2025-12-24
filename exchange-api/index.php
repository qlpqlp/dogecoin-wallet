<?php
/**
 * Metal Pay Connect API Backend for Dogecoin Wallet
 * 
 * This script implements the Metal Pay Connect API specification for generating
 * HMAC signatures required for authentication with the Metal Pay Connect SDK.
 * 
 * Upload this file to your web server (e.g., https://api.dogecoinwallet.org/)
 * 
 * Required environment variables:
 * - SECRET_KEY: Secret key for HMAC signature generation
 * - API_KEY: Your Metal Pay Connect API key
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Get environment variables
$secretKey = getenv('SECRET_KEY');
$apiKey = getenv('API_KEY');

// Check if required environment variables are set
if (empty($secretKey)) {
    http_response_code(500);
    echo json_encode([
        'error' => 'SECRET_KEY environment variable is not set'
    ]);
    exit;
}

if (empty($apiKey)) {
    http_response_code(500);
    echo json_encode([
        'error' => 'API_KEY environment variable is not set'
    ]);
    exit;
}

// Only handle GET requests to /v1/signature endpoint
$requestUri = $_SERVER['REQUEST_URI'];
$path = parse_url($requestUri, PHP_URL_PATH);

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode([
        'error' => 'Method not allowed. Only GET requests are supported.'
    ]);
    exit;
}

// Check if the path matches /v1/signature
// Handle various path formats: /v1/signature, /index.php/v1/signature, /v1/signature.php
$path = rtrim($path, '/'); // Remove trailing slash
if ($path !== '/v1/signature' && 
    $path !== '/index.php/v1/signature' && 
    $path !== '/v1/signature.php' &&
    !preg_match('#^/v1/signature(/|$)#', $path)) {
    http_response_code(404);
    echo json_encode([
        'error' => 'Endpoint not found. Use /v1/signature'
    ]);
    exit;
}

/**
 * Generate a nonce (timestamp-based for simplicity)
 * You can use a more sophisticated method if needed
 */
function generateNonce() {
    return (string) (time() * 1000); // Milliseconds timestamp
}

/**
 * Generate HMAC signature
 * The signature must be generated using: nonce + apiKey
 * 
 * @param string $nonce The nonce value
 * @param string $secretKey The secret key for HMAC
 * @param string $apiKey The API key
 * @return string The HMAC signature in hexadecimal format
 */
function generateHMAC($nonce, $secretKey, $apiKey) {
    $message = $nonce . $apiKey;
    return hash_hmac('sha256', $message, $secretKey);
}

try {
    // Generate nonce
    $nonce = generateNonce();
    
    // Generate HMAC signature
    $signature = generateHMAC($nonce, $secretKey, $apiKey);
    
    // Return response
    $response = [
        'apiKey' => $apiKey,
        'signature' => $signature,
        'nonce' => $nonce
    ];
    
    http_response_code(200);
    echo json_encode($response, JSON_UNESCAPED_SLASHES);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'error' => 'Internal Server Error: ' . $e->getMessage()
    ]);
}
?>

