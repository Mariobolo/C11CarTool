package com.c11.cartool;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日志系统（增强版）
 *
 * 功能：
 *   - 日志分级：DEBUG / INFO / OK / WARN / ERROR / STEP / TITLE / CMD
 *   - 时间戳 + 标签
 *   - UI 回调（实时更新日志视图）
 *   - 命令执行日志（含 exit code / stdout / stderr）
 *   - 性能监控
 *   - 日志上限（防止内存溢出，默认 1000 条）
 *   - 导出全部日志
 */
public final class Logger {
    private static final String TAG = "C11CarTool";
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    /** 日志上限（超过自动清空旧的） */
    private static final int MAX_LOG_ENTRIES = 1000;

    public enum Level { DEBUG, INFO, OK, WARN, ERROR, STEP, TITLE, CMD }

    public interface Callback { void onLog(String line, Level level); }
    public interface PerfCallback { void onPerf(String cmd, long durationMs); }

    private static Callback cb;
    private static PerfCallback perfCb;
    private static final List<String> cmdLog = new ArrayList<>();

    public static void setCallback(Callback c) { cb = c; }
    public static void setPerfCallback(PerfCallback c) { perfCb = c; }

    // ═══ 带标签的日志方法（用于模块区分） ═══

    public static void debug(String tag, String msg) { log(Level.DEBUG, tag, msg); }
    public static void info(String tag, String msg)  { log(Level.INFO, tag, msg); }
    public static void ok(String tag, String msg)    { log(Level.OK, tag, msg); }
    public static void warn(String tag, String msg)  { log(Level.WARN, tag, msg); }
    public static void error(String tag, String msg) { log(Level.ERROR, tag, msg); }
    public static void error(String tag, String msg, Throwable t) { log(Level.ERROR, tag, msg + "\n" + Log.getStackTraceString(t)); }
    public static void warn(String tag, String msg, Throwable t) { log(Level.WARN, tag, msg + "\n" + Log.getStackTraceString(t)); }

    // ═══ 不带标签的日志方法（兼容旧代码） ═══

    public static void title(String msg) { log(Level.TITLE, null, msg); }
    public static void step(String msg)  { log(Level.STEP, null, msg); }
    public static void info(String msg)  { log(Level.INFO, null, msg); }
    public static void ok(String msg)    { log(Level.OK, null, msg); }
    public static void warn(String msg)  { log(Level.WARN, null, msg); }
    public static void fail(String msg)  { log(Level.ERROR, null, msg); }
    public static void error(String msg) { log(Level.ERROR, null, msg); }
    public static void debug(String msg) { log(Level.DEBUG, null, msg); }

    // ═══ 命令执行日志 ═══

    public static void cmd(String command, Sh.Result result) {
        String entry = TS.format(new Date()) + " $ " + command
                + "\n  exit=" + result.exit
                + " duration=" + result.durationMs + "ms"
                + (result.timeout ? " [TIMEOUT]" : "")
                + "\n  out=\"" + trunc(result.out, 500) + "\""
                + "\n  err=\"" + trunc(result.err, 200) + "\"";
        addCmdLog(entry);
        log(Level.CMD, null, command + " → " + (result.ok() ? "OK" : "exit=" + result.exit));
    }

    public static void cmd(String command, String output) {
        String entry = TS.format(new Date()) + " $ " + command + "\n  → " + trunc(output, 500);
        addCmdLog(entry);
        log(Level.CMD, null, command + " → " + trunc(output, 100));
    }

    public static void onPerf(String cmd, long durationMs) {
        if (perfCb != null) perfCb.onPerf(cmd, durationMs);
    }

    // ═══ 日志管理 ═══

    private static void addCmdLog(String entry) {
        synchronized (cmdLog) {
            cmdLog.add(entry);
            // 超过上限时清空最旧的一半
            if (cmdLog.size() > MAX_LOG_ENTRIES) {
                int removeCount = MAX_LOG_ENTRIES / 2;
                for (int i = 0; i < removeCount; i++) {
                    cmdLog.remove(0);
                }
            }
        }
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
     * 导出全部日志（含时间戳、设备信息）
     */
    public static String exportAll() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sb.append("# C11 车控测试 日志导出\n");
        sb.append("# 导出时间: ").append(sdf.format(new Date())).append("\n");
        sb.append("# APP 版本: ").append(AppInfo.VERSION).append("\n");
        sb.append("# 日志条数: ").append(cmdLog.size()).append("\n\n");
        synchronized (cmdLog) {
            for (String entry : cmdLog) {
                sb.append(entry).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取最近 N 条日志
     */
    public static String getRecentLogs(int count) {
        StringBuilder sb = new StringBuilder();
        synchronized (cmdLog) {
            int start = Math.max(0, cmdLog.size() - count);
            for (int i = start; i < cmdLog.size(); i++) {
                sb.append(cmdLog.get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    // ═══ 核心日志方法 ═══

    private static void log(Level lv, String tag, String msg) {
        String ts = TS.format(new Date());
        String tagStr = (tag != null && !tag.isEmpty()) ? " [" + tag + "]" : "";
        String line = "[" + ts + "]" + tagStr + " " + msg;

        // 输出到 logcat（普通应用只能看到自己的日志，但这是标准做法）
        switch (lv) {
            case TITLE: Log.i(TAG, "═══ " + msg); break;
            case STEP:  Log.i(TAG, "  → " + msg); break;
            case OK:    Log.i(TAG, "  ✅ " + msg); break;
            case ERROR: Log.e(TAG, "  ❌ " + msg); break;
            case WARN:  Log.w(TAG, "  ⚠️ " + msg); break;
            case CMD:   Log.d(TAG, "  $ " + msg); break;
            case DEBUG: Log.d(TAG, "  · " + msg); break;
            default:    Log.i(TAG, "  " + msg); break;
        }

        // UI 回调
        if (cb != null) {
            try {
                cb.onLog(line, lv);
            } catch (Exception e) {
                Log.e(TAG, "Logger callback error: " + e.getMessage());
            }
        }
    }
}
