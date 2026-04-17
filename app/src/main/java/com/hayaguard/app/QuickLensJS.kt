package com.hayaguard.app

import android.webkit.JavascriptInterface

class QuickLensInterface(private val onImageLongPress: (String) -> Unit) {

    @JavascriptInterface
    fun onImageSelected(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            onImageLongPress(imageUrl)
        }
    }
}

object QuickLensJS {
    const val LONG_PRESS_SCRIPT = """
        (function() {
            if (window._quickLensInitialized) return;
            window._quickLensInitialized = true;
            
            var longPressTimer = null;
            var longPressDuration = 500;
            var startX = 0;
            var startY = 0;
            var moveThreshold = 10;
            
            function isReactionArea(element) {
                if (!element) return false;
                var current = element;
                for (var i = 0; i < 10 && current && current !== document.body; i++) {
                    var role = current.getAttribute && current.getAttribute('role');
                    var ariaLabel = (current.getAttribute && current.getAttribute('aria-label') || '').toLowerCase();
                    var sigil = current.getAttribute && current.getAttribute('data-sigil') || '';
                    var className = current.className || '';
                    var id = current.id || '';
                    
                    if (role === 'button' || role === 'toolbar' || role === 'menubar') {
                        return true;
                    }
                    if (ariaLabel.indexOf('like') !== -1 || ariaLabel.indexOf('love') !== -1 || ariaLabel.indexOf('haha') !== -1 || ariaLabel.indexOf('wow') !== -1 || ariaLabel.indexOf('sad') !== -1 || ariaLabel.indexOf('angry') !== -1 || ariaLabel.indexOf('care') !== -1 || ariaLabel.indexOf('react') !== -1 || ariaLabel.indexOf('comment') !== -1 || ariaLabel.indexOf('share') !== -1) {
                        return true;
                    }
                    if (sigil.indexOf('like') !== -1 || sigil.indexOf('reaction') !== -1 || sigil.indexOf('comment') !== -1 || sigil.indexOf('share') !== -1 || sigil.indexOf('ufi') !== -1 || sigil.indexOf('feed_action') !== -1) {
                        return true;
                    }
                    if (className.indexOf('reaction') !== -1 || className.indexOf('ufi') !== -1 || className.indexOf('footer') !== -1 || className.indexOf('action') !== -1) {
                        return true;
                    }
                    current = current.parentElement;
                }
                return false;
            }
            
            function findValidImage(element) {
                if (!element) return null;
                
                if (element.tagName === 'IMG' && element.src) {
                    var src = element.src.toLowerCase();
                    if (src.indexOf('emoji') !== -1 || src.indexOf('rsrc.php') !== -1) {
                        return null;
                    }
                    var rect = element.getBoundingClientRect();
                    if (rect.width >= 80 && rect.height >= 80) {
                        return element.src;
                    }
                }
                
                var img = element.querySelector && element.querySelector('img');
                if (img && img.src) {
                    var imgSrc = img.src.toLowerCase();
                    if (imgSrc.indexOf('emoji') === -1 && imgSrc.indexOf('rsrc.php') === -1) {
                        var imgRect = img.getBoundingClientRect();
                        if (imgRect.width >= 80 && imgRect.height >= 80) {
                            return img.src;
                        }
                    }
                }
                
                if (element.parentElement && element.parentElement !== document.body) {
                    return findValidImage(element.parentElement);
                }
                
                return null;
            }
            
            function handleTouchStart(e) {
                var touch = e.touches[0];
                startX = touch.clientX;
                startY = touch.clientY;
                
                var target = document.elementFromPoint(startX, startY);
                
                if (isReactionArea(target)) {
                    return;
                }
                
                var imageUrl = findValidImage(target);
                
                if (imageUrl) {
                    longPressTimer = setTimeout(function() {
                        if (window.QuickLens) {
                            window.QuickLens.onImageSelected(imageUrl);
                        }
                    }, longPressDuration);
                }
            }
            
            function handleTouchMove(e) {
                if (longPressTimer) {
                    var touch = e.touches[0];
                    var deltaX = Math.abs(touch.clientX - startX);
                    var deltaY = Math.abs(touch.clientY - startY);
                    if (deltaX > moveThreshold || deltaY > moveThreshold) {
                        clearTimeout(longPressTimer);
                        longPressTimer = null;
                    }
                }
            }
            
            function handleTouchEnd(e) {
                if (longPressTimer) {
                    clearTimeout(longPressTimer);
                    longPressTimer = null;
                }
            }
            
            document.addEventListener('touchstart', handleTouchStart, { passive: true, capture: false });
            document.addEventListener('touchmove', handleTouchMove, { passive: true, capture: false });
            document.addEventListener('touchend', handleTouchEnd, { passive: true, capture: false });
            document.addEventListener('touchcancel', handleTouchEnd, { passive: true, capture: false });
        })();
    """
}
