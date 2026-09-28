package com.c11.cartool;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * 开机广播接收器：开机后自动启动零跑系统 Demo 软件以开启 WiFi ADB
 *
 * 流程：
 * 1. 收到 BOOT_COMPLETED 广播
 * 2. 检查用户是否在设置中开启了"开机自动开启 WiFi ADB"
 * 3. 延迟 45 秒（等系统完全启动、WiFi 就绪、无障碍服务已连接）
 * 4. 启动 com.leapmotor.system.demo.DemoActivity
 * 5. AdbWifiAccessibilityService 检测到 DemoActivity 窗口后自动点击 "turn on adb wifi" 按钮
 * 6. 点击成功后发送完成广播
 *
 * 注意：
 * - 用户需要先在系统设置中手动开启本应用的无障碍服务权限
 * - 用户需要在本应用设置中开启"开机自动开启 WiFi ADB"开关
 * - 延迟时间可以根据实际情况调整
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /** SharedPreferences 文件名 */
    public static final String PREFS_NAME = "c11_cartool_prefs";

    /** 开机自动开启 WiFi ADB 的开关 key */
    public static final String KEY_AUTO_START_ADB_WIFI = "auto_start_adb_wifi";

    /** 开机延迟时间（毫秒），默认 45 秒 */
    public static final long DEFAULT_BOOT_DELAY_MS = 45000;

    /** 延迟时间设置 key */
    public static final String KEY_BOOT_DELAY_MS = "boot_delay_ms";

    /** 延迟启动 Demo 的 action（AlarmManager 精确调度，进程被杀也能触发） */
    public static final String ACTION_START_DEMO = "com.c11.cartool.action.START_DEMO";

    /** 重试轮次 extra */
    public static final String EXTRA_RETRY = "retry";

    /** 启动失败后的最大重试轮次 */
    private static final int MAX_START_RETRY = 2;

    /** 重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 15000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "Received broadcast: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            handleBootCompleted(context);
        } else if (ACTION_START_DEMO.equals(action)) {
            // [FIX] 延迟任务真正落地点；启动失败自动重试，不再一失永失
            int retry = intent.getIntExtra(EXTRA_RETRY, 0);
            if (!startDemoActivity(context) && retry < MAX_START_RETRY) {
                Log.w(TAG, "startDemoActivity 失败，安排重试 " + (retry + 1));
                scheduleStartDemo(context, RETRY_DELAY_MS, retry + 1);
            }
        }
    }

    /**
     * 处理开机完成广播
     */
    private void handleBootCompleted(Context context) {
        // 开机即开始日志落盘（下载目录/软件同名目录，logcat + 软件日志，始终存储）
        LogStore.init(context.getApplicationContext());

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 检查用户是否开启了开机自动开启 WiFi ADB
        boolean autoStart = prefs.getBoolean(KEY_AUTO_START_ADB_WIFI, false);
        if (!autoStart) {
            Log.i(TAG, "Auto start ADB WiFi is disabled, skip");
            return;
        }

        // 获取延迟时间
        long delayMs = prefs.getLong(KEY_BOOT_DELAY_MS, DEFAULT_BOOT_DELAY_MS);
        Log.i(TAG, "Auto start ADB WiFi enabled, delay " + delayMs + "ms");

        // [FIX] 用 AlarmManager 精确定时（进程被回收也能触发）；postDelayed 在 receiver 里不可靠
        scheduleStartDemo(context, delayMs, 0);
    }

    /**
     * 安排一次 DemoActivity 启动（AlarmManager 精确闹钟；不可用时退化为 Handler）
     */
    private static void scheduleStartDemo(Context context, long delayMs, int retry) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(context, BootReceiver.class);
            i.setAction(ACTION_START_DEMO);
            i.putExtra(EXTRA_RETRY, retry);
            PendingIntent pi = PendingIntent.getBroadcast(context, 1001 + retry, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delayMs, pi);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleStartDemo error: " + e.getMessage());
        }
        // 兜底路径：仅当进程存活时生效（劣于 AlarmManager，聊胜于无）
        new Handler(Looper.getMainLooper()).postDelayed(() -> startDemoActivity(context), delayMs);
    }

    /**
     * 启动零跑系统 Demo Activity
     *
     * 启动后，AdbWifiAccessibilityService 会自动检测窗口并点击 "turn on adb wifi" 按钮
     * @return true 表示启动命令已发出
     */
    public static boolean startDemoActivity(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.leapmotor.system", "com.leapmotor.system.demo.DemoActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            Log.i(TAG, "Started DemoActivity: com.leapmotor.system.demo.DemoActivity");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start DemoActivity: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查无障碍服务是否已开启（通过 Secure Settings）
     *
     * @param context 上下文
     * @return true 表示无障碍服务已开启
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        try {
            String enabledServices = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (enabledServices == null || enabledServices.isEmpty()) {
                return false;
            }

            String expectedService = context.getPackageName() + "/" + AdbWifiAccessibilityService.class.getName();
            // [FIX] 精确比较（按 : 分割），避免 contains 前缀误判
            for (String s : enabledServices.split(":")) {
                if (expectedService.equals(s.trim())) return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "isAccessibilityServiceEnabled error: " + e.getMessage());
            return false;
        }
    }
}
