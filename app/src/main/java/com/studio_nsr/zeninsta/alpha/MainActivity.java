package com.studio_nsr.zeninsta.alpha;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView zenWebView;
    private ProgressBar zenProgressBar;
    private boolean isViewingSingleDmReel = false;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pure black status & navigation bars for native dark mode feel
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        setContentView(R.layout.activity_main);

        zenWebView = findViewById(R.id.zenWebView);
        zenProgressBar = findViewById(R.id.zenProgressBar);

        WebSettings settings = zenWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        );

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(zenWebView, true);

        // Native Android Touch Swipe Lock when viewing single DM reel
        zenWebView.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isViewingSingleDmReel) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getY() - startY) > 12) {
                            return true; // Block vertical swipe to next reel
                        }
                        break;
                }
                return false;
            }
        });

        // Smooth top loading progress bar
        zenWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    zenProgressBar.setVisibility(View.VISIBLE);
                    zenProgressBar.setProgress(newProgress);
                } else {
                    zenProgressBar.setVisibility(View.GONE);
                }
            }
        });

        zenWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Block main Reels tab/feed URLs
                if (url.contains("instagram.com/reels")) {
                    view.loadUrl("https://www.instagram.com/direct/inbox/");
                    return true;
                }

                // Keep Instagram links inside Zen-Insta, open external links in default browser
                if (!url.contains("instagram.com") && !url.contains("facebook.com") && !url.contains("fbcdn.net")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isViewingSingleDmReel = url.contains("/reel/");
                injectZenAlphaEngine(view);
            }
        });

        // Back button navigation
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (zenWebView.canGoBack()) {
                    zenWebView.goBack();
                } else {
                    finish();
                }
            }
        });

        zenWebView.loadUrl("https://www.instagram.com/");
    }

    // Strict DM-Only R-Mode Engine (Zero Badge)
    private void injectZenAlphaEngine(WebView view) {
        String js = "javascript:(function() {" +
                "if (window.__zenAlphaLoaded) return;" +
                "window.__zenAlphaLoaded = true;" +
                "let authorizedDmReelId = null;" +

                "const style = document.createElement('style');" +
                "style.textContent = 'a[href=\"/reels/\"], a[href^=\"/reels?\"], button[aria-label=\"Next\"], button[aria-label=\"Previous\"], svg[aria-label=\"Down chevron\"], svg[aria-label=\"Up chevron\"] { display: none !important; }';" +
                "document.head.appendChild(style);" +

                "document.addEventListener('click', function(e) {" +
                "if (!window.location.pathname.startsWith('/direct/')) return;" +
                "const anchor = e.target.closest('a[href*=\"/reel/\"]');" +
                "if (anchor) {" +
                "const match = anchor.getAttribute('href').match(/\\/reel\\/([a-zA-Z0-9_-]+)/);" +
                "if (match && match[1]) {" +
                "authorizedDmReelId = match[1];" +
                "sessionStorage.setItem('zen_allowed_reel', authorizedDmReelId);" +
                "}" +
                "}" +
                "}, true);" +

                "const origFetch = window.fetch;" +
                "window.fetch = function(resource, init) {" +
                "const url = typeof resource === 'string' ? resource : (resource && resource.url) || '';" +
                "if (url.includes('/api/v1/clips/') || url.includes('reels_tray') || url.includes('feed/reels')) {" +
                "return Promise.reject(new Error('Blocked by Zen-Insta'));" +
                "}" +
                "return origFetch.apply(this, arguments);" +
                "};" +

                "function enforceStrictMode() {" +
                "const path = window.location.pathname;" +
                "if (path === '/reels' || path.startsWith('/reels/')) {" +
                "window.location.replace('/direct/inbox/');" +
                "return;" +
                "}" +
                "if (path.startsWith('/reel/')) {" +
                "const match = path.match(/\\/reel\\/([a-zA-Z0-9_-]+)/);" +
                "const currentId = match ? match[1] : null;" +
                "const savedId = authorizedDmReelId || sessionStorage.getItem('zen_allowed_reel');" +
                "if (!savedId || currentId !== savedId) {" +
                "window.location.replace('/direct/inbox/');" +
                "return;" +
                "}" +
                "document.documentElement.style.setProperty('overflow', 'hidden', 'important');" +
                "document.body.style.setProperty('overflow', 'hidden', 'important');" +
                "} else {" +
                "if (!path.startsWith('/direct/')) {" +
                "authorizedDmReelId = null;" +
                "sessionStorage.removeItem('zen_allowed_reel');" +
                "}" +
                "document.documentElement.style.overflow = '';" +
                "document.body.style.overflow = '';" +
                "}" +
                "}" +

                "['wheel', 'touchmove'].forEach(evt => {" +
                "window.addEventListener(evt, e => {" +
                "if (window.location.pathname.startsWith('/reel/')) {" +
                "e.preventDefault();" +
                "e.stopPropagation();" +
                "}" +
                "}, { passive: false, capture: true });" +
                "});" +

                "function cleanUI() {" +
                "const path = window.location.pathname;" +
                "document.querySelectorAll('a[href=\"/reels/\"], a[href^=\"/reels?\"]').forEach(l => l.style.setProperty('display', 'none', 'important'));" +
                "if (path.startsWith('/explore')) {" +
                "document.querySelectorAll('a[href^=\"/reel/\"]').forEach(r => r.style.setProperty('display', 'none', 'important'));" +
                "}" +
                "if (path === '/' || path === '') {" +
                "document.querySelectorAll('article a[href^=\"/reel/\"]').forEach(r => {" +
                "const art = r.closest('article');" +
                "if (art) art.style.setProperty('display', 'none', 'important');" +
                "});" +
                "}" +
                "}" +

                "const origPush = history.pushState;" +
                "history.pushState = function() { origPush.apply(this, arguments); setTimeout(() => { enforceStrictMode(); cleanUI(); }, 40); };" +
                "const origReplace = history.replaceState;" +
                "history.replaceState = function() { origReplace.apply(this, arguments); setTimeout(() => { enforceStrictMode(); cleanUI(); }, 40); };" +
                "window.addEventListener('popstate', () => { enforceStrictMode(); cleanUI(); });" +

                "new MutationObserver(cleanUI).observe(document.body, { childList: true, subtree: true });" +
                "enforceStrictMode();" +
                "cleanUI();" +
                "})()";

        view.evaluateJavascript(js, null);
    }
}