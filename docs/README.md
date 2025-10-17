# Dogecoin Wallet Website

A modern, responsive website for the Dogecoin Wallet Android application.

## 📱 Current Version Information

- **Version**: v1.0 (Version Code: 64)
- **Target SDK**: API 35 (Android 15)
- **Minimum SDK**: API 24 (Android 7.0)
- **Build Size**: ~14.3 MB (minified AAB)
- **Status**: Available on Google Play Store
- **Android 15 Compatible**: ✅ Fixed BOOT_COMPLETED foreground service restrictions
- **Crash Fixes**: ✅ Fixed race condition crashes during wallet startup and synchronization
- **New Features**: ✅ Added comprehensive Accounting Reports with CSV, JSON, and PDF export

## 📱 App Demo & RadioDoge Integration

<div style="display: flex; flex-wrap: wrap; gap: 20px; justify-content: center; align-items: center;">
  <div style="text-align: center;">
    <h4>Dogecoin Wallet App</h4>
    <img src="images/dogecoinwallet.png" width="300" alt="Dogecoin Wallet App" />
  </div>
  <div style="text-align: center;">
    <h4>RadioDoge Integration</h4>
    <img src="images/back-phone-radiodoge.png" width="300" alt="RadioDoge Integration" />
  </div>
</div>

### 🌐 More Information & Documentation
Visit **[dogecoinwallet.org](https://dogecoinwallet.org)** for comprehensive documentation, user guides, and the latest updates about the Dogecoin Wallet.

## 🚀 Features

- **Modern Design**: Clean, Dogecoin-themed design with amber/yellow color scheme
- **Responsive**: Works perfectly on desktop, tablet, and mobile devices
- **Interactive**: Smooth animations, hover effects, and scroll animations
- **Comprehensive**: Complete documentation, legal pages, and user guides
- **SEO Optimized**: Proper meta tags, structured content, and semantic HTML

## 📊 Accounting Reports Feature

The Dogecoin Wallet now includes comprehensive accounting and reporting capabilities:

- **Transaction Analytics**: Generate detailed reports of all wallet transactions
- **Multiple Export Formats**: Export data to CSV, JSON, or professionally formatted PDF
- **Date Range Filtering**: Select specific time periods for targeted reporting
- **Visual Timeline Charts**: Interactive charts showing transaction flow over time
- **Complete Transaction Data**: Full transaction details including:
  - Transaction IDs (non-truncated)
  - Transaction types (Send, Receive, Internal)
  - Precise timestamps with UTC formatting
  - Source and destination addresses with labels
  - Amounts and fees with 8 decimal precision
  - OP_RETURN notes and metadata
- **Professional PDF Reports**: Landscape-formatted reports with Dogecoin branding
- **Smart Classification**: Automatic detection of internal transactions
- **Net Amount Calculation**: Accurate reporting of sent amounts (excluding fees)
- **Easy Sharing**: Share reports via any Android sharing method

## 📁 Structure

```
website/
├── index.html              # Main homepage
├── styles.css              # CSS styles
├── script.js               # JavaScript functionality
├── images/                 # Images and screenshots
│   └── placeholder.txt     # Image requirements
└── docs/                   # Documentation pages
    ├── privacy-policy.html
    ├── terms-of-service.html
    ├── license.html
    ├── user-guide.html
    ├── developer-guide.html
    └── disclaimer.html
```

## 🎨 Design Features

### Color Scheme
- Primary: #ffc107 (Amber)
- Secondary: #ff8f00 (Darker Amber)
- Background: #fafafa (Light Gray)
- Text: #333 (Dark Gray)

### Typography
- Font: Inter (Google Fonts)
- Responsive font sizes
- Proper line heights for readability

### Layout
- CSS Grid and Flexbox for responsive layouts
- Mobile-first design approach
- Smooth scrolling navigation
- Card-based content sections

## 📱 Sections

1. **Hero Section**: Eye-catching introduction with call-to-action buttons
2. **Features**: Comprehensive feature overview with icons
3. **Screenshots**: App screenshots with descriptions
4. **Download**: Google Play Store links and iOS placeholder
5. **Documentation**: Links to all legal and help documents
6. **Contribute**: Information for developers and contributors
7. **Footer**: Links, contact info, and legal disclaimers

## 🔧 Technical Features

### JavaScript
- Smooth scrolling navigation
- Scroll-triggered animations
- Ripple effects on buttons
- Typing animation for hero title
- Counter animations for statistics
- Parallax effects
- Mobile menu support

### CSS
- CSS Grid for complex layouts
- Flexbox for component alignment
- CSS animations and transitions
- Responsive design with media queries
- Custom properties for theming
- Modern CSS features

### HTML
- Semantic HTML5 elements
- Proper heading hierarchy
- Accessibility attributes
- SEO-friendly structure
- Clean, readable markup

## 📋 Required Images

The website requires several images to be added to the `images/` directory:

### Logos and Icons
- `dogecoin-logo.png` (40x40px)
- `google-play-badge.png` (200x80px)
- `apple-store-icon.png` (40x40px)
- `github-icon.png` (20x20px)
- `twitter-icon.png` (20x20px)
- `favicon.png` (32x32px)

### Screenshots
- `wallet-hero.png` (300x600px)
- `download-hero.png` (300x600px)
- `screenshot-1.png` through `screenshot-6.png` (250x500px each)

## 🏗️ Building the Dogecoin Wallet

### Quick Build Commands

#### **Build Android App Bundle (AAB) for Google Play Console**:
```bash
# Navigate to project root
cd dogecoin-wallet

# Clean previous builds
.\gradlew clean

# Build release AAB
.\gradlew :wallet:bundleRelease
```

#### **Build APK for Testing**:
```bash
# Build debug APK
.\gradlew :wallet:assembleDebug

# Build release APK
.\gradlew :wallet:assembleRelease
```

### 📦 Build Outputs

After building, you'll find:
- **AAB File**: `wallet/build/outputs/bundle/release/wallet-release.aab`
- **APK Files**: `wallet/build/outputs/apk/release/` or `debug/`
- **R8 Mapping**: `wallet/build/outputs/mapping/release/mapping.txt`

### 🛠️ Prerequisites

- **Android Studio**: Version 2023.1.1 or later
- **Android SDK**: API Level 35 (Android 15)
- **Build Tools**: 35.0.0 or later
- **Java**: Java 8 SDK or later
- **Gradle**: 8.12.2 (included with Android Studio)

### 🔧 Current Build Configuration

- **Version Name**: 1.0
- **Version Code**: 61
- **Target SDK**: 35 (Android 15)
- **Minify**: Enabled (R8)
- **Signing**: Release keystore configured
- **File Size**: ~14.3 MB (minified AAB)
- **Android 15 Fix**: BOOT_COMPLETED restrictions resolved

### 📱 Google Play Console Requirements

- **Target SDK**: API 35 ✅
- **Version Code**: Unique and incrementing ✅
- **R8 Mapping**: Upload `mapping.txt` ✅
- **App Bundle**: AAB format ✅
- **Signing**: Release keystore ✅
- **Android 15 Compatibility**: BOOT_COMPLETED restrictions fixed ✅

### 🤖 Android 15 Compatibility Fix

The latest version (v39) includes a critical fix for Android 15 compatibility:

- **Issue**: Android 15+ restricts certain foreground service types from being started by `BOOT_COMPLETED` receivers
- **Solution**: Modified `BootstrapReceiver` to schedule blockchain service with delay on Android 15+
- **Result**: App no longer crashes on Android 15+ devices and passes Google Play Console validation
- **Backward Compatibility**: Maintains full functionality on older Android versions

## 🚀 Website Deployment

1. **Static Hosting**: Upload all files to any static hosting service
2. **GitHub Pages**: Push to GitHub and enable Pages
3. **Netlify**: Connect to GitHub repository for automatic deployment
4. **Vercel**: Deploy directly from GitHub

## 🔗 Links

- **Google Play Store**: https://play.google.com/store/apps/details?id=org.dogecoin.wallet
- **GitHub Repository**: https://github.com/qlpqlp/dogecoin-wallet
- **Source Code**: Open source under GPL v3.0

## 📞 Support

- **GitHub Issues**: https://github.com/qlpqlp/dogecoin-wallet/issues
- **Documentation**: Complete user and developer guides included

## ⚖️ Legal

- **License**: GPL v3.0
- **Privacy Policy**: Comprehensive privacy protection
- **Terms of Service**: Clear usage terms
- **Disclaimer**: Important risk warnings

## 🐕 Much Website, Very Wow!

This website showcases the Dogecoin Wallet with a fun, modern design that reflects the playful nature of the Dogecoin community while maintaining professionalism and trustworthiness.

---

**Made with ❤️ for the Dogecoin community!**
