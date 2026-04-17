package com.hayaguard.app

object HideReelsJS {

    val HIDE_REELS_SCRIPT = """
        (function() {
            if (window._hayaGuardReelsHider) return;
            window._hayaGuardReelsHider = true;
            
            function isReelsViewerPage() {
                var url = window.location.href || '';
                if (url.indexOf('/reel/') !== -1) return true;
                if (url.indexOf('/reels/') !== -1) return true;
                if (url.indexOf('reel_id=') !== -1) return true;
                var fullscreenVideo = document.querySelector('video[style*="100%"]');
                if (fullscreenVideo && fullscreenVideo.offsetHeight > window.innerHeight * 0.7) return true;
                return false;
            }
            
            if (isReelsViewerPage()) return;
            
            var quranQuotes = [
                { quote: "And We have certainly created man in the best of stature.", reference: "Quran 95:4" },
                { quote: "Indeed, Allah is with those who fear Him and those who are doers of good.", reference: "Quran 16:128" },
                { quote: "So verily, with the hardship, there is relief.", reference: "Quran 94:5" },
                { quote: "And whoever puts their trust in Allah, then He will suffice him.", reference: "Quran 65:3" },
                { quote: "Indeed, the hearing, the sight and the heart - about all those one will be questioned.", reference: "Quran 17:36" },
                { quote: "And say, My Lord, increase me in knowledge.", reference: "Quran 20:114" },
                { quote: "Allah does not burden a soul beyond that it can bear.", reference: "Quran 2:286" },
                { quote: "And He found you lost and guided you.", reference: "Quran 93:7" },
                { quote: "Indeed, prayer prohibits immorality and wrongdoing.", reference: "Quran 29:45" },
                { quote: "And whoever relies upon Allah - then He is sufficient for him.", reference: "Quran 65:3" },
                { quote: "Do not lose hope, nor be sad.", reference: "Quran 3:139" },
                { quote: "Verily, in the remembrance of Allah do hearts find rest.", reference: "Quran 13:28" },
                { quote: "And your Lord is the Forgiving, Full of Mercy.", reference: "Quran 18:58" },
                { quote: "My mercy encompasses all things.", reference: "Quran 7:156" },
                { quote: "So remember Me; I will remember you.", reference: "Quran 2:152" }
            ];
            
            var processedReels = new WeakSet();
            var usedIndices = [];
            
            function getNextQuote() {
                if (usedIndices.length >= quranQuotes.length) {
                    usedIndices = [];
                }
                var idx;
                do {
                    idx = Math.floor(Math.random() * quranQuotes.length);
                } while (usedIndices.indexOf(idx) !== -1);
                usedIndices.push(idx);
                return quranQuotes[idx];
            }
            
            function createQuoteHTML(q, w, h) {
                var quote = q.quote || quranQuotes[0].quote;
                var ref = q.reference || quranQuotes[0].reference;
                var pattern = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80' viewBox='0 0 80 80'%3E%3Cg fill='%23ffffff' fill-opacity='0.15'%3E%3Cpath d='M40 0L20 20L40 40L20 60L40 80L60 60L40 40L60 20L40 0zM0 40L20 20L0 0L0 40zM80 40L60 60L80 80L80 40zM0 80L20 60L0 40L0 80zM80 0L60 20L80 40L80 0z'/%3E%3C/g%3E%3C/svg%3E";
                return '<div style="width:100%;height:100%;min-height:' + h + 'px;background:linear-gradient(135deg,#0d7377 0%,#14a3a8 50%,#44c4c9 100%);border-radius:16px;display:flex;flex-direction:column;justify-content:center;align-items:center;padding:24px;box-sizing:border-box;position:relative;overflow:hidden;">' +
                    '<div style="position:absolute;top:0;left:0;right:0;bottom:0;background-image:url(' + "'" + pattern + "'" + ');background-size:80px 80px;opacity:0.4;"></div>' +
                    '<div style="position:absolute;top:-30px;right:-30px;width:120px;height:120px;border:2px solid rgba(255,255,255,0.2);border-radius:50%;"></div>' +
                    '<div style="position:absolute;bottom:-40px;left:-40px;width:150px;height:150px;border:2px solid rgba(255,255,255,0.15);border-radius:50%;"></div>' +
                    '<div style="position:relative;z-index:1;display:flex;flex-direction:column;align-items:center;">' +
                    '<svg width="36" height="36" viewBox="0 0 100 100" style="margin-bottom:16px;"><polygon points="50,5 61,40 98,40 68,62 79,97 50,75 21,97 32,62 2,40 39,40" fill="rgba(255,255,255,0.9)"/><polygon points="50,20 56,40 78,40 60,52 66,72 50,60 34,72 40,52 22,40 44,40" fill="#0d7377"/></svg>' +
                    '<div style="color:#ffffff;font-size:15px;line-height:1.6;text-align:center;font-family:Georgia,serif;font-style:italic;max-width:92%;margin-bottom:12px;text-shadow:0 1px 2px rgba(0,0,0,0.2);">"' + quote + '"</div>' +
                    '<div style="color:rgba(255,255,255,0.95);font-size:12px;font-family:system-ui,sans-serif;font-weight:600;background:rgba(255,255,255,0.2);padding:4px 12px;border-radius:12px;">' + ref + '</div>' +
                    '<div style="margin-top:16px;border-top:1px solid rgba(255,255,255,0.2);padding-top:12px;width:100%;text-align:center;">' +
                    '<span style="color:rgba(255,255,255,0.8);font-size:10px;font-family:system-ui,sans-serif;">Protected by HayaGuard</span>' +
                    '</div></div></div>';
            }
            
            function isReelElement(el) {
                if (!el) return false;
                var html = el.innerHTML || '';
                var text = el.innerText || '';
                var ariaLabel = el.getAttribute('aria-label') || '';
                if (ariaLabel.indexOf('View reel') !== -1) return true;
                if (html.indexOf('View reel') !== -1) return true;
                if (html.indexOf('aria-label="View reel') !== -1) return true;
                var h2 = el.querySelector('h2');
                if (h2 && h2.innerText && h2.innerText.trim() === 'Reels') return true;
                var spans = el.querySelectorAll('span');
                for (var i = 0; i < spans.length; i++) {
                    if (spans[i].innerText && spans[i].innerText.trim() === 'Reels') {
                        var parent = spans[i].parentElement;
                        if (parent && (parent.tagName === 'H2' || parent.querySelector('h2'))) return true;
                    }
                }
                return false;
            }
            
            function replaceReel(container) {
                if (processedReels.has(container)) return;
                processedReels.add(container);
                container.setAttribute('data-hg-replaced', '1');
                
                var quoteData = getNextQuote();
                var target = container.querySelector('[aria-label*="View reel"]');
                
                if (!target) {
                    var candidates = container.querySelectorAll('div');
                    for (var i = 0; i < candidates.length; i++) {
                        var c = candidates[i];
                        if (c.offsetHeight > 300 && c.offsetWidth > 200) {
                            var vid = c.querySelector('video');
                            var img = c.querySelector('img');
                            if (vid || img) {
                                target = c;
                                break;
                            }
                        }
                    }
                }
                
                if (!target) {
                    var allDivs = container.querySelectorAll('div[data-mcomponent="MContainer"]');
                    for (var i = 0; i < allDivs.length; i++) {
                        if (allDivs[i].offsetHeight > 350) {
                            target = allDivs[i];
                            break;
                        }
                    }
                }
                
                if (target) {
                    var w = target.offsetWidth || 380;
                    var h = target.offsetHeight || 420;
                    target.innerHTML = createQuoteHTML(quoteData, w, h);
                    target.onclick = function(e) { e.preventDefault(); e.stopPropagation(); };
                    target.removeAttribute('aria-label');
                    target.removeAttribute('role');
                    target.removeAttribute('tabindex');
                }
                
                var header = container.querySelector('h2');
                if (header) {
                    var span = header.querySelector('span');
                    if (span && span.innerText.trim() === 'Reels') {
                        span.innerText = 'Quran Reminder';
                    }
                }
            }
            
            function scanAndReplace() {
                if (isReelsViewerPage()) return;
                
                var containers = document.querySelectorAll('[data-tracking-duration-id]');
                for (var i = 0; i < containers.length; i++) {
                    var c = containers[i];
                    if (c.getAttribute('data-hg-replaced')) continue;
                    var playingVideo = c.querySelector('video');
                    if (playingVideo && !playingVideo.paused) continue;
                    if (isReelElement(c)) {
                        replaceReel(c);
                    }
                }
                
                var reelButtons = document.querySelectorAll('[aria-label*="View reel"]');
                for (var i = 0; i < reelButtons.length; i++) {
                    var btn = reelButtons[i];
                    var parent = btn.closest('[data-tracking-duration-id]');
                    if (parent && !parent.getAttribute('data-hg-replaced')) {
                        var playingVideo = parent.querySelector('video');
                        if (playingVideo && !playingVideo.paused) continue;
                        replaceReel(parent);
                    } else if (!parent) {
                        var wrapper = btn.parentElement;
                        while (wrapper && wrapper.offsetHeight < 300) {
                            wrapper = wrapper.parentElement;
                        }
                        if (wrapper && !wrapper.getAttribute('data-hg-replaced')) {
                            wrapper.setAttribute('data-hg-replaced', '1');
                            var quoteData = getNextQuote();
                            var w = btn.offsetWidth || 380;
                            var h = btn.offsetHeight || 420;
                            btn.innerHTML = createQuoteHTML(quoteData, w, h);
                            btn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); };
                            btn.removeAttribute('aria-label');
                        }
                    }
                }
            }
            
            scanAndReplace();
            
            var lastScroll = 0;
            window.addEventListener('scroll', function() {
                var now = Date.now();
                if (now - lastScroll > 200) {
                    lastScroll = now;
                    scanAndReplace();
                }
            }, { passive: true });
            
            var pending = false;
            var observer = new MutationObserver(function() {
                if (pending) return;
                pending = true;
                requestAnimationFrame(function() {
                    scanAndReplace();
                    pending = false;
                });
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        })();
    """.trimIndent()
}
