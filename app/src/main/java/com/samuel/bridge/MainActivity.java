package com.samuel.bridge;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.samuel.bridge.R;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextView textView;
    private Button btn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        webView = (WebView)findViewById(R.id.webview);

        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(webChromeClient);

        // 通过getSettings方法获取WebSettings对象，设置允许加载js，设置缓存模式，支持缩放
        WebSettings webSettings=webView.getSettings();
        // 允许Native调用javascript
        webSettings.setJavaScriptEnabled(true);

        /**
         * LOAD_CACHE_ONLY: 不使用网络，只读取本地缓存数据
         * LOAD_DEFAULT: （默认）根据cache-control决定是否从网络上取数据。
         * LOAD_NO_CACHE: 不使用缓存，只从网络获取数据.
         * LOAD_CACHE_ELSE_NETWORK，只要本地有，无论是否过期，或者no-cache，都使用缓存中的数据。
         */
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);//不使用缓存，只从网络获取数据.

        //加载url
        webView.loadUrl("file:///android_asset/index.html");

        //注册JSBridge api
        JSBridge.register("namespace_bridge", BridgeImpl.class);
        // 设置当前webview
        JSBridge.setCurrentWebView(webView);

        textView = findViewById(R.id.textView);

        btn = (Button)findViewById(R.id.btnLogin);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject object = new JSONObject();
                try {
                    object.put("origin", "native");
                    object.putOpt("content", "我想改回原来的内容");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JSBridge.callH5("h5_api", object);
            }
        });
    }


    // WebViewClient主要帮助WebView处理各种通知、请求事件
    private WebViewClient webViewClient = new WebViewClient() {

        @Override // 重写shouldOverrideUrlLoading()方法，使得打开网页时不调用系统浏览器， 而是在本WebView中显示
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.i("网页中输出以下内容：", url);
            JSBridge.callJava(MainActivity.this, JSBridge.getCurrentWebView(), url);
            return true;
        }
    };

    // WebChromeClient主要辅助WebView处理Javascript的对话框、网站图标、网站title、加载进度等
    private WebChromeClient webChromeClient = new WebChromeClient(){

        @Override
        public void onConsoleMessage(String message, int lineNumber,String sourceID) {
            super.onConsoleMessage(message, lineNumber, sourceID);
            Log.i("网页中输出以下内容：",message);
        }
    };
}
