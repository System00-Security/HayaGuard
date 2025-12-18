# HayaGuard

<p align="center">
  <img src="assets/logo.svg" width="120" alt="HayaGuard Logo">
</p>

<p align="center">
  <strong>Reclaim Your Social Media Experience</strong><br>
  Privacy-focused Facebook client with on-device content filtering
</p>

<p align="center">
  <a href="#features">Features</a> |
  <a href="#installation">Installation</a> |
  <a href="#architecture">Architecture</a> |
  <a href="#privacy">Privacy</a> |
  <a href="#license">License</a>
</p>

---

## Overview

HayaGuard is a privacy-focused Facebook client for Android that gives you complete control over your social media experience. It uses on-device machine learning to filter inappropriate content, block trackers, and provide transparency into your feed composition without sending any data to external servers.

| Requirement | Version |
|-------------|---------|
| Minimum SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 36) |

---

## Features

### Content Filtering

Real-time, on-device NSFW detection using a multi-stage machine learning pipeline:

| Stage | Model | Purpose |
|-------|-------|---------|
| Pre-filter | Skin tone analysis | Fast rejection of non-candidate images |
| Primary | NSFW.js (TFLite) | Main classification with 224x224 input |
| Secondary | NudeNet (TFLite) | Validation with 256x256 input |
| Validator | ML Kit | Context validation for false positive reduction |

Key capabilities:
- Adaptive performance based on device capability
- Confidence-based actions with review option
- Strong 32px block pixelation for detected content

### Haya Mode

Gender-aware face blurring for users who prefer modesty filtering:

- ML Kit face detection for accurate localization
- Custom TFLite model for gender classification
- Selective blur based on user preference
- 24px pixelation ensuring faces are unrecognizable

### Feed DNA Analytics

Real-time analysis of Facebook feed composition:

| Category | Description |
|----------|-------------|
| Family and Friends | Posts from people you follow |
| Political/Toxic | Political content and rage-bait |
| Viral/Junk | Clickbait, memes, viral content |
| News/Info | News and educational content |
| Sponsored | Ads and promoted content |

Supported languages: English, Bangla, Arabic, Hindi

### Quick Lens

Long-press OCR for text extraction:

- Multi-script support for Latin and Devanagari
- Touch-to-copy functionality
- Visual bounding box overlay
- Haptic feedback confirmation

### Tracker Blocking

Comprehensive blocking of tracking infrastructure:

- Facebook Pixel and analytics endpoints
- Google Analytics and DoubleClick
- Audience Network scripts
- Cross-site tracking prevention
- Sponsored post removal
- Open in App banner blocking

### Digital Wellbeing

- Daily time limits from 15 minutes to 4 hours
- Bedtime mode with auto-close
- Session statistics and usage tracking
- One-tap override with session logging

---

## Installation

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 26-36
- Gradle 8.x

### Build from Source

```bash
git clone https://github.com/System00-Security/HayaGuard.git
cd HayaGuard
./gradlew assembleDebug
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture

### Adaptive Performance Engine

Automatic device profiling with tiered processing:

| Tier | Threads | Timeout | Behavior |
|------|---------|---------|----------|
| Low-End | 2 | 3s | Show on timeout |
| Mid-Range | 3 | 5s | Blur on timeout |
| High-End | 4 | 8s | Blur on timeout |

Profiling considers RAM, CPU cores, and GPU availability.

### Network Optimization

Cronet engine with HTTP/3 and QUIC support:

- Pre-configured QUIC hints for Facebook CDNs
- HTTP/2 multiplexing
- Brotli compression
- 50MB disk cache
- DNS pre-warming at launch

### Image Processing Pipeline

```
WebView Image Request
         |
         v
    Pre-filter -----> Size check, skin tone analysis
         |
         v
    Placeholder -----> Show loading indicator
         |
         v
    NSFW.js ----------> TFLite inference
         |
         v
    NudeNet ----------> Secondary validation
         |
         v
    Haya Mode --------> Face detection + gender classification
         |
         v
    Result -----------> Original, pixelated, or blurred
```

### Memory Management

- Bitmap pooling to reduce garbage collection pressure
- LRU caches for processed images
- Automatic cleanup on activity lifecycle events

### WebView Security

| Setting | Value |
|---------|-------|
| allowFileAccess | false |
| allowFileAccessFromFileURLs | false |
| allowUniversalAccessFromFileURLs | false |
| mixedContentMode | NEVER_ALLOW |
| safeBrowsingEnabled | true |
| setGeolocationEnabled | false |

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| TensorFlow Lite | 2.14.0 | ML inference engine |
| TensorFlow Lite GPU | 2.14.0 | GPU acceleration |
| TensorFlow Lite Support | 0.4.4 | Image processing |
| ML Kit Face Detection | 16.1.7 | Face detection |
| ML Kit Text Recognition | 16.0.0 | Latin OCR |
| ML Kit Text Recognition Devanagari | 16.0.0 | Bangla/Hindi OCR |
| ML Kit Language ID | 17.0.6 | Language detection |
| Cronet | 18.1.0 | HTTP/3 networking |
| AndroidX Lifecycle | 2.8.7 | Coroutine integration |
| Material Components | 1.12.0 | UI components |

---

## Privacy

### Data Handling

| Data | Storage | Transmission |
|------|---------|--------------|
| Facebook cookies | Device only | Facebook servers |
| Usage statistics | Device only | Never transmitted |
| ML models | Bundled in APK | Never updated remotely |
| Feed analysis | Memory only | Never persisted |
| Detected images | Never stored | Never transmitted |

### Permissions

| Permission | Purpose |
|------------|---------|
| INTERNET | Facebook WebView |
| ACCESS_NETWORK_STATE | Network optimization |
| CAMERA | Photo/video upload |
| READ_MEDIA_IMAGES/VIDEO | Gallery upload |
| VIBRATE | Haptic feedback |

### Privacy Guarantees

- No analytics or telemetry
- No crash reporting to external services
- No user tracking
- No data collection
- No remote model updates
- No cloud processing

---

## Project Structure

```
app/src/main/java/com/hayaguard/app/
├── MainActivity.kt              Main WebView activity
├── NSFWWebViewClient.kt         Image interception and processing
├── ContentFilterPipeline.kt     ML pipeline orchestration
├── NSFWJSDetector.kt            NSFW.js TFLite wrapper
├── NudeNetDetector.kt           NudeNet TFLite wrapper
├── HayaModeProcessor.kt         Face blur processing
├── GenderClassifier.kt          Gender classification model
├── FeedAnalyzer.kt              Feed DNA analysis
├── FeedScraperJS.kt             JavaScript feed scraper
├── QuickLensJS.kt               OCR JavaScript interface
├── TrackerBlocker.kt            Tracker and ad blocking
├── CronetHelper.kt              HTTP/3 networking
├── AdaptivePerformanceEngine.kt Device profiling
├── SettingsManager.kt           Preference management
├── StatsTracker.kt              Usage statistics
├── DashboardActivity.kt         Statistics dashboard
└── SettingsActivity.kt          Settings UI
```

---

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Daily Time Limit | 60 min | Maximum daily usage |
| Bedtime Mode | Disabled | Auto-close time |
| Haya Mode | Disabled | Face blur filter |
| User Gender | Male | For Haya Mode |
| Quick Lens | Enabled | Long-press OCR |

---

## Author

**Abdur Rahman Maheer**

- Twitter: [@0xrahmanmaheer](https://x.com/0xrahmanmaheer)
- Organization: [System00 Security](https://github.com/System00-Security)

---

## License

MIT License

Copyright (c) 2025 System00 Security

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
