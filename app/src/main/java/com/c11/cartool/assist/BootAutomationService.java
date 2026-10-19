package com.c11.cartool.assist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.c11.cartool.BootReceiver;
import com.c11.cartool.LogStore;
import com.c11.cartool.Logger;
import com.c11.cartool.Sh;

/**
 * 开机顺序自动化服务（单次执行，前台 + 子线程）。
 *
 * 顺序（任一步失败仅记录、跳过，不中断）：
 *  1) 日志落盘；
 *  2) 若 adbAuto：开 Demo（无障碍自动点 ADB）→ APP 主动连接并等待就绪；
 *     否则也尝试一次自动连接（ADB 可能已开）；
 *  3) ADB 就绪后：Shizuku → 副屏/仪表启动 → 行人警示/视频/360 限制解除；
 *  4) 若开启转向 360：启动 {@link TurnSignal360Service}。
 */
public class BootAutomationService extends Service {

    private static final String TAG = "BootAuto";
    private static final String CHANNEL = "c11_boot_auto";
    private static final long WAIT_ADB_MS = 90000;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogStore.init(getApplicationContext());
        Sh.setContext(getApplicationContext());
        new Thread(this::runAll, "boot-automation").start();
        return START_NOT_STICKY;
    }

    private void runAll() {
        try { Thread.sleep(3000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return;
        }
        AssistConfig cfg = new AssistConfig(this);

        // ── 2) ADB 通道 ──
        if (cfg.adbAuto()) {
            Logger.info(TAG, "开机自动 ADB：启动系统 Demo");
            BootReceiver.startDemoActivity(getApplicationContext());
            // 无障碍自动点击使 adbd 监听；APP 同时主动连接
            Sh.autoConnectLocal();
        } else if (!Sh.isAdbConnected()) {
            Sh.autoConnectLocal();
        }
        waitAdbReady(cfg.adbAuto());

        if (!Sh.isAdbConnected()) {
            Logger.warn(TAG, "ADB 未就绪，依赖权限的开机项跳过（转向360仍尝试启动）");
        } else {
            // ── 3) ADB 就绪后的各项 ──
            if (cfg.shizuku()) startShizuku();
            if (cfg.subScreen()) startOnDisplay(cfg.subPkg(), cfg.subAct(), AssistConfig.DISPLAY_SUB);
            if (cfg.dashScreen()) startOnDisplay(cfg.dashPkg(), cfg.dashAct(), AssistConfig.DISPLAY_DASH);
            if (cfg.pedestrianOff()) runShell(
                    "am broadcast -a com.leapmotor.speech.tocarcontrol --es type PEDESTRIANS_ALERT --ei state 0");
            if (cfg.videoEnable()) runShell("settings put global C11_VIDEO_ENABLE 1");
            if (cfg.camera360AnySpeed()) runShell("settings put global camera_overspeed 0");
        }

        // ── 4) 转向 360（该服务自身周期轮询，ADB 未就绪会空转等待）──
        if (cfg.turnSignal360()) {
            startService(new Intent(this, TurnSignal360Service.class)
                    .setAction(TurnSignal360Service.ACTION_START));
        }
        Logger.ok(TAG, "开机自动化流程结束");
        stopSelf();
    }

    private void waitAdbReady(boolean longWait) {
        long max = longWait ? WAIT_ADB_MS : 8000;
        long start = System.currentTimeMillis();
        while (!Sh.isAdbConnected() && System.currentTimeMillis() - start < max) {
            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return;
            }
        }
    }

    /** Shizuku（intent 方式）：广播到 ManualStartReceiver。 */
    private void startShizuku() {
        runShell("am broadcast -n moe.shizuku.privileged.api/"
                + "moe.shizuku.manager.receiver.ManualStartReceiver"
                + " -a moe.shizuku.privileged.api.START");
    }

    private void startOnDisplay(String pkg, String act, int display) {
        if (pkg == null || pkg.isEmpty() || act == null || act.isEmpty()) {
            Logger.warn(TAG, "display " + display + " 目标 pkg/act 为空，跳过");
            return;
        }
        runShell("am start -n " + pkg + "/" + act + " --display " + display);
    }

    private void runShell(String cmd) {
        try {
            Sh.Result r = Sh.run(cmd, 10000);
            Logger.cmd(cmd, r);
            if (!r.ok()) Logger.warn(TAG, "执行失败: " + cmd);
        } catch (Exception e) {
            Logger.warn(TAG, "执行异常: " + cmd + " -> " + e.getMessage());
        }
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "开机自动化",
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            startForeground(2, new Notification.Builder(this, CHANNEL)
                    .setContentTitle("C11 开机自动化运行中").build());
        } else {
            startForeground(2, new Notification.Builder(this)
                    .setContentTitle("C11 开机自动化运行中").build());
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
