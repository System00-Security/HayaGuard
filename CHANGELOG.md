# HayaGuard Changelog & Improvement Ideas

## v1.2 - Gender Classifier & Enhanced Content Pipeline

### New Features
- **Gender/Age Classification** - Added ONNX-based gender and age classifier (`GenderClassifier.kt`) for enhanced Haya Mode filtering
- **Improved Content Filter Pipeline** - Enhanced multi-stage ML pipeline with better accuracy and performance

### Improvements
- Performance optimizations across the content filtering stack
- Updated dependencies and SDK targets

---

## v1.1 - Code Quality & Performance Improvements

### Refactoring
- **Extracted ImageEffects utility** - Moved image processing functions (blur, pixelation, placeholder generation) from NSFWWebViewClient to a dedicated `ImageEffects.kt` utility class
- **Consolidated URL validation logic** - Created shared `isExcludedResource()` and `hasImageExtension()` methods to eliminate duplicate pattern matching between `shouldPassthrough` and `isImageUrl`
- **Created AppConstants** - Centralized all magic numbers, timeouts, and thresholds into `AppConstants.kt` for easier maintenance and configuration

### Memory & Stability
- **Fixed WebView memory leak** - Changed direct WebView reference to WeakReference to prevent memory leaks when activities are destroyed
- **GPU crash protection** - Added GPU blacklist for problematic devices (Vivo, Oppo, iTel, Tecno, Infinix) with automatic CPU fallback
- **GPU stability testing** - Runtime GPU stability measurement (3 test runs, 70% threshold) with SharedPreferences persistence

### Performance Optimizations
- **BitmapPool pre-allocation** - Pre-allocate common bitmap sizes (224x224, 320x320) on app startup to reduce allocation overhead
- **Color analysis caching** - Added LRU cache for skin ratio analysis to avoid redundant full color scans on duplicate images
- **Eager GPU initialization** - GPU/TFLite interpreter initialized in background during Application.onCreate instead of blocking on first image

### UI/UX
- **Islamic-themed splash screen** - New gradient background (#17A79A to #095651) with decorative ornaments
- **Updated app icon** - Teal/gold Islamic theme colors matching splash screen
- **Fixed status bar overlap** - Added fitsSystemWindows for proper splash screen layout

### Code Metrics
- NSFWWebViewClient reduced from 1454 to 1165 lines (~20% reduction)
- Added 2 new utility files: `ImageEffects.kt`, `AppConstants.kt`

---

## Future Improvement Ideas

### High Priority
- [ ] Further split NSFWWebViewClient into smaller modules (JavaScript injection, response handling)
- [ ] Add proper error handling with user-friendly messages
- [ ] Implement proper lifecycle management for coroutine scopes

### Medium Priority
- [ ] Add unit tests for ImageEffects and URL validation
- [ ] Create dedicated network layer abstraction
- [ ] Implement proper logging system with log levels

### Low Priority
- [ ] Add ProGuard rules optimization
- [ ] Consider migrating to Kotlin Flows for reactive processing
- [ ] Add performance metrics collection
