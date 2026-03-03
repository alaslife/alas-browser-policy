package com.sun.alasbrowser.web

/**
 * Site-specific JavaScript injections
 * 
 * These scripts are injected based on SiteCompatibilityRegistry rules.
 * Each script is minimal, safe, and scoped to avoid conflicts.
 */
object SiteScripts {

    /**
     * Relaxed window.open() implementation
     * 
     * Allows legitimate window.open() calls while blocking known ad networks.
     * Used for sites like APK distributions that need window.open() for dropdowns
     * and selection logic.
     * 
     * ✅ ALLOWS:
     *   - Empty/null requests (permission checks)
     *   - File downloads (.apk, .zip, etc.)
     *   - Same-domain navigation
     *   - CDN links
     * 
     * 🚫 BLOCKS:
     *   - Ad networks (doubleclick, adservice, googlesyndication)
     *   - Tracking pixels
     *   - Known popup patterns
     * 
     * ✅ DEFAULT:
     *   - Pass through to real window.open()
     */
    fun windowOpenRelaxed(): String = """
        (function() {
            if (window.__alasWindowOpenPatched) return;
            window.__alasWindowOpenPatched = true;

            const realOpen = window.open;
            window.open = function(url, target, features) {
                // Allow empty/null URL (used for permission checks)
                if (!url) {
                    return realOpen.call(this, url, target, features);
                }

                const u = url.toLowerCase();

                // 🚫 KNOWN AD NETWORKS - Block these
                const adDomains = [
                    'doubleclick',
                    'adservice', 
                    'googlesyndication',
                    'facebook.com/tr',
                    'analytics.google',
                    'google-analytics',
                ];

                if (adDomains.some(ad => u.includes(ad))) {
                    // Return fake window object
                    return {
                        closed: false,
                        close: function() { this.closed = true; },
                        focus: function() {},
                        blur: function() {},
                        postMessage: function() {},
                        location: { href: u },
                        document: { 
                            write: function(){}, 
                            close: function(){},
                            body: {}
                        }
                    };
                }

                // ✅ ALLOW everything else
                return realOpen.call(this, url, target, features);
            };
        })();
    """.trimIndent()

    /**
     * Minimal ad blocker for sites without full OperaAdBlockerPro
     * 
     * Used on sites where we want some ad blocking but can't inject
     * the heavy-duty OperaAdBlockerPro script due to UI conflicts.
     */
    fun lightAdBlocker(): String = """
        (function() {
            if (window.__alasLightAdBlocker) return;
            window.__alasLightAdBlocker = true;

            // Hide common ad elements
            const adSelectors = [
                '[class*="ad-"]',
                '[id*="ad-"]',
                '[class*="advertisement"]',
                '.banner',
                '.popup-ad',
            ];

            const style = document.createElement('style');
            style.textContent = adSelectors.map(sel => sel + ' { display: none !important; }').join('\n');
            document.head.appendChild(style);
        })();
    """.trimIndent()

    /**
     * Dropdown/select element fixer
     * 
     * Some sites have broken select elements. This restores basic functionality.
     * Applied only on sites that specifically need it.
     */
    fun fixSelectElements(): String = """
        (function() {
            if (window.__alasSelectFixed) return;
            window.__alasSelectFixed = true;

            // Ensure select elements are visible and clickable
            const selects = document.querySelectorAll('select');
            selects.forEach(el => {
                el.style.pointerEvents = 'auto';
                el.style.opacity = '1';
                el.style.visibility = 'visible';
                if (el.parentElement) {
                    el.parentElement.style.pointerEvents = 'auto';
                }
            });

            // Re-enable change event listeners
            selects.forEach(el => {
                const clone = el.cloneNode(true);
                el.parentNode.replaceChild(clone, el);
            });
        })();
    """.trimIndent()

    /**
     * Form detection script
     * 
     * Detects login and search forms for UI features.
     * Minimal DOM traversal, safe to inject anywhere.
     */
    fun detectForms(): String = """
        (function() {
            const forms = document.querySelectorAll('form');
            const result = {
                hasLoginForm: false,
                hasSearchForm: false,
                loginUrl: null,
                searchUrl: null
            };

            forms.forEach(form => {
                const action = (form.action || '').toLowerCase();
                const html = form.innerHTML.toLowerCase();

                if (html.includes('password') || action.includes('login')) {
                    result.hasLoginForm = true;
                    result.loginUrl = form.action;
                }

                if (html.includes('search') || action.includes('search')) {
                    result.hasSearchForm = true;
                    result.searchUrl = form.action;
                }
            });

            return JSON.stringify(result);
        })();
    """.trimIndent()

    /**
     * Tab title extraction
     * 
     * Get page metadata for bookmarking/history.
     */
    fun extractPageMetadata(): String = """
        (function() {
            const metadata = {
                title: document.title || '',
                description: document.querySelector('meta[name="description"]')?.content || '',
                ogImage: document.querySelector('meta[property="og:image"]')?.content || '',
                favicon: document.querySelector('link[rel="icon"]')?.href || '',
                url: window.location.href
            };
            return JSON.stringify(metadata);
        })();
    """.trimIndent()
}
