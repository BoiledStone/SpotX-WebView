package com.boiled.spotx;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Validate a complete session before touching cookies, then await all cookie callbacks. */
public final class ProfileSessions {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final List<String> DOMAINS=Arrays.asList("https://open.spotify.com","https://accounts.spotify.com",
        "https://api-partner.spotify.com","https://gew4-spclient.spotify.com","https://spclient.wg.spotify.com",
        "https://api.spotify.com","https://www.spotify.com");
    private ProfileSessions(){}
    public static boolean restore(Context context,String raw){
        final List<String[]> cookies=new ArrayList<>();boolean authenticated=false;
        try{
            if(raw==null||raw.length()>1024*1024)return false;
            JSONObject source=new JSONObject(raw);Iterator<String> hosts=source.keys();
            while(hosts.hasNext()){
                String host=hosts.next();if(!DOMAINS.contains(host))return false;
                String pairs=source.getString(host);
                if(pairs.indexOf('\n')>=0||pairs.indexOf('\r')>=0)return false;
                for(String pair:pairs.split(";")){
                    pair=pair.trim();if(pair.isEmpty())continue;
                    int eq=pair.indexOf('=');if(eq<1||pair.length()>16384)return false;
                    String name=pair.substring(0,eq);
                    if(!name.matches("[A-Za-z0-9_!#$%&'*+.^`|~-]+"))return false;
                    if(name.equals("sp_dc")&&pair.length()>eq+1)authenticated=true;
                    cookies.add(new String[]{host,pair+"; Path=/; Secure"});
                    if(cookies.size()>1000)return false;
                }
            }
        }catch(Exception e){return false;}
        if(!authenticated||cookies.isEmpty()||!BUSY.compareAndSet(false,true))return false;
        new Handler(Looper.getMainLooper()).post(()->{
            try{
                // Stop the old page and its native transport before it can repopulate the old session.
                context.stopService(new Intent().setComponent(new ComponentName(context.getPackageName(),"com.project.lol.service.MediaNotificationService")));
                Class.forName("oy4").getMethod("E").invoke(null);
                CookieManager cm=CookieManager.getInstance();
                cm.removeAllCookies(removed->{
                    AtomicInteger pending=new AtomicInteger(cookies.size());AtomicBoolean failed=new AtomicBoolean();
                    for(String[] cookie:cookies)cm.setCookie(cookie[0],cookie[1],ok->{
                        if(!Boolean.TRUE.equals(ok))failed.set(true);
                        if(pending.decrementAndGet()==0){
                            cm.flush();BUSY.set(false);
                            context.getSharedPreferences("spotilol_prefs",0).edit().putBoolean("LoggedIn",!failed.get()).apply();
                            if(failed.get())Toast.makeText(context,"La session n'a pas pu être restaurée. Reconnecte-toi à Spotify.",Toast.LENGTH_LONG).show();
                            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                            if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);context.startActivity(launch);}
                        }
                    });
                });
            }catch(Exception e){BUSY.set(false);Hardening.profileFailure(context);}
        });
        return true;
    }
}
