package com.boiled.spotx;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Public CDN artwork only, bounded downloads/decodes, latest result wins. */
public final class ArtworkLoader {
    private static final Map<Object,Task> ACTIVE=new WeakHashMap<>();
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static{WORK.allowCoreThreadTimeOut(true);}
    private ArtworkLoader(){}
    public static synchronized void load(Object owner,String url,boolean pip) {
        cancel(owner);
        if(!Policy.artwork(url))return;
        Task task=new Task(owner,url,pip);ACTIVE.put(owner,task);
        task.enqueue();
    }
    public static synchronized void cancel(Object owner) {
        Task old=ACTIVE.remove(owner);
        if(old!=null){
            old.cancelled=true;
            MAIN.removeCallbacks(old.retry);
            // The worker can clear this volatile field between two reads.
            HttpURLConnection connection=old.connection;
            if(connection!=null)connection.disconnect();
            if(old.future!=null)old.future.cancel(true);WORK.purge();
        }
    }
    private static void invoke(Object owner,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args);
    }
    private static Field field(Object owner,String name)throws Exception{
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static final class Task implements Runnable {
        final WeakReference<Object> owner;final String url;final boolean pip;
        volatile boolean cancelled;volatile HttpURLConnection connection;Future<?> future;
        int attempts;final Runnable retry=this::enqueue;
        Task(Object o,String u,boolean p){owner=new WeakReference<>(o);url=u;pip=p;}
        private boolean current(Object target){
            if(cancelled||target==null||ACTIVE.get(target)!=this)return false;
            try{return url.equals(field(target,pip?"lastPipCoverUrl":"lastCoverUrl").get(target));}
            catch(Exception error){return false;}
        }
        private void enqueue(){
            synchronized(ArtworkLoader.class){
                Object target=owner.get();
                if(!current(target)){if(target!=null&&ACTIVE.get(target)==this)ACTIVE.remove(target);return;}
                attempts++;
                try{future=WORK.submit(this);}catch(java.util.concurrent.RejectedExecutionException error){failed();}
            }
        }
        private void failed(){
            synchronized(ArtworkLoader.class){
                Object target=owner.get();
                if(current(target)&&attempts<3)MAIN.postDelayed(retry,attempts==1?1000:4000);
                else if(target!=null&&ACTIVE.get(target)==this)ACTIVE.remove(target);
            }
        }
        @Override public void run(){
            Bitmap bitmap=null;
            try{bitmap=read();}catch(Exception e){}
            if(bitmap==null){MAIN.post(this::failed);return;}
            Bitmap result=bitmap;
            MAIN.post(()->{
                Object target=owner.get();
                try{
                    synchronized(ArtworkLoader.class){
                        if(cancelled||target==null||ACTIVE.get(target)!=this){result.recycle();return;}
                        ACTIVE.remove(target);
                    }
                    if(!url.equals(field(target,pip?"lastPipCoverUrl":"lastCoverUrl").get(target))){result.recycle();return;}
                    field(target,pip?"pipCoverBitmap":"coverBitmap").set(target,result);
                    if(pip){
                        Method m=target.getClass().getDeclaredMethod("fetchPipCover$lambda$0$0",target.getClass(),Bitmap.class);
                        m.setAccessible(true);m.invoke(null,target,result);
                    }else{
                        invoke(target,"updateMetadata",new Class<?>[0]);
                        invoke(target,"showNotification",new Class<?>[0]);
                        invoke(target,"pushWidgetState",new Class<?>[]{boolean.class},true);
                    }
                }catch(Exception e){}
            });
        }
        Bitmap read()throws Exception{
            String next=url;long deadline=android.os.SystemClock.elapsedRealtime()+15000;
            for(int hop=0;hop<5;hop++){
                if(cancelled||!Policy.artwork(next))return null;
                connection=(HttpURLConnection)new URL(next).openConnection();
                try{
                    connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                    int status=connection.getResponseCode();
                    if(status==301||status==302||status==303||status==307||status==308){
                        String location=connection.getHeaderField("Location");if(location==null)return null;
                        next=new URL(new URL(next),location).toString();continue;
                    }
                    if(status!=200||connection.getContentLengthLong()>8*1024*1024)return null;
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=connection.getInputStream()){
                        byte[] buf=new byte[32768];int n;
                        while((n=in.read(buf))!=-1){
                            if(cancelled||bytes.size()+n>8*1024*1024||android.os.SystemClock.elapsedRealtime()>deadline)return null;
                            bytes.write(buf,0,n);
                        }
                    }
                    byte[] data=bytes.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                    if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>8192||bounds.outHeight>8192||((long)bounds.outWidth*bounds.outHeight)>32000000)return null;
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                    while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
                    Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(image==null)return null;
                    float scale=Math.min(1f,512f/Math.max(image.getWidth(),image.getHeight()));
                    if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)),true);if(image!=resized)image.recycle();image=resized;}
                    return image;
                }finally{connection.disconnect();connection=null;}
            }
            return null;
        }
    }
}
