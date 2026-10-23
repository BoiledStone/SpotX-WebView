package com.boiled.spotx.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import com.boiled.spotx.BridgeInput;
import com.boiled.spotx.Hardening;
import com.boiled.spotx.NativeHttp;
import com.boiled.spotx.ProfileSessions;
import com.boiled.spotx.SecureBridge;
import com.boiled.spotx.ArtworkLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public final class SecurityInstrumentation extends Instrumentation {
    private Activity activity;private WebView view;private Probe probe;private int checks;
    private CountDownLatch loaded;
    private volatile String servedHtml="";
    private String bridgeTrace="no callback";
    private void traceBridge(){
        try{
            Field active=SecureBridge.class.getDeclaredField("ACTIVE");active.setAccessible(true);
            SecureBridge handler=(SecureBridge)((Map<?,?>)active.get(null)).get(view);
            if(handler==null){bridgeTrace="no secure transport";return;}
            Field providerField=SecureBridge.class.getDeclaredField("provider");providerField.setAccessible(true);
            InvocationHandler provider=(InvocationHandler)providerField.get(handler);
            Class<?> api=Class.forName("org.chromium.support_lib_boundary.WebViewProviderBoundaryInterface");
            provider.invoke(null,api.getMethod("removeWebMessageListener",String.class),new Object[]{"SpotXNative"});
            InvocationHandler wrapper=(proxy,method,args)->{
                if(method.getName().equals("onPostMessage"))bridgeTrace="callback origin="+args[2]+" main="+args[3]+" sameView="+(args[0]==view)+" url="+view.getUrl();
                try{return handler.invoke(proxy,method,args);}catch(Throwable e){bridgeTrace+=" failure="+e.getClass().getSimpleName()+":"+e.getMessage();throw e;}
            };
            provider.invoke(null,api.getMethod("addWebMessageListener",String.class,String[].class,InvocationHandler.class),
                new Object[]{"SpotXNative",new String[]{"https://open.spotify.com","https://accounts.spotify.com"},wrapper});
        }catch(Throwable e){bridgeTrace="test trace setup: "+e.getClass().getSimpleName();}
    }
    public static final class Probe {
        public volatile int logins;public void loginDetected(){logins++;}
        final AtomicInteger lyricsCalls=new AtomicInteger();
        final CountDownLatch lyricsStarted=new CountDownLatch(4),releaseLyrics=new CountDownLatch(1),lyricsDone=new CountDownLatch(4);
        public String spicyLyrics(String id,String title,String artist,int duration)throws Exception{
            lyricsCalls.incrementAndGet();lyricsStarted.countDown();
            try{releaseLyrics.await(8,TimeUnit.SECONDS);return "{}";}finally{lyricsDone.countDown();}
        }
    }
    private void artworkCancellation()throws Exception{
        Object owner=new Object();Class<?> taskClass=Class.forName("com.boiled.spotx.ArtworkLoader$Task");
        java.lang.reflect.Constructor<?> constructor=taskClass.getDeclaredConstructor(Object.class,String.class,boolean.class);constructor.setAccessible(true);
        Object task=constructor.newInstance(owner,"https://i.scdn.co/image/test",false);
        Field connection=taskClass.getDeclaredField("connection");connection.setAccessible(true);
        Field activeField=ArtworkLoader.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Object,Object> active=(Map<Object,Object>)activeField.get(null);
        AtomicInteger disconnects=new AtomicInteger();
        HttpURLConnection fake=new HttpURLConnection(new URL("https://i.scdn.co/image/test")){
            @Override public void disconnect(){disconnects.incrementAndGet();}
            @Override public void connect(){}
            @Override public boolean usingProxy(){return false;}
        };
        AtomicBoolean running=new AtomicBoolean(true);AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try{while(running.get()){connection.set(task,fake);Thread.yield();connection.set(task,null);}}catch(Throwable error){failure.set(error);}});
        worker.start();
        try{runOnMainSync(()->{try{for(int i=0;i<10000;i++){synchronized(ArtworkLoader.class){active.put(owner,task);}ArtworkLoader.cancel(owner);}}catch(Throwable error){failure.set(error);}});}
        finally{running.set(false);worker.join(2000);ArtworkLoader.cancel(owner);}
        check(failure.get()==null,"concurrent artwork cancellation does not crash: "+failure.get());
        check(disconnects.get()>0,"artwork cancellation exercised active connections");
    }
    public static final class ArtworkOwner {private final String lastCoverUrl="https://i.scdn.co/image/test";}
    private void artworkRetry()throws Exception{
        Object owner=new ArtworkOwner();Class<?> type=Class.forName("com.boiled.spotx.ArtworkLoader$Task");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Object.class,String.class,boolean.class);constructor.setAccessible(true);
        Object task=constructor.newInstance(owner,"https://i.scdn.co/image/test",false);
        Object exhausted=constructor.newInstance(owner,"https://i.scdn.co/image/test",false);
        Field activeField=ArtworkLoader.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Object,Object> active=(Map<Object,Object>)activeField.get(null);
        Field attempts=type.getDeclaredField("attempts"),retry=type.getDeclaredField("retry"),main=ArtworkLoader.class.getDeclaredField("MAIN");
        attempts.setAccessible(true);retry.setAccessible(true);main.setAccessible(true);
        Method failed=type.getDeclaredMethod("failed");failed.setAccessible(true);
        android.os.Handler handler=(android.os.Handler)main.get(null);Runnable callback=(Runnable)retry.get(task),exhaustedCallback=(Runnable)retry.get(exhausted);
        AtomicBoolean scheduled=new AtomicBoolean(),removed=new AtomicBoolean(),bounded=new AtomicBoolean();
        runOnMainSync(()->{try{
            synchronized(ArtworkLoader.class){active.put(owner,task);attempts.setInt(task,1);}
            failed.invoke(task);scheduled.set(handler.hasCallbacks(callback));
            ArtworkLoader.cancel(owner);removed.set(!handler.hasCallbacks(callback));
            synchronized(ArtworkLoader.class){active.put(owner,exhausted);attempts.setInt(exhausted,3);}
            failed.invoke(exhausted);bounded.set(!active.containsKey(owner)&&!handler.hasCallbacks(exhaustedCallback));
        }catch(Exception error){throw new RuntimeException(error);}});
        check(scheduled.get(),"failed native artwork schedules a delayed retry");
        check(removed.get(),"cancelling artwork removes its delayed retry");
        check(bounded.get(),"native artwork stops after its retry budget");
    }
    private void check(boolean result,String label){checks++;if(!result)throw new AssertionError(label);}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private String eval(String source)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> answer=new AtomicReference<>();
        runOnMainSync(()->view.evaluateJavascript(source,value->{answer.set(value);latch.countDown();}));
        if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("JS evaluation timed out");
        return answer.get();
    }
    private void load(String origin,String html)throws Exception{
        loaded=new CountDownLatch(1);
        servedHtml=html;
        runOnMainSync(()->view.loadUrl(origin));
        if(!loaded.await(8,TimeUnit.SECONDS))throw new AssertionError("WebView page load timed out");
    }
    private boolean waitValue(String expression,String expected)throws Exception{
        for(int i=0;i<30;i++){if(expected.equals(eval(expression)))return true;Thread.sleep(100);}
        return false;
    }
    private static final class Request extends PermissionRequest{
        final Uri origin;final String[] resources;String[] granted;boolean denied;
        Request(String host,String... values){origin=Uri.parse(host);resources=values;}
        @Override public Uri getOrigin(){return origin;}
        @Override public String[] getResources(){return resources;}
        @Override public void grant(String[] values){granted=values;}
        @Override public void deny(){denied=true;}
    }
    private String asset(String name)throws Exception{
        try(InputStream in=getContext().getAssets().open(name)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");
        }
    }
    @Override public void onStart(){
        Bundle report=new Bundle();
        try{
            Intent launch=new Intent().setClassName(getTargetContext().getPackageName(),"com.project.lol.ui.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);
            probe=new Probe();
            runOnMainSync(()->{
                view=new WebView(activity);view.getSettings().setJavaScriptEnabled(true);view.getSettings().setDomStorageEnabled(true);
                view.setWebViewClient(new WebViewClient(){
                    @Override public void onPageFinished(WebView v,String u){if(loaded!=null)loaded.countDown();}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                        String html=r.isForMainFrame()?servedHtml:"";
                        return new WebResourceResponse(r.isForMainFrame()?"text/html":"text/plain","UTF-8",new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                });
                activity.addContentView(view,new ViewGroup.LayoutParams(-1,-1));
                SecureBridge.install(view,probe,"AndBridge");
                traceBridge();
            });
            load("https://open.spotify.com/","<html><head></head><body>Trusted main frame</body></html>");
            check("\"object\"".equals(eval("typeof AndBridge")),"secure origin transport is installed on actual WebView");
            check("true".equals(eval("SpotXNative.postMessage('{');try{SpotXNative.postMessage(new ArrayBuffer(8))}catch(_){};true")),"malformed and binary messages do not crash the transport");
            eval("window.result='pending';AndBridge.loginDetected().then(function(){window.result='ok'}).catch(function(){window.result='error'});true");
            check(waitValue("window.result","\"ok\""),"login waits for native acknowledgement: "+bridgeTrace+" result="+eval("window.result")+" logins="+probe.logins);
            check(probe.logins==1,"trusted main frame can call native method once");
            eval("var f=document.createElement('iframe');f.srcdoc='<script>SpotXNative.postMessage(JSON.stringify({method:\"loginDetected\",args:[]}))<\\/script>';document.body.appendChild(f);true");
            Thread.sleep(500);check(probe.logins==1,"same-origin subframe cannot call native methods");
            eval("SpotXNative.postMessage(JSON.stringify({method:'getClass',args:[]}));true");
            check(probe.logins==1,"unknown native method is not dispatched");
            eval("window.result='pending';AndBridge.nFetch('https://127.0.0.1/private','{}').then(function(s){window.result=JSON.parse(s).status});true");
            check(waitValue("window.result","0"),"private-network native URL is rejected");
            eval(asset("native-fetch.js"));
            eval("window.fetchOk='pending';window.originalFetch=AndBridge.nFetch;AndBridge.nFetch=function(url,opts){window.fetchRequest={url:url,opts:JSON.parse(opts)};return Promise.resolve(JSON.stringify({status:200,headers:{'Content-Type':'application/json'},body:'{\"ok\":true}'}))};var req=new Request('https://api.spotify.com/test',{method:'POST',headers:[['x-client-id','fixture']],body:'fixture'});mngFetch(req).then(async function(r){var copy=r.clone();window.fetchOk=fetchRequest.url===req.url&&fetchRequest.opts.method==='POST'&&fetchRequest.opts.body==='fixture'&&fetchRequest.opts.headers['x-client-id']==='fixture'&&!req.bodyUsed&&copy!==r&&(await copy.json()).ok&&(await r.json()).ok}).catch(function(){window.fetchOk=false}).finally(function(){AndBridge.nFetch=window.originalFetch});true");
            check(waitValue("window.fetchOk","true"),"Request, header pairs and independent Response clones work in actual Android WebView");
            load("https://evil.invalid/","<html><body>Untrusted origin</body></html>");
            check("\"undefined\"".equals(eval("typeof SpotXNative")),"foreign origin gets no native endpoint");
            check("\"undefined\"".equals(eval("typeof AndBridge")),"foreign origin gets no compatibility shim");
            Request mixed=new Request("https://open.spotify.com",PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            Request foreign=new Request("https://evil.invalid",PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
            Request camera=new Request("https://open.spotify.com",PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            runOnMainSync(()->{Hardening.permission(mixed);Hardening.permission(foreign);Hardening.permission(camera);});
            check(Arrays.equals(mixed.granted,new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}),"grant only DRM from a mixed request");
            check(foreign.denied,"deny DRM to a foreign origin");check(camera.denied,"deny web camera requests");
            runOnMainSync(()->{
                check(!view.getSettings().getAllowFileAccess(),"file access disabled");
                check(!view.getSettings().getAllowContentAccess(),"content access disabled");
                check(view.getSettings().getMixedContentMode()==android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,"mixed content disabled");
                check(view.getSettings().getSafeBrowsingEnabled(),"safe browsing enabled");
            });
            check(Hardening.mediaClient(getTargetContext(),getTargetContext().getPackageName(),android.os.Process.myUid()),"own media client allowed");
            check(!Hardening.mediaClient(getTargetContext(),"com.evil.fake",android.os.Process.myUid()),"media package/UID spoof rejected");
            check(!Hardening.validMediaIntent(new Intent(Intent.ACTION_MEDIA_BUTTON)),"unstamped service media command rejected");
            Intent stamped=new Intent(Intent.ACTION_MEDIA_BUTTON);Hardening.stampMediaIntent(stamped);
            check(Hardening.validMediaIntent(stamped),"internal media command allowed");
            JSONObject http=new JSONObject(NativeHttp.fetch("https://evil.invalid","{}"));
            check(http.getInt("status")==0,"native fetch blocks foreign hosts before network access");
            String track="{\"trackId\":\"0123456789abcdefghijkl\",\"title\":\"Example\",\"cover\":\"https://127.0.0.1/cover\"}";
            check(new JSONObject(BridgeInput.download(track,false)).getString("cover").isEmpty(),"download metadata drops private-network artwork");
            boolean rejected=false;try{BridgeInput.download("{\"trackId\":\"../../private\"}",false);}catch(Exception e){rejected=true;}
            check(rejected,"download ID traversal rejected");
            check(!ProfileSessions.restore(getTargetContext(),"{\"https://evil.invalid\":\"sp_dc=abc\"}"),"invalid session domain rejected before cookie removal");
            check(!ProfileSessions.restore(getTargetContext(),"{\"https://open.spotify.com\":\"sp_dc=abc\\r\\nHeader: x\"}"),"cookie header injection rejected");
            check(new JSONObject(NativeHttp.fetch("https://api.spotify.com/test","{}",()->true)).getInt("status")==0,"cancelled native fetch rejected before connection");
            artworkCancellation();
            artworkRetry();
            load("https://open.spotify.com/","<html><body>Queue cancellation</body></html>");
            eval("for(var i=0;i<8;i++)AndBridge.spicyLyrics('0123456789abcdefghijkl','fixture','fixture',1).catch(function(){});true");
            check(probe.lyricsStarted.await(4,TimeUnit.SECONDS),"four native workers are active before navigation");
            Field activeBridge=SecureBridge.class.getDeclaredField("ACTIVE");activeBridge.setAccessible(true);
            Field workers=SecureBridge.class.getDeclaredField("workers");workers.setAccessible(true);
            AtomicInteger queued=new AtomicInteger();
            for(int i=0;i<30;i++){
                runOnMainSync(()->{try{Object bridge=((Map<?,?>)activeBridge.get(null)).get(view);queued.set(((java.util.concurrent.ThreadPoolExecutor)workers.get(bridge)).getQueue().size());}catch(Exception error){queued.set(-1);}});
                if(queued.get()==4)break;Thread.sleep(50);
            }
            check(queued.get()==4,"four old-document requests are queued before cancellation");
            runOnMainSync(()->SecureBridge.navigation(view,"https://accounts.spotify.com/"));
            load("https://accounts.spotify.com/","<html><body>New document</body></html>");
            probe.releaseLyrics.countDown();
            check(probe.lyricsDone.await(4,TimeUnit.SECONDS),"previous active calls finish after navigation");
            Thread.sleep(200);check(probe.lyricsCalls.get()==4,"queued calls from the previous document never run");
            String fixture=asset("player.html");load("https://open.spotify.com/",fixture);
            check(waitValue("document.getElementById('spl-track')&&document.getElementById('spl-track').textContent","\"Evening Light\""),"current title fallback works on changed Spotify DOM");
            check("\"Luna & The Coast\"".equals(eval("document.getElementById('spl-artist').textContent")),"current artist fallback");
            check("true".equals(eval("document.getElementById('spl-cover-img').hidden && !document.getElementById('spl-cover-img').hasAttribute('src')")),"missing cover has no broken image request");
            check("true".equals(eval("[...document.querySelectorAll('#spotilolPlayerControls .spl-transport button,#spotilolPlayerControls .spl-mini-transport button')].filter(b=>b.getBoundingClientRect().width>0).every(b=>b.getBoundingClientRect().width>=43.5&&b.getBoundingClientRect().height>=43.5)")),"44px primary player controls");
            check("true".equals(eval("document.getElementById('spotilolPlayerControls').getBoundingClientRect().height<=170")),"compact player height restored");
            check("true".equals(eval("document.getElementById('spotilolPlayerControls').getBoundingClientRect().right<=innerWidth+1")),"player fits phone viewport");
            check("\"Lecture\"".equals(eval("document.getElementById('spl-play').getAttribute('aria-label')")),"French playback accessibility label");
            check("\"slider\"".equals(eval("document.getElementById('spl-bar').getAttribute('role')")),"seek bar has slider semantics");
            File folder=getTargetContext().getExternalFilesDir("validation");folder.mkdirs();
            Bitmap screenshot=getUiAutomation().takeScreenshot();
            if(screenshot!=null)try(FileOutputStream out=new FileOutputStream(new File(folder,"player-phone.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);screenshot.recycle();}
            eval("window.badReload='pending';SpotXNative.postMessage(JSON.stringify({id:987654,method:'reloadPlayer',args:[1]}));var origMessage=SpotXNative.onmessage;SpotXNative.onmessage=function(event){var data=JSON.parse(event.data);if(data.id===987654)window.badReload=!!data.error;else origMessage(event)};true");
            check(waitValue("window.badReload","true"),"reload rejects unexpected arguments");
            loaded=new CountDownLatch(1);
            eval("var recoveryNow=Date.now.bind(Date),recoveryClock=recoveryNow()-132002;Date.now=function(){return recoveryClock};spotxPlaybackRecovery.background();recoveryClock+=120001;spotxPlaybackRecovery.resume();actPlayPause(true);recoveryClock=recoveryNow();true");
            check(loaded.await(5,TimeUnit.SECONDS),"stalled play after simulated long background reloads actual Android WebView");
            String resumeRecord=eval("sessionStorage.getItem('spotx.playback-recovery.v1')");
            check("true".equals(eval("(function(){var r=JSON.parse(sessionStorage.getItem('spotx.playback-recovery.v1'));return !!r&&r.uri==='spotify:track:0123456789abcdefghijkl'&&r.position===72000})()")),"recovery keeps intended track and position across reload: "+resumeRecord);
            eval("window.repeatReload='pending';AndBridge.reloadPlayer().then(function(ok){window.repeatReload=ok});true");
            check(waitValue("window.repeatReload","false"),"native cooldown rejects immediate repeated reload");
            load("https://accounts.spotify.com/","<html><body>Login remains outside recovery</body></html>");
            loaded=new CountDownLatch(1);
            eval("SpotXNative.postMessage(JSON.stringify({method:'reloadPlayer',args:[]}));true");
            check(!loaded.await(400,TimeUnit.MILLISECONDS),"account origin cannot request a player reload");
            report.putString("status","PASS");report.putInt("checks",checks);
            report.putString("stream","PASS "+checks+" Android WebView/security/experience assertions\n");
            finish(Activity.RESULT_OK,report);
        }catch(Throwable e){
            report.putString("status","FAIL");report.putInt("checks",checks);
            report.putString("stream",e.getClass().getSimpleName()+": "+e.getMessage()+"\n");
            finish(Activity.RESULT_CANCELED,report);
        }finally{
            if(view!=null)runOnMainSync(()->{SecureBridge.close(view,"AndBridge");((ViewGroup)view.getParent()).removeView(view);view.destroy();});
        }
    }
}
