package com.samuel.bridge;

import android.app.Activity;
import android.util.Log;
import android.webkit.WebView;
import android.widget.TextView;

import com.samuel.bridge.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * API的定义必须满足以下条件：
 * 1.实现IBridge
 * 2.方法必须是public static类型
 * 3.固定4个参数Activity，WebView，JSONObject，Callback
 * 4.回调统一采用 callback.apply(),参数通过JSBridge.getJSONObject()获取Json对象
 * 注意：
 * 耗时操作在多线程中实现
 * UI操作在主线程实现
 *
 * JSONObject 转 JsonObject
 * JsonObject jsonObject = new JsonParser().parse(param==null?"":param.toString()).getAsJsonObject();
 */

public class BridgeImpl {
    /**
     * 自定义原生API
     * 参数：
     * param1：参数1
     */
    public static void testNativeFunc(final Activity webLoader, WebView wv, JSONObject param, final Callback callback) {
        BridgeImpl.changeContent(webLoader, wv, param, callback);
        wv.post(new Runnable() {
            public void run() {
                //做一些自己的操作，操作完毕后将值通过回调回传给h5页面
                try {
                    JSONObject object = new JSONObject();
                    object.put("origin", "native");
                    object.put("content", "请把Native返回的信息在H5页面中展示出来");
                    callback.apply(com.samuel.bridge.JSBridge.getSuccessJSONObject(object));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    public static void changeContent(final Activity webLoader, WebView wv, JSONObject param, final Callback callback) {
        // Log.d("ss","changeContent~");
        final String origin = param.optString("origin");
        final String content = param.optString("content");
        TextView textView = webLoader.findViewById(R.id.textView);
        textView.setText(origin + "：" + content);
    }
}
