package com.c11.cartool.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放器选择与方控对接（Android 9 / API 28）。
 *
 * <p>优先用 {@link MediaBrowser} 连接目标播放器的 MediaBrowserService（车机版通常 exported），
 * 拿到 {@link MediaController} 后读取标题 / 艺术家 / 封面与播放状态，并通过 TransportControls 控制；
 * 连接失败时控制方法返回 {@code false}，由 UI 层降级为 shell {@code input keyevent}。
 * 媒体按键经 {@link MediaButtonReceiver} 转发到当前控制器（标准方控路由，真机验证）。
 */
public class MediaHub {

    private static final String TAG = "MediaHub";
    private static final String PREF = "c11_media";
    private static final String KEY_PKG = "player_pkg";
    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";

    private static final String[] KEYWORDS = {
            "music", "player", "media", "audio", "qishui", "luna",
            "cloudmusic", "qqmusic", "kugou", "kuwo", "migu"
    };

    /** 可选择的播放器。 */
    public static class PlayerInfo {
        public final String pkg;
        public final String name;

        PlayerInfo(String pkg, String name) {
            this.pkg = pkg;
            this.name = name;
        }

        @Override public String toString() { return name; }
    }

    public interface Listener {
        void onMetadata(String title, String artist, Bitmap cover, boolean playing);
    }

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences sp;
    private final Listener listener;

    private MediaBrowser browser;
    private MediaController controller;
    private String currentPkg;

    public MediaHub(Context context, Listener l) {
        this.ctx = context.getApplicationContext();
        this.pm = this.ctx.getPackageManager();
        this.sp = this.ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        this.listener = l;
        this.currentPkg = sp.getString(KEY_PKG, null);
    }

    public String getSavedPackage() { return currentPkg; }

    // ── 播放器列表 ──

    public List<PlayerInfo> listPlayers() {
        List<PlayerInfo> out = new ArrayList<>();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            String pkg = ai.packageName;
            if (pm.getLaunchIntentForPackage(pkg) == null) continue;
            String name = String.valueOf(pm.getApplicationLabel(ai));
            String hay = (pkg + " " + name).toLowerCase();
            for (String kw : KEYWORDS) {
                if (hay.contains(kw)) {
                    out.add(new PlayerInfo(pkg, name));
                    break;
                }
            }
        }
        return out;
    }

    // ── 选择 / 连接 ──

    public void select(String pkg) {
        currentPkg = pkg;
        sp.edit().putString(KEY_PKG, pkg).apply();
        connect();
    }

    /** 连接已保存的播放器（启动时调用）。 */
    public void start() {
        if (currentPkg != null) connect();
    }

    public void release() {
        if (controller != null) {
            controller.unregisterCallback(controllerCallback);
            controller = null;
        }
        if (browser != null) {
            browser.disconnect();
            browser = null;
        }
    }

    private void connect() {
        release();
        ComponentName svc = findBrowserService(currentPkg);
        if (svc == null) {
            Log.i(TAG, "未发现 MediaBrowserService，将降级 shell 按键: " + currentPkg);
            return;
        }
        browser = new MediaBrowser(ctx, svc, connectionCallback, null);
        browser.connect();
    }

    private ComponentName findBrowserService(String pkg) {
        Intent intent = new Intent(MEDIA_BROWSER_SERVICE);
        intent.setPackage(pkg);
        List<ResolveInfo> list = pm.queryIntentServices(intent, 0);
        if (list == null || list.isEmpty()) return null;
        ResolveInfo ri = list.get(0);
        return new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
    }

    private final MediaBrowser.ConnectionCallback connectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() {
                    try {
                        MediaController c = new MediaController(ctx, browser.getSessionToken());
                        c.registerCallback(controllerCallback);
                        controller = c;
                        pushMetadata();
                    } catch (Exception e) {
                        Log.w(TAG, "建立 MediaController 失败: " + e.getMessage());
                    }
                }

                @Override public void onConnectionFailed() {
                    Log.w(TAG, "MediaBrowser 连接失败，降级 shell 按键");
                }
            };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { pushMetadata(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { pushMetadata(); }
    };

    private void pushMetadata() {
        if (listener == null || controller == null) return;
        MediaMetadata md = controller.getMetadata();
        PlaybackState ps = controller.getPlaybackState();

        String title = "";
        String artist = "";
        Bitmap cover = null;
        if (md != null) {
            title = string(md, MediaMetadata.METADATA_KEY_TITLE);
            artist = string(md, MediaMetadata.METADATA_KEY_ARTIST);
            cover = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (cover == null) cover = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        listener.onMetadata(title, artist, cover, playing);
    }

    private static String string(MediaMetadata md, String key) {
        String v = md.getString(key);
        return v == null ? "" : v;
    }

    // ── 控制：成功走 TransportControls；返回 false 让 UI 降级 shell 按键 ──

    public boolean playPause() {
        if (controller == null) return false;
        PlaybackState ps = controller.getPlaybackState();
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
        return true;
    }

    public boolean next() {
        if (controller == null) return false;
        controller.getTransportControls().skipToNext();
        return true;
    }

    public boolean previous() {
        if (controller == null) return false;
        controller.getTransportControls().skipToPrevious();
        return true;
    }

    /** 转发媒体按键（供 {@link MediaButtonReceiver} 调用）。 */
    public void dispatchButton(KeyEvent event) {
        if (controller != null) controller.dispatchMediaButtonEvent(event);
    }
}
