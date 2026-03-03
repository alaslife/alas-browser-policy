package com.sun.alasbrowser.utils

object ScriptManager {
    
    fun getHighContrastScript(): String {
        return """
            (function() {
                var styleId = 'alas-high-contrast';
                var existingStyle = document.getElementById(styleId);
                if (existingStyle) {
                    existingStyle.remove();
                    return;
                }
                
                var style = document.createElement('style');
                style.id = styleId;
                style.type = 'text/css';
                style.innerHTML = '* { background-color: #121212 !important; color: #e0e0e0 !important; border-color: #444 !important; text-shadow: none !important; box-shadow: none !important; } a { color: #8AB4F8 !important; text-decoration: underline !important; } a:visited { color: #C58AF9 !important; } img, video { filter: opacity(80%) contrast(110%); } input, textarea, select, button { background-color: #2d2d2d !important; color: #fff !important; border: 1px solid #666 !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
    }

    fun getReaderModeScript(): String {
        return """
            (function() {
                var readerId = 'alas-reader-mode';
                if (document.getElementById(readerId)) return;

                // heuristics to find content
                var content = document.querySelector('article') || 
                              document.querySelector('main') || 
                              document.querySelector('.post-content') || 
                              document.querySelector('.entry-content') ||
                              document.querySelector('#content');

                if (!content) {
                    // Fallback: try to find the container with most P tags
                    var divs = document.getElementsByTagName('div');
                    var maxP = 0;
                    for (var i = 0; i < divs.length; i++) {
                        var pCount = divs[i].getElementsByTagName('p').length;
                        if (pCount > maxP) {
                            maxP = pCount;
                            content = divs[i];
                        }
                    }
                }

                if (!content) {
                    alert('Reader mode: Could not identify main content.');
                    return;
                }

                var title = document.title;
                var contentHTML = content.innerHTML;

                // Create reader container
                var readerDiv = document.createElement('div');
                readerDiv.id = readerId;
                readerDiv.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:#1a1a1a;color:#e0e0e0;z-index:2147483647;overflow-y:scroll;padding:20px;font-family:sans-serif;line-height:1.6;font-size:18px;box-sizing:border-box;';
                
                var closeBtn = document.createElement('button');
                closeBtn.innerText = 'Close Reader View';
                closeBtn.style.cssText = 'position:fixed;top:10px;right:10px;padding:10px 20px;background:#333;color:#fff;border:none;border-radius:4px;cursor:pointer;z-index:2147483648;';
                closeBtn.onclick = function() { readerDiv.remove(); };
                
                var titleEl = document.createElement('h1');
                titleEl.innerText = title;
                titleEl.style.color = '#e0e0e0';
                titleEl.style.marginTop = '40px';
                
                var contentContainer = document.createElement('div');
                contentContainer.innerHTML = contentHTML;
                contentContainer.style.maxWidth = '800px';
                contentContainer.style.margin = '0 auto';
                contentContainer.style.color = '#e0e0e0';
                
                // Clean up styles in content
                var all = contentContainer.getElementsByTagName('*');
                for (var i = 0; i < all.length; i++) {
                    all[i].style.background = 'transparent';
                    all[i].style.color = 'inherit';
                    all[i].style.width = 'auto';
                    all[i].style.height = 'auto';
                    all[i].style.maxWidth = '100%';
                    all[i].style.fontFamily = 'inherit';
                }

                readerDiv.appendChild(closeBtn);
                readerDiv.appendChild(titleEl);
                readerDiv.appendChild(contentContainer);
                document.body.appendChild(readerDiv);
            })();
        """.trimIndent()
    }

    fun getTranslationScript(targetLang: String = "hi", sourceLang: String = "auto"): String {
        return """
            (function() {
                var targetLang = '$targetLang';
                var sourceLang = '$sourceLang';
                
                // Hide Google Translate UI
                var style = document.getElementById('alas-translate-hide');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'alas-translate-hide';
                    style.textContent = '.goog-te-banner-frame,.skiptranslate,#goog-gt-tt,.goog-te-balloon-frame,.goog-te-menu-frame,.goog-te-spinner-pos,.goog-te-gadget{display:none!important}body{top:0!important;position:static!important}#google_translate_element{position:absolute!important;top:-9999px!important;left:-9999px!important}';
                    (document.head || document.documentElement).appendChild(style);
                }
                
                // Update cookies for new language
                var cookieVal = '/auto/' + targetLang;
                document.cookie = 'googtrans=' + cookieVal + '; path=/';
                try {
                    var h = location.hostname;
                    document.cookie = 'googtrans=' + cookieVal + '; path=/; domain=' + h;
                    document.cookie = 'googtrans=' + cookieVal + '; path=/; domain=.' + h;
                    var parts = h.split('.');
                    if (parts.length > 2) {
                        document.cookie = 'googtrans=' + cookieVal + '; path=/; domain=.' + parts.slice(-2).join('.');
                    }
                } catch(e) {}
                
                // Trigger translation on select element
                function trigger(select) {
                    // Check if language option exists
                    var hasLang = false;
                    for (var i = 0; i < select.options.length; i++) {
                        if (select.options[i].value === targetLang) {
                            hasLang = true;
                            select.selectedIndex = i;
                            break;
                        }
                    }
                    if (!hasLang) {
                        select.value = targetLang;
                    }
                    
                    // Fire change event
                    var e = document.createEvent('HTMLEvents');
                    e.initEvent('change', true, true);
                    select.dispatchEvent(e);
                    
                    // Double-trigger for reliability
                    setTimeout(function() {
                        if (select.value !== targetLang) {
                            select.value = targetLang;
                            select.dispatchEvent(e);
                        }
                    }, 100);
                }
                
                // Check if select already exists (switching languages)
                var existingSelect = document.querySelector('.goog-te-combo');
                if (existingSelect && existingSelect.options.length > 0) {
                    // Already have translator, just switch language
                    if (existingSelect.value === targetLang) {
                        return; // Already on this language
                    }
                    trigger(existingSelect);
                    return;
                }
                
                // First time setup - create container
                var div = document.getElementById('google_translate_element');
                if (!div) {
                    div = document.createElement('div');
                    div.id = 'google_translate_element';
                    document.body.appendChild(div);
                }
                
                // Watch for combo to appear
                var observer = new MutationObserver(function(mutations, obs) {
                    var select = document.querySelector('.goog-te-combo');
                    if (select && select.options.length > 1) {
                        obs.disconnect();
                        trigger(select);
                    }
                });
                observer.observe(document.body, { childList: true, subtree: true });
                
                // Fallback polling
                var fallbackCheck = setInterval(function() {
                    var select = document.querySelector('.goog-te-combo');
                    if (select && select.options.length > 1) {
                        clearInterval(fallbackCheck);
                        observer.disconnect();
                        trigger(select);
                    }
                }, 50);
                setTimeout(function() { clearInterval(fallbackCheck); observer.disconnect(); }, 10000);
                
                // Google Translate callback
                window.googleTranslateElementInit = function() {
                    new google.translate.TranslateElement({
                        pageLanguage: sourceLang === 'auto' ? '' : sourceLang,
                        autoDisplay: true
                    }, 'google_translate_element');
                };
                
                // Load script if needed
                if (!document.querySelector('script[src*="translate.google.com/translate_a/element.js"]')) {
                    var s = document.createElement('script');
                    s.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                    document.head.appendChild(s);
                } else if (window.google && window.google.translate) {
                    window.googleTranslateElementInit();
                }
            })();
        """.trimIndent()
    }

    fun getRevertTranslationScript(): String {
        return """
            (function() {
                'use strict';
                
                function safeRemove(el) {
                    try { if (el && el.parentNode) el.parentNode.removeChild(el); } catch(e) {}
                }
                
                // Clear all Google Translate cookies on all domains
                try {
                    var hostname = window.location.hostname;
                    var domains = ['', hostname];
                    var parts = hostname.split('.');
                    for (var i = 0; i < parts.length - 1; i++) {
                        domains.push('.' + parts.slice(i).join('.'));
                    }
                    domains.forEach(function(domain) {
                        var base = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/';
                        document.cookie = domain ? base + '; domain=' + domain : base;
                    });
                } catch(e) {}
                
                // Remove all translation-related elements
                var selectors = [
                    '#google_translate_element',
                    '#alas-translate-container',
                    '.goog-te-banner-frame',
                    '.goog-te-menu-frame',
                    '.goog-te-spinner-pos',
                    '.goog-te-balloon-frame',
                    '#goog-gt-tt',
                    '#alas-translate-hide',
                    '.skiptranslate',
                    '.goog-te-gadget',
                    'script[src*="translate.google"]',
                    'script[src*="element.js"]',
                    'link[href*="translate"]',
                    'iframe[src*="translate"]'
                ];
                
                selectors.forEach(function(sel) {
                    try {
                        var elements = document.querySelectorAll(sel);
                        for (var i = 0; i < elements.length; i++) {
                            safeRemove(elements[i]);
                        }
                    } catch(e) {}
                });
                
                // Clear global state
                try { delete window.googleTranslateElementInit; } catch(e) {}
                try { delete window.google_translate_element; } catch(e) {}
                try { delete window._alasTranslateInProgress; } catch(e) {}
                try { if (window.google) window.google.translate = undefined; } catch(e) {}
                
                // Reload to restore original page
                window.location.reload();
            })();
        """.trimIndent()
    }

    fun getTranslationScriptFallback(targetLang: String = "en", sourceLang: String = "auto"): String {
        return "window.location.href = 'https://translate.google.com/translate?sl=$sourceLang&tl=$targetLang&u=' + encodeURIComponent(window.location.href);"
    }

    fun getFormDetectionScript(): String {
        return """
            (function() {
                function setupFormListener() {
                    document.querySelectorAll('form').forEach(form => {
                        if (form.getAttribute('data-alas-listener') === 'true') return;
                        form.setAttribute('data-alas-listener', 'true');
                        
                        form.addEventListener('submit', function(e) {
                            try {
                                var inputs = form.querySelectorAll('input');
                                var username = '';
                                var password = '';
                                
                                inputs.forEach(input => {
                                    var type = input.type.toLowerCase();
                                    var name = input.name.toLowerCase();
                                    var id = input.id.toLowerCase();
                                    
                                    if (type === 'password') {
                                        password = input.value;
                                    } else if ((type === 'text' || type === 'email' || type === 'tel') && 
                                               (name.includes('user') || name.includes('mail') || name.includes('login') || id.includes('user') || id.includes('mail'))) {
                                        username = input.value;
                                    }
                                });
                                
                                if (!username) {
                                     // Fallback: look for the input before the password field
                                     inputs.forEach((input, index) => {
                                        if (input.type === 'password' && index > 0) {
                                            var prev = inputs[index - 1];
                                            if (prev.type === 'text' || prev.type === 'email') {
                                                username = prev.value;
                                            }
                                        }
                                     });
                                }

                                if (username && password) {
                                    var data = JSON.stringify({username: username, password: password});
                                    if (window.AlasAutofill) {
                                        window.AlasAutofill.onFormSubmitted(window.location.href, data);
                                    }
                                }
                            } catch (err) {
                                console.error('AlasAutofill error', err);
                            }
                        });
                    });
                }
                
                // Run on load and periodically for dynamic forms
                setupFormListener();
                setTimeout(setupFormListener, 1000);
                setTimeout(setupFormListener, 3000);
            })();
        """.trimIndent()
    }

    fun getAutofillScript(credentials: List<Pair<String, String>>): String {
        if (credentials.isEmpty()) return ""
        
        // Convert credentials to JSON array string manually to avoid dependency
        val jsonBuilder = StringBuilder("[")
        credentials.forEachIndexed { index, (user, pass) ->
            if (index > 0) jsonBuilder.append(",")
            jsonBuilder.append("{\"u\":\"${user.replace("\"", "\\\"")}\",\"p\":\"${pass.replace("\"", "\\\"")}\"}")
        }
        jsonBuilder.append("]")
        val credsJson = jsonBuilder.toString()

        return """
            (function() {
                var creds = $credsJson;
                if (creds.length === 0) return;
                
                // Simple heuristic: use the first matching credential for now, or prompt?
                // For MVP, we'll try to autofill with the most recent (first) one.
                var user = creds[0].u;
                var pass = creds[0].p;
                
                function autofill() {
                    var inputs = document.querySelectorAll('input');
                    var userField = null;
                    var passField = null;
                    
                    inputs.forEach(input => {
                        var type = input.type.toLowerCase();
                        if (type === 'password' && !passField) {
                            passField = input;
                        } else if ((type === 'text' || type === 'email') && !userField) {
                            // Simple heuristic
                            userField = input;
                        }
                    });
                    
                    // Refined search
                     if (!userField || !passField) {
                        inputs.forEach(input => {
                            var name = (input.name || '').toLowerCase();
                            var id = (input.id || '').toLowerCase();
                            if ((name.includes('user') || name.includes('mail') || id.includes('user')) && !userField) userField = input;
                        });
                    }

                    if (userField && passField) {
                        userField.value = user;
                        passField.value = pass;
                        
                        // Trigger events so framework detects change
                        userField.dispatchEvent(new Event('input', { bubbles: true }));
                        userField.dispatchEvent(new Event('change', { bubbles: true }));
                        passField.dispatchEvent(new Event('input', { bubbles: true }));
                        passField.dispatchEvent(new Event('change', { bubbles: true }));
                        
                        // Highlight
                        userField.style.backgroundColor = '#e8f0fe';
                        passField.style.backgroundColor = '#e8f0fe';
                    }
                }
                
                autofill();
                setTimeout(autofill, 500); // Retry for dynamic forms
            })();
        """.trimIndent()
    }
    fun getTextExtractionScript(): String {
        return """
            (function() {
                try {
                    // Simple heuristic to get main content
                    // 1. Try readability-like selectors
                    var content = document.querySelector('article') || 
                                  document.querySelector('main') || 
                                  document.querySelector('[role="main"]') ||
                                  document.querySelector('.post-content') || 
                                  document.querySelector('#content');
                                  
                    var text = "";
                    if (content) {
                        text = content.innerText;
                    } else {
                        // Fallback to body but try to filter out nav/footer/scripts
                        var clone = document.body.cloneNode(true);
                        var toRemove = clone.querySelectorAll('nav, header, footer, script, style, noscript, iframe, .ad, .advertisement, [role="navigation"], [role="banner"], [role="contentinfo"]');
                        toRemove.forEach(function(el) { el.parentNode.removeChild(el); });
                        text = clone.innerText;
                    }
                    
                    // Clean up whitespace
                    text = text.replace(/\s+/g, ' ').trim();
                    
                    // Limit length if extremely large? Native side handles truncation too.
                    
                    if (window.AlasAutofill) {
                        window.AlasAutofill.onTextExtracted(text);
                    } else {
                        // Fallback for GeckoView: use prompt() to pass data to Native
                        // This allows passing much larger data than window.location.href
                        var encoded = encodeURIComponent(text);
                        prompt("alas-extract:" + encoded);
                    }
                } catch(e) {
                    console.error("Text extraction failed", e);
                }
            })();
        """.trimIndent()
    }
}