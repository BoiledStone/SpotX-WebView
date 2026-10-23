package com.boiled.spotx.tests;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.webkit.WebView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Temporary, separately signed diagnostic APK. Never reads cookies or profile storage. */
public final class LiveInstrumentation extends Instrumentation {
    private void snapshot(File folder) throws Exception {
        NotificationManager manager=(NotificationManager)getTargetContext().getSystemService("notification");
        for(StatusBarNotification posted:manager.getActiveNotifications()) {
            if(!posted.getPackageName().equals(getTargetContext().getPackageName()))continue;
            Notification notification=posted.getNotification();
            MediaSession.Token token=notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            if(token==null)continue;
            MediaController controller=new MediaController(getTargetContext(),token);
            JSONObject value=new JSONObject();
            MediaMetadata metadata=controller.getMetadata();
            if(metadata!=null) {
                value.put("title",metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
                value.put("artist",metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
                value.put("duration",metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
                android.graphics.Bitmap cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if(cover==null)cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                value.put("cover",cover==null?JSONObject.NULL:cover.getWidth()+"x"+cover.getHeight());
            }
            android.media.session.PlaybackState state=controller.getPlaybackState();
            if(state!=null){value.put("state",state.getState());value.put("position",state.getPosition());}
            value.put("notificationLargeIcon",notification.getLargeIcon()!=null);
            Files.write(new File(folder,"native-status.json").toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
            File command=new File(folder,"media-command");
            if(command.exists()) {
                String action=new String(Files.readAllBytes(command.toPath()),StandardCharsets.UTF_8).trim();command.delete();
                if(action.equals("pause"))controller.getTransportControls().pause();
                if(action.equals("play"))controller.getTransportControls().play();
                if(action.equals("next"))controller.getTransportControls().skipToNext();
            }
            return;
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        File stop = new File(getTargetContext().getExternalFilesDir("validation"), "stop-live");
        stop.delete();
        try {
            runOnMainSync(() -> WebView.setWebContentsDebuggingEnabled(true));
            Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySync(launch);
            Bundle ready = new Bundle(); ready.putString("stream", "Live WebView diagnostic ready; session storage retained.\n");
            sendStatus(0, ready);
            long deadline = System.currentTimeMillis() + 20 * 60 * 1000;
            while (!stop.exists() && System.currentTimeMillis() < deadline) {
                try{snapshot(stop.getParentFile());}catch(Exception ignored){}
                Thread.sleep(500);
            }
        } catch (Exception failure) {
            Bundle error = new Bundle(); error.putString("stream", failure.getClass().getSimpleName()); sendStatus(-1, error);
        } finally {
            runOnMainSync(() -> WebView.setWebContentsDebuggingEnabled(false));
            stop.delete();
            finish(0, new Bundle());
        }
    }
}
