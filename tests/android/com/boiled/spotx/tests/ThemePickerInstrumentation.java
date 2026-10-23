package com.boiled.spotx.tests;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.boiled.spotx.ThemePicker;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Brief, reversible theme validation. Only ThemePreset and MaterialYou are changed;
 * both are restored through the application callbacks even when an assertion fails. */
public final class ThemePickerInstrumentation extends Instrumentation{
    private Activity activity;private int checks;private Map<?,?> active;
    private String originalPreset,beforeThemes,originalSecondary;private boolean originalMaterial,originalAmoled,restoreNeeded;
    private interface Work{void run()throws Exception;}
    private void ui(Work work)throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();
        runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});
        if(failure.get() instanceof Exception)throw (Exception)failure.get();
        if(failure.get() instanceof Error)throw (Error)failure.get();
        waitForIdleSync();
    }
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static Object field(Object object,String name)throws Exception{Field value=object.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(object);}
    private Object session(){return active.get(activity);}
    private AlertDialog dialog()throws Exception{return (AlertDialog)field(session(),"dialog");}
    private String preferences(){
        SharedPreferences p=getTargetContext().getSharedPreferences("spotilol_prefs",0);
        return p.getString("ThemePreset","spotilol")+"|"+p.getBoolean("MaterialYou",false)+"|"+p.getBoolean("AmoledTheme",false)+"|"+p.getString("ThemeSecondary",null);
    }
    private Method themeMethod(String name,Class<?> argument)throws Exception{
        Class<?> type=Class.forName("com.project.lol.ui.MainActivity");return type.getMethod(name,type,argument);
    }
    private void screenshot(String name)throws Exception{
        Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"theme gallery screenshot is available");
        try{
            File directory=getTargetContext().getExternalFilesDir("validation");
            if(directory==null||(!directory.isDirectory()&&!directory.mkdirs()))throw new IllegalStateException("Private validation directory unavailable");
            try(FileOutputStream output=new FileOutputStream(new File(directory,name))){check(image.compress(Bitmap.CompressFormat.PNG,100,output),"private gallery screenshot is saved: "+name);}
        }finally{image.recycle();}
    }
    private void keyboardScenario()throws Exception{
        ui(()->{
            View card=dialog().getWindow().getDecorView().findViewWithTag("spotx-theme:rose_pine");
            check(card!=null&&card.performClick(),"a theme found by the accented query can be selected");
            EditText input=(EditText)field(session(),"search");input.requestFocus();input.setSelection(input.length());
            InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            check(keyboard!=null,"Android input method service is available");keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        });
        // The IME animation must finish while the main thread remains free to resize the dialog.
        Thread.sleep(650);waitForIdleSync();
        try{
            screenshot("theme-gallery-keyboard.png");
            ui(()->{
                View decor=dialog().getWindow().getDecorView();EditText input=(EditText)field(session(),"search");
                Rect frame=new Rect();decor.getWindowVisibleDisplayFrame(frame);
                if(Build.VERSION.SDK_INT>=30){WindowInsets insets=input.getRootWindowInsets();check(insets!=null&&insets.isVisible(WindowInsets.Type.ime()),"the real Android keyboard is visible during layout checks");}
                else check(frame.bottom<activity.getResources().getDisplayMetrics().heightPixels-Math.round(100*activity.getResources().getDisplayMetrics().density),"the real keyboard reduces the visible display frame");
                View card=decor.findViewWithTag("spotx-theme:rose_pine");Rect shown=new Rect();int touch=Math.round(44*activity.getResources().getDisplayMetrics().density);
                check(card!=null&&card.getGlobalVisibleRect(shown)&&shown.intersect(frame)&&shown.height()>=touch&&shown.width()>=touch,"filtered theme card retains a visible 44dp target with the keyboard open");
                for(String name:new String[]{"apply","cancel"}){
                    View button=decor.findViewWithTag("spotx-theme-"+name);int[] position=new int[2];button.getLocationOnScreen(position);
                    Rect bounds=new Rect(position[0],position[1],position[0]+button.getWidth(),position[1]+button.getHeight());
                    check(button.getGlobalVisibleRect(new Rect())&&frame.contains(bounds),name+" remains fully inside the display frame above the keyboard");
                }
            });
        }finally{
            ui(()->{
                if(session()==null)return;EditText input=(EditText)field(session(),"search");
                InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();
                dialog().getWindow().getDecorView().findViewWithTag("spotx-theme-gallery").requestFocus();
            });
            Thread.sleep(500);waitForIdleSync();
        }
        ui(()->check(((View)field(session(),"previewColumn")).getVisibility()==View.VISIBLE,"preview returns after closing the keyboard"));
    }
    private static Throwable combine(Throwable previous,Throwable next){if(previous==null)return next;previous.addSuppressed(next);return previous;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();AtomicInteger advanced=new AtomicInteger();AtomicReference<Object> wrapped=new AtomicReference<>();
        AtomicReference<Object> cancelledSession=new AtomicReference<>();AtomicReference<String> appliedId=new AtomicReference<>();Throwable outcome=null;
        try{
            Field map=ThemePicker.class.getDeclaredField("ACTIVE");map.setAccessible(true);active=(Map<?,?>)map.get(null);
            activity=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"com.project.lol.ui.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(active.isEmpty(),"no gallery was already being edited before the test");
            Class<?> function=Class.forName("qo1");
            Object unit=Class.forName("lx5").getField("a").get(null);
            Object original=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("equals"))return proxy==args[0];
                    return "Theme validation callback";
                }
                check(active.isEmpty(),"advanced callback runs after the gallery closes");advanced.incrementAndGet();return unit;
            });
            SharedPreferences themePrefs=getTargetContext().getSharedPreferences("spotilol_prefs",0);
            originalPreset=themePrefs.getString("ThemePreset","spotilol");originalMaterial=themePrefs.getBoolean("MaterialYou",false);
            originalAmoled=themePrefs.getBoolean("AmoledTheme",false);originalSecondary=themePrefs.getString("ThemeSecondary",null);
            String before=preferences();beforeThemes=before;
            ui(()->{
                check(ThemePicker.wrap("Unrelated setting",original)==original,"unrelated settings keep their action");
                String title=activity.getString(0x7f11031a);Object action=ThemePicker.wrap(title,original);wrapped.set(action);
                check(action!=original&&function.isInstance(action),"theme entry returns a native gallery action implementing qo1");
                check(ThemePicker.wrap(title,original)==action,"wrapper identity is stable across recomposition");
                check(ThemePicker.wrap(title,action)==action,"an action is never wrapped twice");
                action.getClass().getMethod("invoke").invoke(action);
            });
            ui(()->{
                check(session()!=null&&dialog().isShowing(),"theme gallery is visible");
                check(before.equals(preferences()),"opening the gallery does not change theme preferences");
                @SuppressWarnings("unchecked") List<Object> themes=(List<Object>)field(session(),"all");
                check(themes.size()>40,"gallery reuses the existing preset catalogue");
                Object current=field(session(),"selected");Object candidate=null;
                for(Object theme:themes)if(!field(theme,"id").equals(field(current,"id"))){candidate=theme;break;}
                check(candidate!=null,"another theme is available for preview");
                String id=(String)field(candidate,"id");View card=dialog().getWindow().getDecorView().findViewWithTag("spotx-theme:"+id);
                check(card!=null&&card.performClick(),"a theme card can be selected");
                check(field(field(session(),"selected"),"id").equals(id),"selection updates the preview model");
                LinearLayout preview=(LinearLayout)field(session(),"preview");int expected=(Boolean)field(session(),"amoled")?Color.BLACK:(Integer)field(candidate,"background");
                check(((GradientDrawable)preview.getBackground()).getColor().getDefaultColor()==expected,"preview uses the preset background or active AMOLED black");
                check(before.equals(preferences()),"changing the preview does not change preferences");
                ((EditText)field(session(),"search")).setText("no-theme-matches-this-fixture");
                check(((List<?>)field(session(),"cards")).isEmpty(),"search handles an empty result without showing unrelated cards");
                ((EditText)field(session(),"search")).setText("rôsé");
                check(dialog().getWindow().getDecorView().findViewWithTag("spotx-theme:rose_pine")!=null,"search ignores accents in theme names");
            });
            keyboardScenario();
            ui(()->{
                cancelledSession.set(session());dialog().getWindow().getDecorView().findViewWithTag("spotx-theme-cancel").performClick();
            });
            ui(()->{
                check(active.isEmpty(),"Cancel releases the gallery session");
                check(!(Boolean)field(cancelledSession.get(),"registered"),"Cancel unregisters lifecycle callbacks");
                check(before.equals(preferences()),"Cancel preserves every existing theme preference");
                check(advanced.get()==0,"Cancel never invokes the advanced action");
            });
            ui(()->wrapped.get().getClass().getMethod("invoke").invoke(wrapped.get()));
            ui(()->{
                check(session()!=null&&dialog().isShowing(),"gallery can be opened again after cancellation");
                dialog().getWindow().getDecorView().findViewWithTag("spotx-theme-advanced").performClick();
            });
            check(advanced.get()==1,"advanced customization invokes the original action exactly once");
            check(before.equals(preferences()),"advanced routing does not apply a preview");

            // Exercise the real Apply button, including the Material You precedence bug.
            // No playback controls, accounts, profiles or cookie storage are accessed.
            restoreNeeded=true;
            ui(()->themeMethod("C",boolean.class).invoke(null,activity,true));
            ui(()->wrapped.get().getClass().getMethod("invoke").invoke(wrapped.get()));
            ui(()->{
                check(session()!=null&&dialog().isShowing(),"gallery opens while system colors are enabled");
                check(themePrefs.getBoolean("MaterialYou",false),"system colors are active before the preset is applied");
                @SuppressWarnings("unchecked") List<Object> themes=(List<Object>)field(session(),"all");
                Object candidate=null;
                for(Object theme:themes)if(!field(theme,"id").equals(originalPreset)){candidate=theme;break;}
                check(candidate!=null,"a different real preset is available");String id=(String)field(candidate,"id");appliedId.set(id);
                View card=dialog().getWindow().getDecorView().findViewWithTag("spotx-theme:"+id);
                check(card!=null&&card.performClick(),"real preset is selected before application");
                check(themePrefs.getString("ThemePreset","spotilol").equals(originalPreset)&&themePrefs.getBoolean("MaterialYou",false),"preview still leaves the active theme and system colors unchanged");
            });
            screenshot("theme-gallery.png");
            ui(()->check(dialog().getWindow().getDecorView().findViewWithTag("spotx-theme-apply").performClick(),"Apply button accepts the selected preset"));
            ui(()->{
                check(appliedId.get().equals(themePrefs.getString("ThemePreset","spotilol")),"Apply stores the chosen preset through the application callback");
                check(!themePrefs.getBoolean("MaterialYou",true),"Apply disables system colors so the selected preset takes effect");
                check(active.isEmpty(),"Apply closes and releases the gallery");
                check(originalAmoled==themePrefs.getBoolean("AmoledTheme",false),"applying a preset preserves AMOLED");
                check(java.util.Objects.equals(originalSecondary,themePrefs.getString("ThemeSecondary",null)),"applying a preset preserves the existing secondary color");
            });
        }catch(Throwable error){
            outcome=error;
        }finally{
            if(activity!=null&&active!=null)try{ui(()->{Object current=session();if(current!=null)((AlertDialog)field(current,"dialog")).dismiss();});}catch(Throwable error){outcome=combine(outcome,error);}
            if(restoreNeeded){
                // Independent attempts ensure the original system-color setting is restored
                // even if restoration of the preset unexpectedly fails.
                try{ui(()->themeMethod("p0",String.class).invoke(null,activity,originalPreset));}catch(Throwable error){outcome=combine(outcome,error);}
                try{ui(()->themeMethod("C",boolean.class).invoke(null,activity,originalMaterial));}catch(Throwable error){outcome=combine(outcome,error);}
            }
            if(beforeThemes!=null)try{check(beforeThemes.equals(preferences()),"all four whitelisted theme preferences match their original values after restoration");}catch(Throwable error){outcome=combine(outcome,error);}
        }
        result.putInt("checks",checks);
        if(outcome==null){result.putString("status","PASS");result.putString("stream","PASS "+checks+" native theme gallery assertions; real preset applied briefly and original theme settings restored\n");finish(Activity.RESULT_OK,result);}
        else{result.putString("status","FAIL");String message=outcome.getClass().getSimpleName()+": "+outcome.getMessage();for(Throwable extra:outcome.getSuppressed())message+="; cleanup: "+extra.getClass().getSimpleName()+": "+extra.getMessage();result.putString("stream",message+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
