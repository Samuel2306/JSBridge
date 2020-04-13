package com.samuel.bridge;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * Created by dailc on 2017/2/19 0019.
 *
 * callback类里面是当前正在执行的回调函数
 * 其中本示例只支持同时执行一个函数(因为只存储了一个mPort变量)，具体同时支持多个方法的拓展可自行进行
 */

public class Callback {
    // Handler这个类就是管理某个线程(也可能是进程)的消百息队列，比如度让Handler处理主线程的消息队列，这样就可以将一些耗时任务放到其他线程之中，
    // 待任务完成之后就往主线程的消息队问列中添加一个消息，这样Handler的Callback，即handleMessage就会答被调用
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    //和前端约定好的前端接收方式
    private static final String CALLBACK_JS_FORMAT = "javascript:JSBridge._handleMessageFromNative(%s);";
    private String mPort;
    private WeakReference<WebView> mWebViewRef;

    public Callback(WebView view, String port) {
        // 弱引用的对象，跟垃圾回收有关系
        mWebViewRef = new WeakReference<>(view);
        mPort = port;
    }

    /**
     * 执行H5的回调函数
     * @param jsonObject
     */
    public void apply(JSONObject jsonObject) {
        JSONObject object = new JSONObject();
        try {
            object.put("responseId", mPort);
            object.putOpt("responseData", jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // 执行H5的
        final String execJs = String.format(CALLBACK_JS_FORMAT, String.valueOf(object));
        //如果activity已经关闭则不回调
        if (mWebViewRef != null && mWebViewRef.get() != null ) {
            if( mWebViewRef.get().getContext() instanceof Activity){
                if (((Activity) mWebViewRef.get().getContext()).isFinishing()){
                    return;
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("ss",execJs);
                    mWebViewRef.get().loadUrl(execJs);
                }
            });
        }
    }

    /**
     * 主动调用h5方法
     * @param jsonObject
     */
    public void call(String handlerName,JSONObject jsonObject) {
        JSONObject object = new JSONObject();
        try {
            object.putOpt("callbackId", "9999");
            object.putOpt("handlerName", handlerName);
            object.putOpt("data", jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        final String execJs = String.format(CALLBACK_JS_FORMAT, String.valueOf(object));
        Log.d("ss",execJs);
        //如果activity已经关闭则不回调
        if (mWebViewRef != null && mWebViewRef.get() != null ) {
            if( mWebViewRef.get().getContext() instanceof Activity){
                if (((Activity) mWebViewRef.get().getContext()).isFinishing()){
                    return;
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebViewRef.get().loadUrl(execJs);
                }
            });
        }
    }
}
