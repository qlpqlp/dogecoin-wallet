# Authentication Server Integration Examples

This directory contains complete server-side code examples for integrating Dogecoin Wallet Authentication into your systems.

## Files

- `authentication-server-go.go` - Complete Go server implementation
- `authentication-server-php.php` - Complete PHP server implementation
- `authentication-server-nodejs.js` - Complete Node.js/Express server implementation
- `allowed_addresses.json` - Example configuration file with allowed Dogecoin addresses

## Quick Start

### Go Server

This example uses [libdogecoin](https://github.com/dogecoinfoundation/libdogecoin) via CGO for signature verification.

1. Build libdogecoin first:
```bash
# Clone and build libdogecoin
git clone https://github.com/dogecoinfoundation/libdogecoin.git
cd libdogecoin
./autogen.sh
./configure --disable-net --disable-tools
make

# The library will be in ./.libs/libdogecoin.a
# Headers are in ./include/dogecoin/
```

2. Update `allowed_addresses.json` with your addresses

3. Compile with CGO enabled:
```bash
# Set CGO flags to point to libdogecoin
export CGO_CFLAGS="-I./libdogecoin/include/dogecoin"
export CGO_LDFLAGS="-L./libdogecoin/.libs -ldogecoin"

# Build with CGO enabled
CGO_ENABLED=1 go build -o auth-server authentication-server-go.go

# Run the server
./auth-server
```

4. Access: `http://localhost:8080`

**Important Notes:**
- Check `libdogecoin.h` for the exact function name and signature. The API may use different function names.
- Refer to the [libdogecoin documentation](https://github.com/dogecoinfoundation/libdogecoin) for the latest API reference.
- Alternatively, you can use Go bindings for libdogecoin if available from [@dogeorg](https://github.com/dogeorg). Check for official Go bindings at the Dogecoin Foundation repositories.

### PHP Server

1. Install dependencies (if using Composer):
```bash
composer require dogecoin/dogecoin-signature-tool
```

2. Update `allowed_addresses.json` with your addresses
3. Place files in your web server directory
4. Access: `http://localhost/authentication-server-php.php`

### Node.js Server

1. Install dependencies:
```bash
npm install express qrcode
npm install dogecoin-signature-tool
```

2. Update `allowed_addresses.json` with your addresses
3. Run: `node authentication-server-nodejs.js`
4. Access: `http://localhost:8080`

## Configuration

Edit `allowed_addresses.json` to add your allowed Dogecoin addresses:

```json
{
  "allowed_addresses": [
    "D7Y55r3b3vQBvy2g3N9Rh41mu1xaBC9KzY",
    "D9zJ4C9hE4L7xM8vN2pQ5rT6wY8zA1bC3d"
  ]
}
```

## API Endpoints

### GET /generate-challenge
Generates a unique challenge message and returns it as JSON:
```json
{
  "challenge": "hash_of_challenge",
  "message": "timestamp-random_string"
}
```

### POST /verify-signature
Verifies a signature and grants access:
```json
{
  "address": "D7Y55r3b3vQBvy2g3N9Rh41mu1xaBC9KzY",
  "message": "timestamp-random_string",
  "signature": "base64_encoded_signature"
}
```

Response:
```json
{
  "status": "granted",
  "address": "D7Y55r3b3vQBvy2g3N9Rh41mu1xaBC9KzY",
  "action": "access_granted"
}
```

## Use Cases

- **Physical Access Control**: Open doors, gates, or access control systems
- **Ticket Validation**: Validate event tickets, cinema passes, or transportation tickets
- **Digital Content Access**: Unlock premium content, software licenses, or digital services
- **IoT Device Control**: Control smart devices like lights, appliances, or IoT systems

## Security Best Practices

1. **Challenge Expiration**: Set expiration times for challenges (e.g., 5 minutes)
2. **Rate Limiting**: Implement rate limiting to prevent brute force attacks
3. **HTTPS Only**: Always use HTTPS in production
4. **Address Whitelist**: Maintain a secure whitelist of allowed addresses
5. **Logging**: Log all authentication attempts for security auditing
6. **Signature Verification**: Always use proper cryptographic libraries

## Notes

- The Go example uses [libdogecoin](https://github.com/dogecoinfoundation/libdogecoin) via CGO for signature verification
- PHP and Node.js examples use simplified verification - in production, use proper libraries like `dogecoin-signature-tool` or libdogecoin bindings
- Store challenges in Redis or a database for production use
- Implement proper error handling and logging
- Add rate limiting and security measures before deploying
- For Go, you can also check for official Go bindings at [@dogeorg](https://github.com/dogeorg) repositories

