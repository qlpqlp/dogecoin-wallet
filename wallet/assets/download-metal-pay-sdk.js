#!/usr/bin/env node
/**
 * Script to download and prepare Metal Pay Connect SDK for Android assets
 * 
 * Usage: node download-metal-pay-sdk.js
 * 
 * This script:
 * 1. Downloads the latest metal-pay-connect-js package from npm
 * 2. Extracts the SDK files
 * 3. Creates a browser-compatible bundle
 * 4. Copies it to the assets directory
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const ASSETS_DIR = __dirname;
const PACKAGE_NAME = 'metal-pay-connect-js';
const OUTPUT_FILE = path.join(ASSETS_DIR, 'metal-pay-connect.js');

console.log('Downloading Metal Pay Connect SDK...');

try {
  // Get latest version
  console.log('Fetching latest version...');
  const version = execSync(`npm view ${PACKAGE_NAME} version`, { encoding: 'utf8' }).trim();
  console.log(`Latest version: ${version}`);

  // Download package
  console.log('Downloading package...');
  execSync(`npm pack ${PACKAGE_NAME}@${version}`, { cwd: ASSETS_DIR, stdio: 'inherit' });

  const tarball = path.join(ASSETS_DIR, `${PACKAGE_NAME}-${version}.tgz`);
  
  // Extract (using tar on Unix, or 7z on Windows)
  console.log('Extracting package...');
  if (process.platform === 'win32') {
    // On Windows, use PowerShell to extract
    execSync(`powershell -Command "Expand-Archive -Path '${tarball.replace(/\\/g, '/')}' -DestinationPath '${ASSETS_DIR}' -Force"`, { stdio: 'inherit' });
  } else {
    execSync(`tar -xzf ${tarball}`, { cwd: ASSETS_DIR, stdio: 'inherit' });
  }

  const packageDir = path.join(ASSETS_DIR, 'package');
  const sdkFile = path.join(packageDir, 'dist', 'index.cjs');
  
  if (!fs.existsSync(sdkFile)) {
    throw new Error(`SDK file not found: ${sdkFile}`);
  }

  // Read the CommonJS version
  let sdkCode = fs.readFileSync(sdkFile, 'utf8');
  
  // Create a browser-compatible wrapper
  // The SDK uses lodash-es and qs, which need to be available
  // For WebView, we'll create a UMD-like wrapper
  const browserBundle = `(function() {
  'use strict';
  
  // Simple polyfills for WebView compatibility
  if (typeof window === 'undefined') {
    var window = {};
  }
  if (typeof document === 'undefined') {
    var document = { createElement: function() { return {}; }, addEventListener: function() {} };
  }
  
  // Load dependencies from CDN if not available
  ${sdkCode}
  
  // Export to global scope for WebView
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = MetalPayConnect;
  }
  if (typeof window !== 'undefined') {
    window.MetalPayConnect = MetalPayConnect;
  }
})();`;

  // Write bundled file
  fs.writeFileSync(OUTPUT_FILE, browserBundle, 'utf8');
  console.log(`✓ SDK bundled to: ${OUTPUT_FILE}`);

  // Cleanup
  console.log('Cleaning up...');
  if (fs.existsSync(tarball)) {
    fs.unlinkSync(tarball);
  }
  if (fs.existsSync(packageDir)) {
    fs.rmSync(packageDir, { recursive: true, force: true });
  }

  console.log('✓ Done! SDK is ready in assets/metal-pay-connect.js');
  console.log('\nNote: The SDK requires lodash-es and qs dependencies.');
  console.log('Consider loading them from CDN or bundling them separately.');

} catch (error) {
  console.error('Error:', error.message);
  process.exit(1);
}

