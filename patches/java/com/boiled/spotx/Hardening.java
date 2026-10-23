package com.boiled.spotx;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.session.MediaSessionManager;
import android.os.Process;
import android.webkit.PermissionRequest;
import android.widget.Toast;
import java.util.Arrays;
import java.lang.reflect.Proxy;

public final class Hardening {
    private static final String MEDIA_TOKEN=java.util.UUID.randomUUID().toString();
    private static Context appContext;
    private static Context labelsContext;
    private static String labelsLocale;
    private Hardening() {}
    public static void initialize(Context c) {
        appContext=c.getApplicationContext();
        SharedPreferences p=c.getSharedPreferences("spotilol_prefs",0);
        SharedPreferences.Editor e=p.edit();
        if(!p.getBoolean("SpotXConnectV1",false))e.putBoolean("SpotXConnectV1",true).putBoolean("TakeControl",false);
        if(!p.getBoolean("SpotXDefaultsV1",false)) {
            e.putBoolean("SpotXDefaultsV1",true).putBoolean("HideEmptyPlayer",true)
                .putBoolean("Logging",false).putBoolean("DebugOverlay",false)
                .putBoolean("ShowScrollbar",false);
        }
        // The upstream updater cannot update this independently signed application.
        e.putBoolean("ServiceOn",true).putBoolean("TelemetryEnabled",false).putLong("LastUpdateCheck",Long.MAX_VALUE).apply();
    }
    public static boolean hideToggleSetting(String title) {
        return title!=null && title.equals(label(0x7f110316));
    }
    public static boolean isTelemetrySetting(String title) {
        return title!=null && title.equals(label(0x7f110318));
    }
    public static boolean hideInfoSetting(String title) {
        return title!=null && title.equals(label(0x7f1102ad));
    }
    private static synchronized String label(int resource) {
        if(appContext==null)return null;
        java.util.Locale locale=java.util.Locale.getDefault();String tag=locale.toLanguageTag();
        if(!tag.equals(labelsLocale)){
            android.content.res.Configuration config=new android.content.res.Configuration(appContext.getResources().getConfiguration());
            config.setLocale(locale);labelsContext=appContext.createConfigurationContext(config);labelsLocale=tag;
        }
        return labelsContext.getString(resource);
    }
    public static void permission(PermissionRequest request) {
        if(request==null) return;
        // Grant only the requested DRM permission, only to the exact player origin.
        if(Policy.player(request.getOrigin().toString()) && Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
            request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
        else request.deny();
    }
    public static boolean mediaClient(Context c, String name, int uid) {
        if(name==null) return false;
        PackageManager pm=c.getPackageManager();
        String[] packages=pm.getPackagesForUid(uid);
        if(packages==null || !Arrays.asList(packages).contains(name)) return false;
        if(uid==Process.myUid()) return true;
        try {
            MediaSessionManager m=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if(m!=null && m.isTrustedForMediaControl(new MediaSessionManager.RemoteUserInfo(name,-1,uid))) return true;
            if(!Arrays.asList("com.google.android.projection.gearhead","com.google.android.wearable.app","com.google.android.wearable.app.cn").contains(name)) return false;
            ApplicationInfo google=pm.getApplicationInfo("com.google.android.gms",0);
            return (google.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0
                && pm.checkSignatures(name,"com.google.android.gms")==PackageManager.SIGNATURE_MATCH;
        } catch(Exception e){return false;}
    }
    public static String link(android.content.Intent intent) {
        String value=intent==null?null:intent.getDataString();
        return Policy.link(value)?value:null;
    }
    public static void stampMediaIntent(android.content.Intent i) {
        if(i!=null)i.putExtra("com.boiled.spotx.MEDIA_TOKEN",MEDIA_TOKEN);
    }
    public static boolean validMediaIntent(android.content.Intent i) {
        return i==null || !android.content.Intent.ACTION_MEDIA_BUTTON.equals(i.getAction()) ||
            MEDIA_TOKEN.equals(i.getStringExtra("com.boiled.spotx.MEDIA_TOKEN"));
    }
    public static android.content.Intent privateReceiver(Context c,android.content.BroadcastReceiver r,android.content.IntentFilter f) {
        if(android.os.Build.VERSION.SDK_INT>=33)return c.registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);
        return c.registerReceiver(r,f,c.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",null);
    }
    public static void profileFailure(Context c) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->Toast.makeText(c,
            "Stockage sécurisé indisponible. Les profils existants sont conservés ; réessaie après le déverrouillage du téléphone.",Toast.LENGTH_LONG).show());
    }
    /** A failed key must never cause cleartext writes or destruction of the original key. */
    public static SharedPreferences unavailablePrefs(Context c) {
        profileFailure(c);
        SharedPreferences.Editor editor=(SharedPreferences.Editor)Proxy.newProxyInstance(Hardening.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.Editor.class},(proxy,method,args)->{
                if(method.getName().equals("commit"))return false;
                if(method.getName().equals("apply"))return null;
                return proxy;
            });
        return (SharedPreferences)Proxy.newProxyInstance(Hardening.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                String name=method.getName();
                if(name.equals("getAll"))return java.util.Collections.emptyMap();
                if(name.equals("contains"))return false;
                if(name.equals("edit"))return editor;
                if(name.startsWith("get") && args!=null && args.length==2)return args[1];
                return null;
            });
    }
}
