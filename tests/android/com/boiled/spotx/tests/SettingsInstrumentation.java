package com.boiled.spotx.tests;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Navigate native settings without touching preferences, sessions or external links. */
public final class SettingsInstrumentation extends Instrumentation {
    private static final List<String> TABS=Arrays.asList("Lecture","Playback","Apparence","Appearance",
        "Contenu","Content","Avancé","Advanced","À propos","Àpropos","About");
    private UiAutomation automation;
    private String target;

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}

    private void freshAccessibility(){
        // Compose does not always invalidate off-screen tab semantics after a
        // horizontal scroll. Requery the provider instead of reusing that cache.
        if(android.os.Build.VERSION.SDK_INT>=33)try{automation.clearCache();}catch(Throwable ignored){}
    }
    private List<AccessibilityNodeInfo> nodes(){
        freshAccessibility();
        List<AccessibilityNodeInfo> result=new ArrayList<>();
        for(AccessibilityWindowInfo window:automation.getWindows())collect(window.getRoot(),result,0);
        if(result.isEmpty())collect(automation.getRootInActiveWindow(),result,0);
        return result;
    }
    private void collect(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> result,int depth){
        if(node==null||depth>45||result.size()>2500)return;
        CharSequence owner=node.getPackageName();
        if(owner!=null&&!target.contentEquals(owner))return;
        result.add(node);
        for(int i=0;i<node.getChildCount();i++)collect(node.getChild(i),result,depth+1);
    }
    private static String text(CharSequence value){return value==null?"":value.toString().trim();}
    private static String canonical(CharSequence value){
        return Normalizer.normalize(text(value),Normalizer.Form.NFKC)
            .replaceAll("[\\p{Z}\\s\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]+"," ")
            .trim().toLowerCase(Locale.ROOT);
    }
    private static boolean labelMatches(CharSequence value,String wanted){
        String expected=canonical(wanted);
        if(canonical(value).equals(expected))return true;
        for(String line:text(value).split("[\\r\\n]+"))if(canonical(line).equals(expected))return true;
        return false;
    }
    private static String tabLabel(AccessibilityNodeInfo node){
        for(String tab:TABS)if(labelMatches(node.getText(),tab)||labelMatches(node.getContentDescription(),tab))return tab;
        return "";
    }
    private static boolean nativeNode(AccessibilityNodeInfo node){
        for(int depth=0;node!=null&&depth<45;depth++,node=node.getParent())
            if(text(node.getClassName()).contains("WebView"))return false;
        return true;
    }
    private AccessibilityNodeInfo find(String... labels){
        List<AccessibilityNodeInfo> all=nodes();
        for(AccessibilityNodeInfo node:all){
            if(!nativeNode(node))continue;
            for(String wanted:labels)if(labelMatches(node.getText(),wanted)
                ||labelMatches(node.getContentDescription(),wanted))return node;
        }
        // The provider can expose a newly visible virtual child before the
        // cached parent reports the new child count. Ask it directly as well.
        for(AccessibilityNodeInfo root:all){
            if(root.getParent()!=null)continue;
            for(String wanted:labels){
                List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByText(wanted);
                for(AccessibilityNodeInfo node:matches)if(nativeNode(node)
                    &&(labelMatches(node.getText(),wanted)||labelMatches(node.getContentDescription(),wanted)))return node;
            }
        }
        return null;
    }
    private AccessibilityNodeInfo waitFor(long timeout,String... labels)throws InterruptedException{
        long deadline=System.currentTimeMillis()+timeout;
        do{
            AccessibilityNodeInfo found=find(labels);if(found!=null)return found;
            Thread.sleep(200);
        }while(System.currentTimeMillis()<deadline);
        return null;
    }
    private boolean clickParent(AccessibilityNodeInfo node){
        for(int depth=0;node!=null&&depth<9;depth++,node=node.getParent()){
            if(!nativeNode(node))return false;
            if(node.isClickable()&&node.isEnabled()){
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }
    private Set<String> tabs(){
        Set<String> found=new LinkedHashSet<>();
        for(AccessibilityNodeInfo node:nodes())if(nativeNode(node)){
            String label=tabLabel(node);if(!label.isEmpty())found.add(label);
        }
        return found;
    }
    private boolean waitForSettings(long timeout)throws InterruptedException{
        long deadline=System.currentTimeMillis()+timeout;
        do{if(tabs().size()>=4)return true;Thread.sleep(200);}while(System.currentTimeMillis()<deadline);
        return false;
    }
    private boolean scrollTabs()throws InterruptedException{
        AccessibilityNodeInfo anchor=find("Avancé","Advanced","Contenu","Content");
        for(AccessibilityNodeInfo parent=anchor;parent!=null;parent=parent.getParent()){
            if(!nativeNode(parent))break;
            Rect bounds=new Rect();parent.getBoundsInScreen(bounds);
            if(parent.isScrollable()&&bounds.width()>bounds.height()*2){
                if(parent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    ||parent.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId())){
                    Thread.sleep(250);return true;
                }
            }
        }
        // Compose may omit its horizontal scroll action. Confine the fallback gesture
        // to the centres of visible tab labels, above all editable setting rows.
        float first=Float.MAX_VALUE,last=-1,y=0;int count=0;
        for(AccessibilityNodeInfo node:nodes()){
            if(!nativeNode(node)||!node.isVisibleToUser()||tabLabel(node).isEmpty())continue;
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);if(bounds.isEmpty())continue;
            float centre=bounds.exactCenterY();
            if(count>0&&Math.abs(centre-y)>bounds.height())continue;
            first=Math.min(first,bounds.exactCenterX());last=Math.max(last,bounds.exactCenterX());
            y=centre;count++;
        }
        if(count<2||last-first<80)return false;
        long downTime=SystemClock.uptimeMillis();
        for(int step=0;step<=10;step++){
            float x=last+(first-last)*step/10f;
            int action=step==0?MotionEvent.ACTION_DOWN:step==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{automation.injectInputEvent(event,true);}finally{event.recycle();}
            Thread.sleep(25);
        }
        Thread.sleep(250);return true;
    }
    private AccessibilityNodeInfo aboutTab()throws InterruptedException{
        for(int attempt=0;attempt<4;attempt++){
            AccessibilityNodeInfo about=find("À propos","Àpropos","About");
            if(about!=null)return about;
            if(!scrollTabs())return null;
        }
        return find("À propos","Àpropos","About");
    }
    private JSONArray versionLabels(){
        Set<String> versions=new LinkedHashSet<>();
        for(AccessibilityNodeInfo node:nodes())if(nativeNode(node)){
            for(String part:text(node.getText()).split("\\n")){
                String label=part.trim();
                // Restrict collection to app/version labels; no arbitrary account text is exported.
                if(label.matches("(?:SpotX WebView(?: Version)?[ ·]*)?v?\\d+\\.\\d+\\.\\d+[ ·]*")
                    ||label.equals("2.0 aperçu"))versions.add(label);
            }
        }
        return new JSONArray(versions);
    }
    private void screenshot(String name){
        try{
            java.io.File folder=getTargetContext().getExternalFilesDir("validation");folder.mkdirs();
            android.graphics.Bitmap bitmap=automation.takeScreenshot();
            if(bitmap!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}
        }catch(Exception ignored){}
    }
    @Override public void onStart(){
        JSONObject report=new JSONObject();boolean opened=false,success=false;
        try{
            target=getTargetContext().getPackageName();
            automation=getUiAutomation();
            AccessibilityServiceInfo info=automation.getServiceInfo();
            info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                |AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                |AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(info);
            Intent launch=new Intent().setComponent(new ComponentName(target,"com.project.lol.ui.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySync(launch);
            report.put("installedVersion",getTargetContext().getPackageManager().getPackageInfo(target,0).versionName);
            AccessibilityNodeInfo gear=waitFor(10000,"Paramètres","Settings");
            report.put("nativeHeaderFound",gear!=null);
            if(gear==null)throw new AssertionError("Native settings header not found");
            boolean clicked=clickParent(gear);report.put("nativeHeaderClicked",clicked);
            if(!clicked)throw new AssertionError("Native settings header has no working accessible click parent");
            opened=waitForSettings(8000);
            report.put("nativeSettingsOpened",opened).put("tabs",new JSONArray(tabs()));
            if(!opened)throw new AssertionError("Header click did not expose native settings tabs");
            screenshot("settings-native.png");
            AccessibilityNodeInfo about=aboutTab();
            report.put("tabsAfterScroll",new JSONArray(tabs()));
            if(about==null)screenshot("settings-tabs-scroll.png");
            boolean aboutClicked=about!=null&&clickParent(about);
            report.put("aboutTabClicked",aboutClicked);
            if(!aboutClicked)throw new AssertionError("Native About tab is not accessible");
            Thread.sleep(500);
            screenshot("settings-about.png");
            report.put("aboutVersionLabels",versionLabels());
            report.put("currentRepositoryLabel",find("github.com/BoiledStone/SpotX-WebView")!=null);
            report.put("legacyRepositoryLabel",find("github.com/lyssadev/Spotilol")!=null);
            boolean issues=find("Signaler un problème","Report an issue")!=null;
            report.put("issueReportingLabel",issues);
            if(!issues)throw new AssertionError("Issue reporting entry is missing");
            success=true;
        }catch(Throwable failure){
            try{report.put("failure",failure.getClass().getSimpleName()+": "+failure.getMessage());}catch(Exception ignored){}
        }finally{
            if(automation!=null){
                try{if(opened||tabs().size()>=4)sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);}catch(Exception ignored){}
            }
            Bundle result=new Bundle();result.putString("stream",report.toString()+"\n");
            sendStatus(success?0:-1,result);finish(success?0:1,result);
        }
    }
}
