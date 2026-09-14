package com.c11.cartool;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日志系统: Logcat + UI 回调 + 命令日志 + 性能监控
 */
public final class Logger {
    private static final String TAG = "C11CarTool";
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public enum Level { STEP, INFO, OK, WARN, FAIL, TITLE, CMD }

    public interface Callback { void onLog(String line, Level level); }
    public interface PerfCallback { void onPerf(String cmd, long durationMs); }

    private static Callback cb;
    private static PerfCallback perfCb;
    private static final List<String> cmdLog = new ArrayList<>();

    public static void setCallback(Callback c) { cb = c; }
    public static void setPerfCallback(PerfCallback c) { perfCb = c; }

    public static void title(String msg) { log(Level.TITLE, msg); }
    public static void step(String msg)  { log(Level.STEP,  msg); }
    public static void info(String msg)  { log(Level.INFO,  msg); }
    public static void ok(String msg)    { log(Level.OK,    msg); }
    public static void warn(String msg)  { log(Level.WARN,  msg); }
    public static void fail(String msg)  { log(Level.FAIL,  msg); }

    public static void cmd(String command, Sh.Result result) {
        String entry = TS.format(new Date()) + " $ " + command
                + "\n  exit=" + result.exit
                + " out=\"" + trunc(result.out, 300) + "\""
                + " err=\"" + trunc(result.err, 100) + "\"";
        synchronized (cmdLog) { cmdLog.add(entry); }
        log(Level.CMD, command + " → " + (result.ok() ? "OK" : "exit=" + result.exit));
    }

    public static void cmd(String command, String output) {
        String entry = TS.format(new Date()) + " $ " + command + "\n  → " + trunc(output, 300);
        synchronized (cmdLog) { cmdLog.add(entry); }
        log(Level.CMD, command + " → " + trunc(output, 100));
    }

    public static void onPerf(String cmd, long durationMs) {
        if (perfCb != null) perfCb.onPerf(cmd, durationMs);
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", "\\n");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public static List<String> getCmdLog() {
        synchronized (cmdLog) { return new ArrayList<>(cmdLog); }
    }

    public static void clearCmdLog() {
        synchronized (cmdLog) { cmdLog.clear(); }
    }

    /**
     * 导出全部日志 (含时间戳)
     */
    public static String exportAll() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("# C11 车控测试 日志\n");
        sb.append("# 导出时间: ").append(sdf.format(new Date())).append("\n\n");
        synchronized (cmdLog) {
            for (String entry : cmdLog) {
                sb.append(entry).append("\n");
            }
        }
        return sb.toString();
    }

    private static void log(Level lv, String msg) {
        String ts = TS.format(new Date());
        String line = "[" + ts + "] " + msg;

        switch (lv) {
            case TITLE: Log.i(TAG, "═══ " + msg); break;
            case STEP:  Log.i(TAG, "  → " + msg); break;
            case OK:    Log.i(TAG, "  ✅ " + msg); break;
            case FAIL:  Log.w(TAG, "  ❌ " + msg); break;
            case WARN:  Log.w(TAG, "  ⚠️ " + msg); break;
            case CMD:   Log.d(TAG, "  $ " + msg); break;
            default:    Log.i(TAG, "  " + msg); break;
        }

        if (cb != null) cb.onLog(line, lv);
    }
}
