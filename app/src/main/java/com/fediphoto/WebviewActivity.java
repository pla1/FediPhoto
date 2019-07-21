package com.fediphoto;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebviewActivity extends Activity {
    private String urlString;
    private final String TAG = this.getClass().getCanonicalName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, String.format("Web view url %s", urlString));
        setContentView(R.layout.activity_webview);
        WebView webView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, String.format("Page finished: %s", url));
                String returnUrl = "fediphoto://fediphotoreturn?code=";
                if (url.startsWith(returnUrl)) {
                    Intent intent = new Intent();
                    String token =  url.substring(returnUrl.length());
                    if (token.endsWith("&state=")) {
                        token = token.substring(0,token.length()- "&state=".length());
                    }
                    Log.i(TAG, String.format("Token \"%s\"", token));
                    intent.putExtra(MainActivity.Literals.token.name(), token);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }

        });
        webView.loadUrl(getIntent().getStringExtra(MainActivity.Literals.urlString.name()));
    }
}
