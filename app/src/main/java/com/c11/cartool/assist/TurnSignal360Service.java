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
import com.c11.cartool.vehicle.LogcatVehicleSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 转向灯 360 视觉辅助（前台常驻）。
 *
 * 轮询 logcat -d（1s/轮），取转向灯“最后状态”驱动状态机：
 *  - 转向开：启动 360 → 切后方镜头；若在关闭倒计时则取消（防抖重置）；
 *  - 转向关：进入 2s 倒计时，期间再收到开则取消；到时按返回键退出 360。
 *
 * [v0.3.26] 信号改多通道（按时间序逐行扫描，最后出现状态为准）：
 *  1. C11CarXml 结构化字段：左/右转向灯（CMSlTurnLight / CMSrTurnLight = 1/0）
 *  2. C11CarSomeIp eventId 38697-38702（BBCM_STEERLAMP 权威源 = 1/0）
 *  3. MacroDroid 正则兜底（CMS*.fTurnLightCol:2 / CMS.TurnLight:0）
 * 任一通道给出“开”即开；连续转弯（关 → 2s 内又开）天然不关闭，即“2 秒防抖”。
 */
public class TurnSignal360Service extends Service {

    private static final String TAG = "Turn360";
    public static final String ACTION_START = "com.c11.cartool.turn360.START";
    public static final String ACTION_STOP  = "com.c11.cartool.turn360.STOP";

    private static final String PKG_360 = "com.leapmotor.cameraaround";
    private static final long POLL_MS = 1000;
    private static final long CLOSE_DELAY_MS = 2000;

    // 转向灯开/关（. 为通配，匹配 CMS/CMSf 等前缀；MacroDroid 实测格式兜底）
    private static final Pattern RE_ON =
            Pattern.compile("CMS.fTurnLightCol\\s+setTextContent\\s*:\\s*2");
    private static final Pattern RE_OFF =
            Pattern.compile("CMS.TurnLight\\s+setTextContent\\s*:\\s*0");

    // 结构化 XML 转向灯中文键（LogcatVehicleSource.XML_FIELDS）
    private static final String[] TURN_XML_KEYS = {"左转向灯", "右转向灯"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean active = false;
    private boolean cameraOn = false;
    private final Runnable closeTask = this::closeCamera;

    // [FIX-20261019] 轮询必须跑在专用后台线程：原实现挂在主线程 Handler 上，
    // poll() 里的 Sh.run(logcat) 是 socket 网络 I/O，Android 9 主线程直接抛
    // NetworkOnMainThreadException，导致转向灯信号读不到、且与 DashboardRepository
    // 共用的 adbClient 流被扰乱（数据采集一并失败，UI 顶栏长期 --）。
    private final Thread worker = new Thread(this::pollLoop, "turn360-poll");

    private void pollLoop() {
        while (active) {
            try {
                poll();
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        startForegroundCompat();
        if (!active) {
            active = true;
            worker.start();
            Logger.info(TAG, "转向灯360辅助已启动（后台线程轮询）");
        }
        return START_STICKY;
    }

    /** 每轮读 logcat dump，多通道解析转向灯；本轮“有任何开信号即开”（防开/关交替误判）。 */
    private void poll() {
        if (!Sh.isAdbConnected()) return;
        // 多通道一次抓取：XML 结构化字段 + SomeIP eventId 38697-38702（-b all 兜底历史缓冲）
        Sh.Result r = Sh.run("logcat -d -b all -v brief -s C11CarXml:D C11CarSomeIp:D", 8000);
        if (r == null || r.out == null) return;

        boolean anyOn = false, anyOff = false;

        // 通道2/3：LogcatVehicleSource 结构化解析（XML 左/右转向灯 + eventId 38697-38702 BBCM_STEERLAMP）
        LogcatVehicleSource.Result p = LogcatVehicleSource.parse(r.out);
        for (String key : TURN_XML_KEYS) {
            LogcatVehicleSource.State st = p.xmlStates.get(key);
            if (st != null) {
                if ("1".equals(st.raw)) anyOn = true; else anyOff = true;
            }
        }
        for (int id = 38697; id <= 38702; id++) {
            LogcatVehicleSource.State st = p.eventStates.get(id);
            if (st != null) {
                if ("1".equals(st.raw)) anyOn = true; else anyOff = true;
            }
        }

        // 通道1：MacroDroid 正则兜底（仅当结构化未命中转向灯信号时）
        if (!anyOn && !anyOff) {
            for (String line : r.out.split("\n")) {
                if (RE_ON.matcher(line).find()) { anyOn = true; break; }
                if (RE_OFF.matcher(line).find()) anyOff = true;
            }
        }

        // 本轮有任何“开”信号 → 开（打灯期间日志开/关交替出现时以开为准，避免误关）；
        // 全部为“关”/无信号 → 关。
        if (anyOn || anyOff) apply(anyOn);
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
        Sh.submitAsync(() -> Sh.run("input keyevent 4", 5000));  // 模拟返回键退出（子线程，closeTask 在主线程）
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
        worker.interrupt();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
