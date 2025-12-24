# Server Setup Instructions for Exchange API

## Prerequisites

Before setting up the Exchange API server, you need to:

1. **Get Metal Pay Connect API Credentials**:
   - Register for a Metal Pay Connect API key and secret at: [https://www.metalpay.com/metal-pay-connect#form](https://www.metalpay.com/metal-pay-connect#form)
   - You will receive an `API_KEY` and `SECRET_KEY` after registration

2. **Review API Documentation**:
   - Full API documentation and authentication flow: [https://connect-docs.metalpay.com/docs/authentication-flow](https://connect-docs.metalpay.com/docs/authentication-flow)
   - This includes detailed implementation examples for various frameworks

## Option 1: Root Directory Setup (Recommended)

### File Structure:
```
/
├── index.php
└── .htaccess
```

### Steps:
1. Upload `index.php` to the root directory of `https://api.dogecoinwallet.org/`
2. Upload `.htaccess` to the same root directory
3. Set environment variables on your server:
   - `SECRET_KEY`: Your secret key for HMAC signature generation
   - `API_KEY`: Your Metal Pay Connect API key

### Access URL:
- `https://api.dogecoinwallet.org/v1/signature`

---

## Option 2: v1 Folder Structure

### File Structure:
```
/
└── v1/
    ├── index.php (or signature.php)
    └── .htaccess
```

### Steps:
1. Create a `v1` folder in the root directory
2. Upload `index.php` to the `v1` folder (or rename it to `signature.php`)
3. If using `signature.php`, update the path check in the PHP file:
   ```php
   if ($path !== '/v1/signature') {
   ```
4. Set environment variables on your server

### Access URL:
- `https://api.dogecoinwallet.org/v1/signature` (if index.php)
- `https://api.dogecoinwallet.org/v1/signature.php` (if signature.php)

---

## Setting Environment Variables

### For cPanel:
1. Go to **cPanel** → **Select PHP Version** → **Options**
2. Click **Extensions** tab
3. Or use **Environment Variables** section in cPanel

### For Apache (.htaccess):
Add to `.htaccess`:
```apache
SetEnv SECRET_KEY "your-secret-key-here"
SetEnv API_KEY "your-api-key-here"
```

### For PHP-FPM / Nginx:
Add to `php.ini` or server configuration:
```ini
env[SECRET_KEY] = "your-secret-key-here"
env[API_KEY] = "your-api-key-here"
```

### For .env file (if using a framework):
Create `.env` file in root:
```
SECRET_KEY=your-secret-key-here
API_KEY=your-api-key-here
```
Then load it in PHP (requires additional code).

---

## Testing the API

### Using cURL:
```bash
curl -X GET https://api.dogecoinwallet.org/v1/signature
```

### Expected Response:
```json
{
  "apiKey": "your-api-key",
  "signature": "generated-hmac-signature",
  "nonce": "1234567890123"
}
```

### Error Responses:
- **404**: Endpoint not found (check .htaccess and file location)
- **500**: Missing environment variables (check SECRET_KEY and API_KEY)
- **405**: Wrong HTTP method (must be GET)

---

## Security Recommendations

1. **Keep SECRET_KEY secret**: Never expose it in code or logs
2. **Use HTTPS**: Always use SSL/TLS for API endpoints
3. **Rate Limiting**: Consider adding rate limiting to prevent abuse
4. **IP Whitelisting**: Optionally restrict access to known IPs
5. **Logging**: Monitor API access for suspicious activity

---

## Troubleshooting

### Issue: 404 Not Found
- Check that `.htaccess` is uploaded and `RewriteEngine` is enabled
- Verify file permissions (644 for files, 755 for directories)
- Check Apache `mod_rewrite` is enabled

### Issue: 500 Internal Server Error
- Check environment variables are set correctly
- Check PHP error logs
- Verify PHP version (5.6+ required)

### Issue: CORS Errors
- The code already includes CORS headers
- If issues persist, check server CORS configuration

---

## Notes

- The `.htaccess` file routes `/v1/signature` to `index.php` automatically
- The PHP code handles both `/v1/signature` and `/index.php/v1/signature` paths
- Environment variables can be set via cPanel, server config, or `.htaccess`

