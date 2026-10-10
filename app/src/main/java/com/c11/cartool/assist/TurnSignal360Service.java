package com.c11.cartool.assist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.c11.cartool.Logger;
import com.c11.cartool.Sh;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 转向灯 360 视觉辅助（前台常驻）。
 *
 * 轮询 logcat -d（1s/轮），取转向灯“最后状态”驱动状态机：
 *  - 转向开（fTurnLightCol:2）：启动 360 → 切后方镜头；若在关闭倒计时则取消（防抖重置）；
 *  - 转向关（TurnLight:0）：进入 2s 倒计时，期间再收到开则取消；到时按返回键退出 360。
 *
 * 连续转弯（关 → 2s 内又开）天然不关闭，即“2 秒防抖”。
 */
public class TurnSignal360Service extends Service {

    private static final String TAG = "Turn360";
    public static final String ACTION_START = "com.c11.cartool.turn360.START";
    public static final String ACTION_STOP  = "com.c11.cartool.turn360.STOP";

    private static final String PKG_360 = "com.leapmotor.cameraaround";
    private static final long POLL_MS = 1000;
    private static final long CLOSE_DELAY_MS = 2000;

    // 转向灯开/关（. 为通配，匹配 CMS/CMSf 等前缀）
    private static final Pattern RE_ON =
            Pattern.compile("CMS.fTurnLightCol\\s+setTextContent\\s*:\\s*2");
    private static final Pattern RE_OFF =
            Pattern.compile("CMS.TurnLight\\s+setTextContent\\s*:\\s*0");

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean active = false;
    private boolean cameraOn = false;
    private final Runnable closeTask = this::closeCamera;
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            poll();
            if (active) main.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        startForegroundCompat();
        if (!active) {
            active = true;
            main.post(pollTask);
            Logger.info(TAG, "转向灯360辅助已启动");
        }
        return START_STICKY;
    }

    /** 每轮读 logcat dump，逐行扫描并以“最后出现”的转向灯信号为准。 */
    private void poll() {
        if (!Sh.isAdbConnected()) return;
        Sh.Result r = Sh.run("logcat -d -v brief -s C11CarXml", 8000);
        if (r == null || r.out == null) return;

        boolean lastOn = false, seen = false;
        for (String line : r.out.split("\n")) {
            if (RE_ON.matcher(line).find())       { lastOn = true;  seen = true; }
            else if (RE_OFF.matcher(line).find()) { lastOn = false; seen = true; }
        }
        if (seen) apply(lastOn);
    }

    private void apply(boolean turnOn) {
        if (turnOn) {
            main.removeCallbacks(closeTask);   // 取消待关闭（防抖重置）
            if (!cameraOn) openCamera();
        } else if (cameraOn) {
            // 转向关：2s 倒计时；期间 poll 再读到开会 removeCallbacks
            main.removeCallbacks(closeTask);
            main.postDelayed(closeTask, CLOSE_DELAY_MS);
        }
    }

    private void openCamera() {
        cameraOn = true;
        Sh.run("am start -n " + PKG_360 + "/.AroundActivity", 8000);
        // 启动后等 600ms 切后方镜头（跨应用，走无障碍）
        main.postDelayed(() -> C11AssistService.clickId("rear_btn", ok ->
                Logger.info(TAG, "切后方镜头 " + (ok ? "成功" : "失败（可手动切）"))), 600);
        Logger.info(TAG, "转向开启 → 启动360");
    }

    private void closeCamera() {
        if (!cameraOn) return;
        cameraOn = false;
        Sh.run("input keyevent 4", 5000);   // 模拟返回键退出
        Logger.info(TAG, "转向关闭防抖结束 → 退出360");
    }

    private void startForegroundCompat() {
        String chId = "c11_turn360";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, "转向灯360辅助",
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            startForeground(1, new Notification.Builder(this, chId)
                    .setContentTitle("转向灯360辅助运行中").build());
        } else {
            startForeground(1, new Notification.Builder(this)
                    .setContentTitle("转向灯360辅助运行中").build());
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
