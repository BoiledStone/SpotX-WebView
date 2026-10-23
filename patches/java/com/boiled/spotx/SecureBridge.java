package com.boiled.spotx;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Origin- and frame-checked AndroidX WebKit boundary transport; no Java object is exposed. */
public final class SecureBridge implements InvocationHandler {
    private static final String NAME="SpotXNative";
    private static final String[] ORIGINS={"https://open.spotify.com","https://accounts.spotify.com"};
    private static final Map<WebView,SecureBridge> ACTIVE=new HashMap<>();
    private final WebView view;
    private final Object target;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private InvocationHandler provider;
    private volatile long generation;
    private volatile boolean closed;
    private long rateWindow;
    private int rateCount;
    private final java.util.ArrayDeque<Long> recoveries=new java.util.ArrayDeque<>();
    private SecureBridge(WebView v,Object t){view=v;target=t;workers.allowCoreThreadTimeOut(true);}

    private static Object call(InvocationHandler h,String iface,String method,Class<?>[] types,Object... args) throws Throwable {
        Method m=Class.forName("org.chromium.support_lib_boundary."+iface).getMethod(method,types);
        return h.invoke(null,m,args);
    }
    private static Object call(InvocationHandler h,String iface,String method) throws Throwable {
        return call(h,iface,method,new Class<?>[0]);
    }
    public static void install(WebView v,Object bridge,String ignored) {
        harden(v);
        v.removeJavascriptInterface("AndBridge");
        SecureBridge b=new SecureBridge(v,bridge);
        try {
            Class<?> glue=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",false,WebView.getWebViewClassLoader());
            InvocationHandler factory=(InvocationHandler)glue.getDeclaredMethod("createWebViewProviderFactory").invoke(null);
            String[] features=(String[])call(factory,"WebViewProviderFactoryBoundaryInterface","getSupportedFeatures");
            if(!Arrays.asList(features).contains("WEB_MESSAGE_LISTENER") || !Arrays.asList(features).contains("DOCUMENT_START_SCRIPT:1"))
                throw new UnsupportedOperationException();
            b.provider=(InvocationHandler)call(factory,"WebViewProviderFactoryBoundaryInterface","createWebView",new Class<?>[]{WebView.class},v);
            call(b.provider,"WebViewProviderBoundaryInterface","addWebMessageListener",
                new Class<?>[]{String.class,String[].class,InvocationHandler.class},NAME,ORIGINS,b);
            String script=asset(v,"spotx/secure-bridge.js");
            call(b.provider,"WebViewProviderBoundaryInterface","addDocumentStartJavaScript",
                new Class<?>[]{String.class,String[].class},script,ORIGINS);
            ACTIVE.put(v,b);
        } catch(Throwable e) {
            b.closed=true; b.workers.shutdownNow();
            android.util.Log.e("SpotX","Secure WebView transport unavailable; update Android System WebView.");
            new Handler(Looper.getMainLooper()).post(()->android.widget.Toast.makeText(v.getContext(),
                "Mets à jour Android System WebView pour activer la connexion sécurisée à SpotX WebView.",android.widget.Toast.LENGTH_LONG).show());
        }
    }
    public static void harden(WebView v) {
        WebSettings s=v.getSettings();
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);s.setGeolocationEnabled(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSaveFormData(false);
    }
    public static void close(WebView v,String ignored) {
        SecureBridge b=ACTIVE.remove(v);
        if(b==null)return;
        b.closed=true;b.generation++;b.workers.shutdownNow();b.main.removeCallbacksAndMessages(null);
        try{call(b.provider,"WebViewProviderBoundaryInterface","removeWebMessageListener",new Class<?>[]{String.class},NAME);}catch(Throwable e){}
    }
    public static void navigation(WebView v,String url) {
        if(v==null)return;
        SecureBridge b=ACTIVE.get(v);
        if(b!=null){b.generation++;b.workers.getQueue().clear();}
        if(!Policy.navigation(url)) {v.stopLoading();return;}
        if(Policy.bridge(url))try{v.evaluateJavascript(asset(v,"spotx/secure-bridge.js"),null);}catch(Exception e){}
    }
    public static boolean override(WebView v,String url) {return !Policy.navigation(url);}
    public static boolean override(WebView v,WebResourceRequest request) {
        return request==null || !Policy.navigation(request.getUrl().toString());
    }
    private static String asset(WebView v,String name) throws Exception {
        try(InputStream in=v.getContext().getAssets().open(name)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return out.toString("UTF-8");
        }
    }
    @Override public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
        if(method.getName().equals("getSupportedFeatures"))return new String[]{"WEB_MESSAGE_LISTENER"};
        if(!method.getName().equals("onPostMessage"))return null;
        if(closed || args==null || args.length!=5 || args[0]!=view || !Boolean.TRUE.equals(args[3]))return null;
        String origin=((Uri)args[2]).toString(),current=view.getUrl();
        if(!Policy.bridge(origin) || !Policy.bridge(current) || !Policy.forwardCredentials(origin,current))return null;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-rateWindow>=1000){rateWindow=now;rateCount=0;}
        if(++rateCount>180)return null;
        String raw;
        try{raw=(String)call((InvocationHandler)args[1],"WebMessageBoundaryInterface","getData");}
        catch(Throwable invalidMessage){return null;}
        if(raw==null || raw.length()>2*1024*1024)return null;
        JSONObject request;
        try{request=new JSONObject(raw);}catch(Exception e){return null;}
        JSONArray values=request.optJSONArray("args");
        if(values==null || values.length()>4)return null;
        String name=request.optString("method","");
        long id=request.optLong("id",-1),epoch=generation;
        InvocationHandler reply=(InvocationHandler)args[4];
        if(!Policy.player(origin) && !name.equals("loginDetected") && !name.equals("dbg"))return null;
        if(name.equals("nFetch") || name.equals("spicyLyrics")) {
            if(id<0)return null;
            try {
                workers.execute(()->{
                    if(closed||generation!=epoch)return;
                    Object result=null;String error=null;
                    try {
                        if(name.equals("nFetch"))result=NativeHttp.fetch(values.optString(0),values.optString(1),()->closed||generation!=epoch);
                        else {
                            if(!values.optString(0).matches("[A-Za-z0-9]{22}") || values.optString(1).length()>512 || values.optString(2).length()>512)throw new SecurityException();
                            result=target.getClass().getMethod("spicyLyrics",String.class,String.class,String.class,int.class)
                                .invoke(target,values.optString(0),values.optString(1),values.optString(2),Math.max(0,Math.min(86400,values.optInt(3))));
                        }
                    } catch(Throwable e){error="Native request unavailable";}
                    Object finalResult=result;String finalError=error;
                    main.post(()->respond(reply,id,finalResult,finalError,epoch,current));
                });
            }catch(java.util.concurrent.RejectedExecutionException e){respond(reply,id,null,"Native request queue full",epoch,current);}
            return null;
        }
        try {
            switch(name) {
                case "reloadPlayer":
                    if(values.length()!=0)throw new SecurityException();
                    long recoveryAt=android.os.SystemClock.elapsedRealtime();
                    while(!recoveries.isEmpty()&&recoveryAt-recoveries.peekFirst()>=600000)recoveries.removeFirst();
                    boolean accepted=recoveries.size()<2&&(recoveries.isEmpty()||recoveryAt-recoveries.peekLast()>=120000);
                    if(accepted)recoveries.addLast(recoveryAt);
                    respond(reply,id,accepted,null,epoch,current);
                    if(accepted)main.postDelayed(()->{
                        if(closed||generation!=epoch||!Policy.player(view.getUrl()))return;
                        android.widget.Toast.makeText(view.getContext(),"Reconnexion du lecteur…",android.widget.Toast.LENGTH_SHORT).show();
                        view.reload();
                    },100);
                    break;
                case "loginDetected": noArgs(name);respond(reply,id,true,null,epoch,current);break;
                case "wakeUp":case "wakeOff":case "cssInjected":case "clearDebugLog":case "playLoaded":
                case "openTimerDialog":case "openVolumeDialog":case "enterPip":case "enterLyricsPip":
                case "enterVideoFullscreen":case "exitVideoFullscreen":case "skipDownload":case "cancelDownload": noArgs(name);break;
                case "downloadTrack":case "downloadCollection":
                    target.getClass().getMethod(name,String.class).invoke(target,BridgeInput.download(values.optString(0),name.equals("downloadCollection")));break;
                case "recMediaStatus":
                    target.getClass().getMethod(name,String.class).invoke(target,BridgeInput.media(values.optString(0)));break;
                case "deferMessage":case "recAdContentIds":case "recAccountName":
                    target.getClass().getMethod(name,String.class).invoke(target,values.optString(0));break;
                case "onMediaItemsLoaded":case "onSearchCompleted":
                    target.getClass().getMethod(name,String.class,String.class).invoke(target,values.optString(0),values.optString(1));break;
                case "manageTShut":case "manageTSleep":case "playerExpanded":case "wideVideo":case "ctxMenuState":
                    target.getClass().getMethod(name,boolean.class).invoke(target,values.optBoolean(0));break;
                case "recMediaPosition":target.getClass().getMethod(name,long.class).invoke(target,Math.max(0,values.optLong(0)));break;
                case "enterPipVideo":target.getClass().getMethod(name,int.class,int.class).invoke(target,Math.max(1,Math.min(8192,values.optInt(0))),Math.max(1,Math.min(8192,values.optInt(1))));break;
                case "dbg":break; // Web-origin text may contain credentials; keep it out of diagnostic storage.
                default:break;
            }
        }catch(Throwable e){respond(reply,id,null,"Native action unavailable",epoch,current);}
        return null;
    }
    private void noArgs(String name) throws Exception {target.getClass().getMethod(name).invoke(target);}
    private void respond(InvocationHandler reply,long id,Object result,String error,long epoch,String url) {
        if(id<0 || closed || generation!=epoch || !Policy.forwardCredentials(url,view.getUrl()))return;
        try {
            JSONObject message=new JSONObject().put("id",id);
            if(error==null)message.put("result",result==null?JSONObject.NULL:result);else message.put("error",error);
            call(reply,"JsReplyProxyBoundaryInterface","postMessage",new Class<?>[]{String.class},message.toString());
        }catch(Throwable e){}
    }
}
