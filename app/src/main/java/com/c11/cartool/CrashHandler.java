package com.c11.cartool;

import android.content.Context;
import android.os.Process;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局异常捕获
 *
 * 捕获 APP 未处理的异常，保存崩溃日志到 /sdcard/c11_crash_logs/
 * 防止 APP 崩溃时用户看不到任何信息
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    // [FIX] 不再硬编码 /sdcard，使用 App 私有外部目录（兼容 Android 10+ 分区存储）
    private static String crashDir() {
        java.io.File dir = new java.io.File(Sh.exportDir(), "crash_logs");
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
    }

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
        Logger.info(TAG, "全局异常捕获已安装");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            // 保存崩溃日志
            String crashLog = buildCrashLog(thread, ex);
            saveCrashLog(crashLog);

            // 记录到 Logger
            Logger.error(TAG, "APP 崩溃: " + ex.getMessage());
            Logger.error(TAG, "崩溃日志已保存到: " + crashDir());

            // 打印堆栈
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Logger.error(TAG, sw.toString());

        } catch (Exception e) {
            // 确保异常处理本身不会导致二次崩溃
            android.util.Log.e(TAG, "CrashHandler error: " + e.getMessage());
        }

        // 交给默认处理器（通常会杀死进程）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private String buildCrashLog(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        sb.append("═══════════════════════════════════════════════\n");
        sb.append("  C11 车控 APP 崩溃日志\n");
        sb.append("═══════════════════════════════════════════════\n\n");
        sb.append("崩溃时间: ").append(sdf.format(new Date())).append("\n");
        sb.append("APP 版本: " + AppInfo.VERSION + "\n");
        sb.append("崩溃线程: ").append(thread.getName()).append(" (id=").append(thread.getId()).append(")\n");
        sb.append("异常类型: ").append(ex.getClass().getName()).append("\n");
        sb.append("异常信息: ").append(ex.getMessage()).append("\n\n");

        // 设备信息
        sb.append("【设备信息】\n");
        sb.append("  Android 版本: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        sb.append("  SDK 版本: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        sb.append("  设备型号: ").append(android.os.Build.MODEL).append("\n");
        sb.append("  品牌: ").append(android.os.Build.BRAND).append("\n");
        sb.append("  制造商: ").append(android.os.Build.MANUFACTURER).append("\n");
        sb.append("  构建号: ").append(android.os.Build.DISPLAY).append("\n\n");

        // 堆栈信息
        sb.append("【堆栈信息】\n");
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        sb.append(sw.toString()).append("\n");

        // 最近的命令日志
        sb.append("【最近 20 条命令日志】\n");
        sb.append(Logger.getRecentLogs(20)).append("\n");

        sb.append("═══════════════════════════════════════════════\n");
        return sb.toString();
    }

    private void saveCrashLog(String crashLog) {
        try {
            String dirPath = crashDir();
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String filename = "crash_" + sdf.format(new Date()) + ".txt";
            File file = new File(dir, filename);

            FileWriter fw = new FileWriter(file);
            fw.write(crashLog);
            fw.close();

            android.util.Log.i(TAG, "崩溃日志已保存: " + file.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e(TAG, "保存崩溃日志失败: " + e.getMessage());
        }
    }
}
