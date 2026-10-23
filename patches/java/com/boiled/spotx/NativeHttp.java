package com.boiled.spotx;

import android.webkit.CookieManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

public final class NativeHttp {
    private static final int LIMIT=8*1024*1024;
    private NativeHttp() {}
    /** The only native fetch implementation reachable from the web player. */
    public static String fetch(String raw, String options) {
        return fetch(raw,options,()->false);
    }
    public static String fetch(String raw,String options,BooleanSupplier cancelled) {
        try { return run(raw, options,cancelled); }
        catch (Exception e) {
            // Do not return URLs, request headers, response bodies or exception messages.
            return "{\"status\":0,\"headers\":{},\"body\":\"Native request rejected or unavailable\"}";
        }
    }
    private static void active(BooleanSupplier cancelled) throws java.io.InterruptedIOException {
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new java.io.InterruptedIOException();
    }
    private static String run(String raw, String options,BooleanSupplier cancelled) throws Exception {
        active(cancelled);
        if (!Policy.nativeUrl(raw)) throw new SecurityException();
        JSONObject opts=options==null||options.isEmpty()?new JSONObject():new JSONObject(options);
        String method=Policy.method(opts.optString("method","GET"));
        JSONObject headers=opts.optJSONObject("headers");
        String body=opts.isNull("body")?null:opts.optString("body",null);
        byte[] payload=body==null?null:body.getBytes(StandardCharsets.UTF_8);
        if (payload!=null && payload.length>2*1024*1024) throw new SecurityException();
        String url=raw;
        boolean credentials=true;
        long deadline=android.os.SystemClock.elapsedRealtime()+25000;
        for (int hop=0; hop<6; hop++) {
            active(cancelled);
            if (!Policy.nativeUrl(url) || android.os.SystemClock.elapsedRealtime()>deadline) throw new SecurityException();
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestMethod(method);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
                c.setRequestProperty("Accept-Encoding","identity");
                c.setRequestProperty("Origin","https://open.spotify.com");
                c.setRequestProperty("Referer","https://open.spotify.com/");
                if (headers!=null) {
                    Iterator<String> keys=headers.keys();
                    while(keys.hasNext()) {
                        String key=keys.next();
                        String value=headers.optString(key,"");
                        if (Policy.nativeHeader(key,credentials)
                            && value.length()<16384 && value.indexOf('\r')<0 && value.indexOf('\n')<0) c.setRequestProperty(key,value);
                    }
                }
                // Never accept a Cookie header from JavaScript. CookieManager applies domain/path rules.
                String cookie=CookieManager.getInstance().getCookie(url);
                if (cookie!=null) c.setRequestProperty("Cookie",cookie);
                if (payload!=null && !method.equals("GET") && !method.equals("HEAD")) {
                    active(cancelled);
                    c.setDoOutput(true); c.setFixedLengthStreamingMode(payload.length);
                    try(java.io.OutputStream out=c.getOutputStream()){out.write(payload);}
                }
                int status=c.getResponseCode();
                active(cancelled);
                JSONObject responseHeaders=new JSONObject();
                for(Map.Entry<String,List<String>> entry:c.getHeaderFields().entrySet()) {
                    String key=entry.getKey(); List<String> values=entry.getValue();
                    if (key==null || values==null || values.isEmpty()) continue;
                    if(key.equalsIgnoreCase("Set-Cookie")) {
                        for(String value:values){active(cancelled);CookieManager.getInstance().setCookie(url,value);}
                    } else if (!key.equalsIgnoreCase("Set-Cookie2")) responseHeaders.put(key,values.get(0));
                }
                if (status==301||status==302||status==303||status==307||status==308) {
                    String location=c.getHeaderField("Location");
                    if(location==null) throw new SecurityException();
                    String next=new URL(new URL(url),location).toString();
                    if(!Policy.nativeUrl(next)) throw new SecurityException();
                    boolean same=Policy.forwardCredentials(url,next);
                    // A credential-bearing payload must not be replayed onto another host.
                    if(!same && payload!=null && (status==307||status==308)) throw new SecurityException();
                    credentials=credentials && same;
                    if(status==303 || ((status==301||status==302)&&method.equals("POST"))) {method="GET";payload=null;}
                    url=next; continue;
                }
                if(c.getContentLengthLong()>LIMIT) throw new SecurityException();
                InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
                ByteArrayOutputStream data=new ByteArrayOutputStream();
                if(stream!=null) try(InputStream in=stream) {
                    byte[] buffer=new byte[32768]; int read;
                    while((read=in.read(buffer))!=-1) {
                        active(cancelled);
                        if(data.size()+read>LIMIT || android.os.SystemClock.elapsedRealtime()>deadline) throw new SecurityException();
                        data.write(buffer,0,read);
                    }
                }
                active(cancelled);CookieManager.getInstance().flush();
                return new JSONObject().put("status",status).put("headers",responseHeaders)
                    .put("body",data.toString("UTF-8")).put("url",url).toString();
            } finally {c.disconnect();}
        }
        throw new SecurityException();
    }
}
