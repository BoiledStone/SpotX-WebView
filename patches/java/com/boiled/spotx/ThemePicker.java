package com.boiled.spotx;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A preview-only gallery; the existing editor and theme application callbacks remain authoritative. */
public final class ThemePicker {
    private static final int THEME_TITLE=0x7f11031a;
    private static final int BG=0xff101612,PANEL=0xff1b241e,TEXT=0xfff1f6f2,MUTED=0xffb7c5bc,ACCENT=0xff91ddb1;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    // Accessed only on the UI thread. Dismissal and owner destruction remove the entry.
    private static final Map<Activity,Session> ACTIVE=new HashMap<>();
    private static WeakReference<Activity> lastActivity=new WeakReference<>(null);
    private static WeakReference<Object> lastOriginal=new WeakReference<>(null),lastWrapper=new WeakReference<>(null);
    private ThemePicker(){}

    public static synchronized Object wrap(String title,Object original){
        if(original==null)return null;
        if(Proxy.isProxyClass(original.getClass())&&Proxy.getInvocationHandler(original) instanceof ThemeClick)return original;
        Activity activity;
        try{
            activity=(Activity)Class.forName("oy4").getField("e").get(null);
            if(activity==null||activity.isFinishing()||activity.isDestroyed()||!activity.getString(THEME_TITLE).equals(title))return original;
            Object cached=lastWrapper.get();
            if(activity==lastActivity.get()&&original==lastOriginal.get()&&cached!=null)return cached;
            Class<?> function=Class.forName("qo1");
            Object wrapper=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},new ThemeClick(activity,original));
            lastActivity=new WeakReference<>(activity);lastOriginal=new WeakReference<>(original);lastWrapper=new WeakReference<>(wrapper);
            return wrapper;
        }catch(Exception error){
            Log.e("SpotX","Theme gallery entry unavailable: "+error.getClass().getSimpleName());
            // The original editor remains usable if the compatibility hook is unavailable.
            return original;
        }
    }
    private static final class ThemeClick implements InvocationHandler{
        private final WeakReference<Activity> owner;private final Object original;
        ThemeClick(Activity activity,Object callback){owner=new WeakReference<>(activity);original=callback;}
        @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
            String name=method.getName();
            if(method.getDeclaringClass()==Object.class){
                if(name.equals("equals"))return proxy==(args==null?null:args[0]);
                if(name.equals("hashCode"))return System.identityHashCode(proxy);
                if(name.equals("toString"))return "SpotX theme gallery action";
            }
            if(!name.equals("invoke")||(args!=null&&args.length!=0))throw new UnsupportedOperationException("Unsupported theme action");
            Activity activity=owner.get();
            if(activity!=null&&!activity.isFinishing()&&!activity.isDestroyed())MAIN.post(()->show(activity,original));
            return Class.forName("lx5").getField("a").get(null);
        }
    }
    private static void show(Activity activity,Object original){
        if(activity.isFinishing()||activity.isDestroyed())return;
        Session current=ACTIVE.get(activity);
        if(current!=null&&current.dialog!=null&&current.dialog.isShowing())return;
        try{
            Session session=new Session(activity,original);
            ACTIVE.put(activity,session);
            try{session.open();}catch(Exception error){session.close();throw error;}
        }catch(Exception error){
            failure(activity,"Impossible d’afficher l’aperçu. Ouverture de l’éditeur avancé.","Could not show the preview. Opening the advanced editor.",error);
            try{callback(original);}catch(Exception fallback){failure(activity,"Impossible d’ouvrir l’éditeur avancé.","Could not open the advanced editor.",fallback);}
        }
    }
    private static boolean french(Activity activity){return "fr".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());}
    private static void failure(Activity activity,String fr,String en,Throwable error){
        Log.e("SpotX","Theme gallery action failed: "+error.getClass().getSimpleName());
        if(!activity.isDestroyed())Toast.makeText(activity,french(activity)?fr:en,Toast.LENGTH_LONG).show();
    }
    private static Object callback(Object action)throws Exception{return action.getClass().getMethod("invoke").invoke(action);}
    private static String folded(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();}
    private static String fieldText(Object object,String name)throws Exception{return String.valueOf(object.getClass().getField(name).get(object));}
    private static int color(Object palette,String name,Method conversion)throws Exception{return (Integer)conversion.invoke(null,palette.getClass().getField(name).getLong(palette));}

    private static final class Theme{
        final String id,name,search;final int accent,onAccent,background,panel,foreground,muted;
        Theme(Object preset,Method conversion)throws Exception{
            id=fieldText(preset,"a");String family=fieldText(preset,"b"),variant=fieldText(preset,"c");
            if("spotilol".equals(id))family="SpotX WebView";
            name=family+" · "+variant;search=folded(name+" "+id);
            Object palette=preset.getClass().getField("d").get(preset);
            accent=color(palette,"a",conversion);onAccent=color(palette,"b",conversion);background=color(palette,"f",conversion);
            panel=color(palette,"g",conversion);foreground=color(palette,"i",conversion);muted=color(palette,"j",conversion);
        }
    }
    private static final class Session implements Application.ActivityLifecycleCallbacks{
        final Activity activity;final Object original;final boolean fr,amoled,system;
        final String originalId;final List<Theme> all=new ArrayList<>();
        final List<View> cards=new ArrayList<>();
        Theme selected;AlertDialog dialog;LinearLayout catalogue,preview,previewColumn;
        TextView previewLabel,count,hint;Button apply;EditText search;boolean registered,closed,advanced,compact;
        View decor;Window window;int initialHeight;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;final Runnable fit=this::fitWindow;
        final float density;final boolean split;
        Session(Activity activity,Object original)throws Exception{
            this.activity=activity;this.original=original;fr=french(activity);
            SharedPreferences prefs=activity.getSharedPreferences("spotilol_prefs",0);
            originalId=prefs.getString("ThemePreset","spotilol");amoled=prefs.getBoolean("AmoledTheme",false);system=prefs.getBoolean("MaterialYou",false);
            density=activity.getResources().getDisplayMetrics().density;
            split=activity.getResources().getDisplayMetrics().widthPixels/density>=600||activity.getResources().getDisplayMetrics().heightPixels/density<520;
            Method conversion=Class.forName("gc0").getMethod("O",long.class);
            Object presets=Class.forName("jo5").getMethod("a").invoke(null);
            if(!(presets instanceof Map))throw new IllegalStateException("Theme catalogue unavailable");
            for(Object preset:((Map<?,?>)presets).values()){
                Theme theme=new Theme(preset,conversion);all.add(theme);if(theme.id.equals(originalId))selected=theme;
            }
            if(all.isEmpty())throw new IllegalStateException("Empty theme catalogue");
            if(selected==null)selected=all.get(0);
        }
        String t(String french,String english){return fr?french:english;}
        int dp(float value){return Math.round(value*density);}
        LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
        LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
        GradientDrawable background(int color,int radius,int border){
            GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));
            if(border!=Color.TRANSPARENT)drawable.setStroke(dp(1),border);return drawable;
        }
        TextView text(String value,float size,int color,boolean bold){
            TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);
            view.setFontFeatureSettings("kern");if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
        }
        LinearLayout.LayoutParams width(int height){return new LinearLayout.LayoutParams(-1,height);}
        void space(LinearLayout parent,int height){parent.addView(new View(activity),width(dp(height)));}
        Button button(String label,boolean primary){
            Button button=new Button(activity);button.setText(label);button.setAllCaps(false);button.setTextSize(14);button.setMinHeight(dp(46));
            button.setMaxLines(2);button.setTextColor(primary?BG:TEXT);button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33000000:0x33ffffff),background(primary?ACCENT:PANEL,12,primary?Color.TRANSPARENT:0xff3a4a40),null));
            button.setPadding(dp(12),dp(4),dp(12),dp(4));return button;
        }
        void open(){
            LinearLayout root=column();root.setTag("spotx-theme-gallery");root.setPadding(dp(18),dp(18),dp(18),dp(14));root.setBackground(background(BG,22,0xff34473a));
            root.addView(text(t("Choisir un thème","Choose a theme"),23,TEXT,true));
            hint=text(system?t("Actuel : couleurs du système. Prévisualise un thème.","Current: system colors. Preview a theme."):t("Essaie une palette avant de l’appliquer.","Preview a palette before applying it."),13,MUTED,false);hint.setPadding(0,dp(4),0,dp(10));root.addView(hint);
            LinearLayout body=split?row():column();
            root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout left=column(),right=column();previewColumn=left;
            if(split){
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,0.44f);lp.rightMargin=dp(14);body.addView(left,lp);
                body.addView(right,new LinearLayout.LayoutParams(0,-1,0.56f));
            }else{body.addView(left,width(-2));body.addView(right,new LinearLayout.LayoutParams(-1,0,1));}
            previewLabel=text("",12,MUTED,true);left.addView(previewLabel);space(left,6);
            preview=column();preview.setTag("spotx-theme-preview");preview.setPadding(dp(12),dp(10),dp(12),dp(10));left.addView(preview,width(-2));
            if(amoled){TextView note=text(t("AMOLED actif : le fond reste noir.","AMOLED is on: the background stays black."),11,MUTED,false);note.setPadding(0,dp(5),0,dp(5));left.addView(note);}
            if(system){TextView note=text(t("Appliquer remplace les couleurs du système.","Applying replaces system colors."),11,MUTED,false);note.setPadding(0,dp(5),0,dp(5));left.addView(note);}
            space(left,10);
            search=new EditText(activity);search.setTag("spotx-theme-search");search.setSingleLine(true);search.setTextSize(14);search.setTextColor(TEXT);search.setHintTextColor(MUTED);
            search.setHint(t("Rechercher un thème","Search themes"));search.setContentDescription(t("Rechercher un thème","Search themes"));
            search.setBackground(background(PANEL,12,0xff34473a));search.setPadding(dp(12),0,dp(12),0);
            right.addView(search,width(dp(46)));space(right,6);count=text("",11,MUTED,false);count.setTag("spotx-theme-count");right.addView(count);space(right,5);
            ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(2));
            catalogue=column();scroll.addView(catalogue,new ScrollView.LayoutParams(-1,-2));right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            space(root,10);LinearLayout actions=row();
            Button cancel=button(t("Annuler","Cancel"),false);cancel.setTag("spotx-theme-cancel");
            apply=button(t("Appliquer","Apply"),true);apply.setTag("spotx-theme-apply");
            LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(0,-2,1);cancelParams.rightMargin=dp(8);actions.addView(cancel,cancelParams);actions.addView(apply,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
            space(root,6);Button advancedButton=button(t("Personnalisation avancée","Advanced customization"),false);advancedButton.setTag("spotx-theme-advanced");root.addView(advancedButton,width(-2));
            dialog=new AlertDialog.Builder(activity).setView(root).create();dialog.setCanceledOnTouchOutside(true);
            cancel.setOnClickListener(view->dialog.dismiss());
            advancedButton.setOnClickListener(view->{advanced=true;dialog.dismiss();});
            apply.setOnClickListener(view->apply());
            dialog.setOnDismissListener(ignored->{
                close();
                if(advanced&&!activity.isFinishing()&&!activity.isDestroyed())MAIN.post(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    try{callback(original);}catch(Exception error){failure(activity,"Impossible d’ouvrir l’éditeur avancé.","Could not open the advanced editor.",error);}
                });
            });
            search.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderCatalogue(s.toString());}
                @Override public void afterTextChanged(Editable value){}
            });
            updatePreview();renderCatalogue("");
            // Do not give the search field initial focus or open the keyboard over the preview.
            root.setFocusableInTouchMode(true);root.requestFocus();
            activity.getApplication().registerActivityLifecycleCallbacks(this);registered=true;
            dialog.show();window=dialog.getWindow();
            if(window!=null){
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                int width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(720));
                initialHeight=Math.min(Math.round(activity.getResources().getDisplayMetrics().heightPixels*0.88f),dp(800));
                // A floating dialog with a fixed requested height is not reliably resized
                // by ADJUST_RESIZE. Fit it explicitly to the display area above the IME.
                window.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
                window.setLayout(width,initialHeight);decor=window.getDecorView();
                layoutListener=this::fitWindow;decor.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);decor.post(fit);
            }
        }
        void fitWindow(){
            if(closed||window==null||decor==null||!decor.isAttachedToWindow())return;
            Rect frame=new Rect();decor.getWindowVisibleDisplayFrame(frame);if(frame.isEmpty())return;
            boolean keyboard;
            WindowInsets insets=decor.getRootWindowInsets();
            if(Build.VERSION.SDK_INT>=30&&insets!=null)keyboard=insets.isVisible(WindowInsets.Type.ime());
            else keyboard=frame.height()<activity.getResources().getDisplayMetrics().heightPixels-dp(150);
            if(compact!=keyboard){
                compact=keyboard;previewColumn.setVisibility(keyboard?View.GONE:View.VISIBLE);hint.setVisibility(keyboard?View.GONE:View.VISIBLE);
            }
            int height=Math.min(initialHeight,Math.max(1,frame.height()-dp(16)));
            int width=Math.min(dp(720),Math.max(1,frame.width()-dp(24)));
            WindowManager.LayoutParams params=window.getAttributes();
            int y=params.y;
            if(decor.isLaidOut()){
                int[] position=new int[2];decor.getLocationOnScreen(position);
                int desiredTop=frame.top+(frame.height()-height)/2;
                // Work from the measured window origin, accounting for status-bar insets
                // and platform-specific floating-window offsets without accumulating them.
                y=Math.max(0,params.y+desiredTop-position[1]);
            }
            if(params.width!=width||params.height!=height||params.y!=y){params.width=width;params.height=height;params.y=y;window.setAttributes(params);}
        }
        void updatePreview(){
            preview.removeAllViews();previewLabel.setText(t("APERÇU · ","PREVIEW · ")+selected.name);
            int base=amoled?Color.BLACK:selected.background,panel=amoled?0xff161616:selected.panel;
            preview.setBackground(background(base,14,0xff445047));
            TextView heading=text("SpotX WebView",13,selected.foreground,true);preview.addView(heading);space(preview,8);
            LinearLayout player=row();player.setPadding(dp(8),dp(8),dp(8),dp(8));player.setBackground(background(panel,10,Color.TRANSPARENT));
            TextView cover=text("♪",23,selected.onAccent,true);cover.setGravity(Gravity.CENTER);cover.setBackground(background(selected.accent,8,Color.TRANSPARENT));player.addView(cover,new LinearLayout.LayoutParams(dp(36),dp(36)));
            LinearLayout meta=column();meta.setPadding(dp(9),0,dp(6),0);meta.addView(text(t("À ton rythme","At your own pace"),13,selected.foreground,true));meta.addView(text(t("Ta musique, tes couleurs","Your music, your colors"),11,selected.muted,false));player.addView(meta,new LinearLayout.LayoutParams(0,-2,1));
            TextView play=text("▶",17,selected.accent,true);play.setGravity(Gravity.CENTER);player.addView(play,new LinearLayout.LayoutParams(dp(25),dp(32)));preview.addView(player,width(-2));
            space(preview,8);View progress=new View(activity);progress.setBackground(background(selected.accent,2,Color.TRANSPARENT));preview.addView(progress,new LinearLayout.LayoutParams(dp(90),dp(3)));
            boolean changed=system||!selected.id.equals(originalId);apply.setEnabled(changed);apply.setAlpha(changed?1f:0.45f);
        }
        void renderCatalogue(String query){
            catalogue.removeAllViews();cards.clear();String needle=folded(query);List<Theme> found=new ArrayList<>();
            for(Theme theme:all)if(theme.search.contains(needle))found.add(theme);
            count.setText(found.size()+t(found.size()==1?" thème":" thèmes",found.size()==1?" theme":" themes"));
            if(found.isEmpty()){
                TextView empty=text(t("Aucun thème ne correspond.","No themes match your search."),14,MUTED,false);empty.setPadding(dp(4),dp(20),dp(4),dp(20));catalogue.addView(empty);return;
            }
            int columns=split?1:2;
            for(int index=0;index<found.size();index+=columns){
                LinearLayout row=row();row.setGravity(Gravity.TOP);
                for(int column=0;column<columns;column++){
                    int at=index+column;
                    if(at>=found.size()){row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));continue;}
                    Theme theme=found.get(at);boolean chosen=theme.id.equals(selected.id),current=!system&&theme.id.equals(originalId);
                    LinearLayout card=column();card.setTag("spotx-theme:"+theme.id);card.setPadding(dp(10),dp(8),dp(10),dp(8));card.setMinimumHeight(dp(86));
                    card.setBackground(background(chosen?0xff233a2c:PANEL,12,chosen?ACCENT:0xff344139));card.setFocusable(true);card.setClickable(true);card.setSelected(chosen);
                    card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),null,background(Color.WHITE,12,Color.TRANSPARENT)));
                    card.setContentDescription(theme.name+(current?t(", thème actuel",", current theme"):"")+(chosen?t(", sélectionné",", selected"):""));
                    LinearLayout swatches=row();swatches.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    int[] colors={theme.accent,theme.panel,amoled?Color.BLACK:theme.background};
                    for(int color:colors){View swatch=new View(activity);swatch.setBackground(background(color,5,0xff67786b));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(23),dp(16));lp.rightMargin=dp(5);swatches.addView(swatch,lp);}
                    TextView mark=text(chosen?"✓":"",13,ACCENT,true);mark.setGravity(Gravity.RIGHT);swatches.addView(mark,new LinearLayout.LayoutParams(0,dp(18),1));card.addView(swatches);space(card,5);
                    TextView name=text(theme.name,12,TEXT,true);name.setMaxLines(2);name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(name);
                    if(current){TextView state=text(t("Actuel","Current"),10,MUTED,false);state.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(state);}
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(column+1<columns)lp.rightMargin=dp(7);row.addView(card,lp);cards.add(card);
                    card.setOnClickListener(view->{selected=theme;updatePreview();renderCatalogue(search.getText().toString());});
                }
                LinearLayout.LayoutParams lp=width(-2);lp.bottomMargin=dp(7);catalogue.addView(row,lp);
            }
        }
        void apply(){
            try{
                Class<?> type=Class.forName("com.project.lol.ui.MainActivity");
                Method material=type.getMethod("C",type,boolean.class),preset=type.getMethod("p0",type,String.class);
                // Preflight both methods before changing anything. The callbacks synchronize Compose,
                // SharedPreferences and the native media surfaces without reloading the player.
                material.invoke(null,activity,false);preset.invoke(null,activity,selected.id);dialog.dismiss();
            }catch(Exception error){failure(activity,"Impossible d’appliquer ce thème. Réessaie dans l’éditeur avancé.","Could not apply this theme. Try the advanced editor.",error);}
        }
        void close(){
            if(closed)return;closed=true;
            if(decor!=null){
                decor.removeCallbacks(fit);
                if(layoutListener!=null&&decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
                layoutListener=null;
            }
            if(dialog!=null&&dialog.isShowing())dialog.dismiss();
            if(registered){activity.getApplication().unregisterActivityLifecycleCallbacks(this);registered=false;}
            if(ACTIVE.get(activity)==this)ACTIVE.remove(activity);
            if(search!=null){InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);}
        }
        @Override public void onActivityDestroyed(Activity owner){if(owner==activity){advanced=false;if(dialog!=null)dialog.dismiss();close();}}
        @Override public void onActivityCreated(Activity a,Bundle state){}
        @Override public void onActivityStarted(Activity a){}
        @Override public void onActivityResumed(Activity a){}
        @Override public void onActivityPaused(Activity a){}
        @Override public void onActivityStopped(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle state){}
    }
}
