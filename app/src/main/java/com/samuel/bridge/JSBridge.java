package com.samuel.bridge;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dailc on 2017/2/19 0019.
 *
 *
 */

public class JSBridge {
    /**
     * scheme字符串，也可以存储到静态类中
     * 约定的schem值-可以自行约定
     */
    private static final String EJS_SCHEME = "customjsbridge";
    /**
     * 注册方法缓存对象
     */
    private static Map<String, HashMap<String, Method>> exposedMethods = new HashMap<>();

    private static WebView webView;


    public static void setCurrentWebView(WebView webView){
        JSBridge.webView = webView;
    }

    public static WebView getCurrentWebView(){
       return JSBridge.webView;
    }

    /**
     * 将api注册到缓存中
     *
     * @param exposedName: 某一类API集合的名称
     * @param clazz: HashMap，某一类API的集合
     */
    public static void register(String exposedName, Class clazz) {
        if (!exposedMethods.containsKey(exposedName)) {
            try {
                exposedMethods.put(exposedName, getAllMethod(clazz));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取框架api类中所有符合要求的api
     *
     * @param injectedCls：
     * @return
     * @throws Exception
     */
    private static HashMap<String, Method> getAllMethod(Class injectedCls) throws Exception {
        HashMap<String, Method> mMethodsMap = new HashMap<>();
        // getDeclaredMethods()获取的是类中所有方法，也就是源文件中有哪些方法，就会获取到哪些，包括：类自身的方法、重写的父类的方法、实现的接口方法
        Method[] methods = injectedCls.getDeclaredMethods();
        for (Method method : methods) {
            String name;
            // 方法必须是public或者static，而且不能是匿名函数
            if (method.getModifiers() != (Modifier.PUBLIC | Modifier.STATIC) || (name = method.getName()) == null) {
                continue;
            }

            // 只有参数满足特定条件的函数才会被放到hashmap中
            Class[] parameters = method.getParameterTypes(); // getParameterTypes获取所有参数的类型，并将他们存到数组里面
            if (null != parameters && parameters.length == 4) {
                if (parameters[1] == WebView.class && parameters[2] == JSONObject.class && parameters[3] == Callback.class) {
                    mMethodsMap.put(name, method);
                }
            }
        }
        return mMethodsMap;
    }

    /**
     *
     * @param webLoader：当前activity
     * @param webView：webview实例对象
     * @param uriString：h5提交的url
     * @return
     */
    public static String callJava(Activity webLoader, WebView webView, String uriString) {
        String methodName = "";
        String apiName = "";
        String param = "{}";
        String port = "";
        String error;

        if (TextUtils.isEmpty(uriString)) {
            return "uri不能为空";
        }

        Uri uri = Uri.parse(uriString);
        if (uri == null) {
            return "参数不合法";
        }

        apiName = uri.getHost();
        param = uri.getQuery();
        port = uri.getPort() + "";
        methodName = uri.getPath();

        if (TextUtils.isEmpty(apiName)) {
            return "API_Name不能为空";
        }
        if (TextUtils.isEmpty(port)) {
            return "callbackId不能为空";
        }
        methodName = methodName.replace("/", "");
        if (TextUtils.isEmpty(methodName)) {
            return "handlerName不能为空";
        }

        if (uriString.contains("#")) {
            error = "参数中不能有#";
            new Callback(webView, port).apply(getFailJSONObject(error));
            return error;
        }
        if (!uriString.startsWith(EJS_SCHEME)) {
            error = "SCHEME不正确";
            new Callback(webView, port).apply(getFailJSONObject(error));
            return error;
        }

        if (exposedMethods.containsKey(apiName)) {
            HashMap<String, Method> methodHashMap = exposedMethods.get(apiName);
            if (methodHashMap != null && methodHashMap.size() != 0 && methodHashMap.containsKey(methodName)) {
                Method method = methodHashMap.get(methodName);
                if (method != null) {
                    try {
                        // invoke方法的作用就是为方法先指定上下文，然后调用对应的方法。
                        // invoke的第一个参数就是被指定的上下文
                        method.invoke(null, webLoader, webView, new JSONObject(param), new Callback(webView, port));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            //未注册API
            error = apiName + "未注册";
            new Callback(webView, port).apply(getFailJSONObject(error));
            return error;
        }
        return null;
    }

    /**
     *
     * @param handlerName: 要调用的H5的方法名
     * @param jsonObject：传给H5的参数
     */
    public static void callH5(String handlerName, JSONObject jsonObject){
        new Callback(JSBridge.webView, null).call(handlerName, jsonObject);
    }


    /**
     * 获取callback返回数据json对象
     *
     * @param code   1：成功 0：失败 2:下拉刷新回传code值 3:页面刷新回传code值
     * @param msg    描述
     * @param result
     * @return
     */
    public static JSONObject getJSONObject(int code, String msg, JSONObject result) {
        JSONObject object = new JSONObject();
        try {
            object.put("code", code);
            if (!TextUtils.isEmpty(msg)) {
                object.put("msg", msg);
            }
            object.putOpt("result", result == null ? "" : result);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object;
    }

    /**
     * 获取调用成功后callback返回数据json对象
     *
     * @return
     */
    public static JSONObject getSuccessJSONObject() {
        return getJSONObject(1, "", null);
    }

    /**
     * 获取调用成功后callback返回数据json对象
     *
     * @param result
     * @return
     */
    public static JSONObject getSuccessJSONObject(JSONObject result) {
        JSONObject response = getJSONObject(1, "", result);
        return response;
    }

    /**
     * 获取调用失败后callback返回数据json对象
     *
     * @return
     */
    public static JSONObject getFailJSONObject() {
        return getJSONObject(0, "API调用失败", null);
    }

    /**
     * 获取调用失败后callback返回数据json对象
     *
     * @param msg
     * @return
     */
    public static JSONObject getFailJSONObject(String msg) {
        return getJSONObject(0, msg, null);
    }
}
