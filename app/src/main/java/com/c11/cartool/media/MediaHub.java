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

import com.c11.cartool.Logger;
import com.c11.cartool.Sh;

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

        /** [v0.3.18] 自动识别到当前播放器（前台应用 / 通知栏通道），pkg 已自动 select。 */
        default void onPlayerDetected(String pkg, String via) {}
    }

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences sp;
    private Listener listener;   // [v0.3.18] 非 final：卡片可后置注册

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

    /** [v0.3.18] 允许卡片在构造后注册为监听者（onCreate 时 hub 先行创建）。 */
    public void setListener(Listener l) {
        this.listener = l;
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

    // ── [v0.3.18] 自动识别当前播放器（双通道：通知栏优先，前台应用兜底）──

    /** 防抖窗口：同一识别结果 10s 内不重复切换，避免每轮快照反复 select。 */
    private static final long AUTO_DEBOUNCE_MS = 10000;
    private volatile String lastAutoPkg = null;
    private volatile long lastAutoTs = 0;

    /** 系统/桌面等非音乐包，不参与自动识别。 */
    private static final String[] NOT_MUSIC = {
            "com.android.systemui", "com.android.launcher", "com.leapmotor",
            "com.c11.cartool", "android", "com.android.settings"
    };

    /**
     * 自动识别当前播放器并接入（ADB 通道，内部子线程执行，不阻塞主线程）。
     * 识别优先级：通知栏媒体通知 → 前台应用。命中音乐关键词且与上次不同才切换（防抖）。
     * 仅在 ADB 已连接时执行；失败静默（不打扰用户手动选择）。
     */
    public void autoDetect() {
        Sh.submitAsync(() -> {
            try {
                if (!Sh.isAdbConnected() || pm == null) return;
                String pkg = null;
                String via = null;

                // 通道1：通知栏媒体通知（正在播放的 App 几乎必有媒体通知）
                Sh.Result notif = Sh.run("dumpsys notification --noredact", 8000);
                if (notif.ok() && notif.out != null) {
                    pkg = parseNotifPlayer(notif.out);
                    if (pkg != null) via = "通知栏";
                }

                // 通道2：前台应用（通知栏未命中时兜底）
                if (pkg == null) {
                    Sh.Result act = Sh.run("dumpsys activity activities | grep -m1 mResumedActivity", 8000);
                    if (act.ok() && act.out != null) {
                        pkg = parseResumed(act.out);
                        if (pkg != null) via = "前台应用";
                    }
                }

                if (pkg == null || !isMusicPkg(pkg)) return;

                // 防抖 + 变化检测
                long now = System.currentTimeMillis();
                if (now - lastAutoTs < AUTO_DEBOUNCE_MS) return;
                if (pkg.equals(lastAutoPkg)) return;
                lastAutoPkg = pkg;
                lastAutoTs = now;

                Logger.info(TAG, "自动识别播放器[" + via + "]: " + pkg);
                select(pkg);
                if (listener != null) listener.onPlayerDetected(pkg, via);
            } catch (Exception e) {
                Logger.warn(TAG, "autoDetect 异常: " + e.getMessage());
            }
        });
    }

    /** 从 dumpsys notification --noredact 输出提取媒体通知包名（逐行 pkg= 收集，取关键词命中首个）。 */
    private String parseNotifPlayer(String dump) {
        String lastPkg = null;
        for (String line : dump.split("\n")) {
            String t = line.trim();
            int i = t.indexOf("pkg=");
            if (i < 0) continue;
            String pkg = t.substring(i + 4);
            int j = pkg.indexOf(' ');
            if (j >= 0) pkg = pkg.substring(0, j);
            pkg = pkg.trim();
            if (pkg.isEmpty()) continue;
            if (pkg.contains("(") || pkg.contains("/")) continue; // 仅取裸包名行
            lastPkg = pkg;
            if (isMusicPkg(pkg)) return pkg;
        }
        return lastPkg; // 兜底返回最近一个包（由调用方 isMusicPkg 再判）
    }

    /** 从 dumpsys activity activities 的 mResumedActivity 行提取前台包名。 */
    private String parseResumed(String dump) {
        for (String line : dump.split("\n")) {
            if (!line.contains("mResumedActivity")) continue;
            int i = line.indexOf(':');
            if (i < 0) continue;
            String rest = line.substring(i + 1).trim();
            int j = rest.indexOf(' ');
            if (j < 0) continue;
            String comp = rest.substring(j + 1).trim(); // com.xxx/.yyy{...}
            int k = comp.indexOf('/');
            if (k < 0) continue;
            String pkg = comp.substring(0, k);
            if (!pkg.isEmpty()) return pkg;
        }
        return null;
    }

    /** 关键词命中（复用播放器筛选 KEYWORDS）且不在非音乐黑名单。 */
    private boolean isMusicPkg(String pkg) {
        String lower = pkg.toLowerCase();
        for (String nope : NOT_MUSIC) if (lower.startsWith(nope)) return false;
        for (String kw : KEYWORDS) if (lower.contains(kw)) return true;
        return false;
    }
}
