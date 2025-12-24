# Metal Pay Connect SDK Setup

This directory should contain the Metal Pay Connect SDK for offline use in the Android app.

## Option 1: Use CDN (Recommended)

The app will automatically try to load the SDK from CDN. No local file is required.

The app tries these CDN URLs in order:
1. `https://unpkg.com/metal-pay-connect-js@latest/dist/index.cjs`
2. `https://cdn.jsdelivr.net/npm/metal-pay-connect-js@latest/dist/index.cjs`
3. `https://unpkg.com/metal-pay-connect-js@latest/dist/index.js`
4. `https://cdn.jsdelivr.net/npm/metal-pay-connect-js@latest/dist/index.js`

## Option 2: Bundle SDK Locally

If you want to bundle the SDK locally for offline use:

### Step 1: Download the SDK

```bash
cd wallet/assets
npm pack metal-pay-connect-js
tar -xzf metal-pay-connect-js-*.tgz
```

### Step 2: Create a Browser Bundle

The SDK requires dependencies (`lodash-es` and `qs`). You have two options:

#### Option A: Use a Bundler (Recommended)

Create a simple HTML file that loads dependencies from CDN and the SDK:

```html
<!DOCTYPE html>
<html>
<head>
  <script src="https://cdn.jsdelivr.net/npm/lodash@latest/lodash.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/qs@latest/dist/qs.js"></script>
</head>
<body>
  <script>
    // Setup require polyfill
    window.require = function(module) {
      if (module === 'lodash-es') return window._;
      if (module === 'qs') return { default: window.qs };
      throw new Error('Module not found: ' + module);
    };
    window.module = { exports: {} };
  </script>
  <script src="package/dist/index.cjs"></script>
  <script>
    // Export to global
    if (window.module.exports.MetalPayConnect) {
      window.MetalPayConnect = window.module.exports.MetalPayConnect;
    }
  </script>
</body>
</html>
```

#### Option B: Use a Build Tool

Use webpack, rollup, or esbuild to create a browser bundle:

```bash
npm install -D webpack webpack-cli
npm install metal-pay-connect-js lodash qs
```

Create `webpack.config.js`:

```javascript
module.exports = {
  entry: './node_modules/metal-pay-connect-js/dist/index.cjs',
  output: {
    filename: 'metal-pay-connect.js',
    library: 'MetalPayConnect',
    libraryTarget: 'umd',
    globalObject: 'this'
  },
  externals: {
    'lodash-es': '_',
    'qs': 'qs'
  }
};
```

Build:
```bash
npx webpack
cp dist/metal-pay-connect.js wallet/assets/
```

### Step 3: Place in Assets

Copy the bundled file to `wallet/assets/metal-pay-connect.js`

The app will automatically try to load from `file:///android_asset/metal-pay-connect.js` first before falling back to CDN.

## Current Status

The app is configured to:
1. Try loading from local assets (`metal-pay-connect.js`)
2. Fallback to CDN URLs if local file doesn't exist
3. Load dependencies (lodash and qs) from CDN when using CommonJS format

## Troubleshooting

If the SDK fails to load:
1. Check internet connection (for CDN fallback)
2. Verify the SDK file exists in `wallet/assets/` if using local bundle
3. Check WebView console logs for JavaScript errors
4. Ensure dependencies (lodash, qs) can be loaded from CDN

## Package Information

- **Package Name**: `metal-pay-connect-js`
- **Latest Version**: Check with `npm view metal-pay-connect-js version`
- **Documentation**: https://connect-docs.metalpay.com/docs/authentication-flow
- **Example**: https://github.com/MetalPay/metal-pay-connect-example-nextjs-app-router

