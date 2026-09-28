package com.c11.cartool;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志存储中心：logcat 日志 + 软件日志 双体系，始终落盘。
 *
 * <p>目录：&lt;下载目录&gt;/&lt;软件名&gt;/（如 /sdcard/Download/C11 车控/）
 * <ul>
 *   <li>logcat 日志：{@code <日期戳>_logcat}（如 20260927_logcat），同名文件直接末尾续写</li>
 *   <li>软件日志　：{@code <日期戳>_<软件名>_log}（如 20260927_C11 车控_log），同名续写</li>
 * </ul>
 * 目录总量上限 700MB，超过时删除最老的日志文件（不删当天正在写的两个文件）。
 *
 * <p>logcat 抓取策略：后台线程定时（30s）全量 dump + 末行扫描，只追加新增行；
 * 断点由日志文件末行恢复（App 重启不丢不断不重，不依赖 logcat -T 的版本兼容性）；
 * 优先走 ADB 通道（shell uid 可读全量系统日志），未连接时退化为本地通道（仅本应用）。
 */
public final class LogStore {

    /** 日志目录容量上限：700MB */
    public static final long MAX_DIR_BYTES = 700L * 1024 * 1024;

    /** logcat 增量抓取间隔 */
    private static final long CAPTURE_INTERVAL_MS = 30_000L;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile Context appContext;

    // ── 软件日志写入器 ──
    private static final Object appLock = new Object();
    private static FileWriter appWriter;
    private static String appWriterDate;
    private static int appFailCount = 0;

    // ── logcat 写入器 ──
    private static final Object catLock = new Object();
    private static FileWriter catWriter;
    private static String catWriterDate;

    private LogStore() {}

    // ══════════════════ 初始化 ══════════════════

    /** 初始化：建目录、恢复 logcat 断点、启动持续抓取（幂等，可在任意入口调用） */
    public static void init(Context ctx) {
        if (ctx != null) appContext = ctx.getApplicationContext();
        if (!started.compareAndSet(false, true)) return;
        Thread t = new Thread(LogStore::captureLoop, "logcat-capture");
        t.setDaemon(true);
        t.start();
        appendAppLog("======== 日志系统启动 " + fullNow() + " ========");
    }

    /** 日志目录：&lt;下载目录&gt;/&lt;软件名&gt;/（不存在则创建） */
    public static File logDir() {
        File base = null;
        try {
            base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } catch (Exception ignored) {}
        if (base == null) base = new File("/sdcard/Download");
        File dir = new File(base, AppInfo.APP_NAME);
        if (!dir.exists()) dir.mkdirs();
        if (!dir.isDirectory()) {   // 兜底：外部存储不可用时退回应用私有目录
            File fb = null;
            Context c = appContext;
            if (c != null) {
                try { fb = c.getExternalFilesDir(null); } catch (Exception ignored) {}
            }
            if (fb == null) fb = new File("/sdcard");
            dir = new File(fb, "Download_" + AppInfo.APP_NAME);
            if (!dir.exists()) dir.mkdirs();
        }
        return dir;
    }

    // ══════════════════ 软件日志（<日期戳>_<软件名>_log，同名续写） ══════════════════

    /** 追加一行软件日志（线程安全、写穿即刷；失败不影响主流程） */
    public static void appendAppLog(String line) {
        try {
            synchronized (appLock) {
                String d = today();
                if (appWriter == null || !d.equals(appWriterDate)) {
                    closeQuietly(appWriter);
                    appWriter = new FileWriter(new File(logDir(), d + "_" + AppInfo.APP_NAME + "_log"), true);
                    appWriterDate = d;
                    appFailCount = 0;
                    pruneIfNeeded();
                }
                appWriter.write(line + "\n");
                appWriter.flush();
            }
        } catch (Exception e) {
            if (++appFailCount > 10) {  // 持续失败则本会话停止重试，避免开销放大
                synchronized (appLock) { closeQuietly(appWriter); appWriter = null; appWriterDate = null; }
            }
        }
    }

    // ══════════════════ logcat 日志（<日期戳>_logcat，同名续写） ══════════════════

    /**
     * 持续抓取循环：每次全量 dump + 末行扫描，只追加新增行（同名续写）。
     * 不依赖 logcat -T 的版本兼容性；断点由日志文件末行恢复，App 重启不断不重。
     */
    private static void captureLoop() {
        String[] state = recoverState();   // [lastTs, lastLine] 断点恢复
        String lastLine = state[1];
        String channelNote = null;
        while (started.get()) {
            try {
                Sh.Result r = Sh.run("logcat -d -v threadtime", 30_000);
                String out = r.out;
                if (out != null && !out.isEmpty()) {
                    String[] lines = out.split("\n");
                    int start = 0;
                    if (!lastLine.isEmpty()) {
                        // 定位上次末行，只写其后；找不到（缓冲已轮转）则全部追加（宁重不丢）
                        for (int i = 0; i < lines.length; i++) {
                            if (lastLine.equals(lines[i])) { start = i + 1; break; }
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < lines.length; i++) {
                        String ln = lines[i];
                        if (ln.isEmpty()) continue;
                        sb.append(ln).append('\n');
                        lastLine = ln;
                    }
                    if (sb.length() > 0) {
                        if (channelNote == null) {
                            channelNote = r.channel == null ? "?" : r.channel;
                            sb.insert(0, "==== logcat 落盘开始 " + fullNow()
                                    + "（通道: " + channelNote
                                    + ("adb".equals(channelNote) ? "，系统全量" : "，仅本应用") + "）====\n");
                        }
                        appendLogcat(sb.toString());
                    }
                }
            } catch (Throwable ignored) {
                // 抓取失败不打扰主流程，下个周期重试
            }
            try { Thread.sleep(CAPTURE_INTERVAL_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            pruneThrottled();
        }
    }

    private static void appendLogcat(String text) {
        try {
            synchronized (catLock) {
                String d = today();
                if (catWriter == null || !d.equals(catWriterDate)) {
                    closeQuietly(catWriter);
                    catWriter = new FileWriter(new File(logDir(), d + "_logcat"), true);
                    catWriterDate = d;
                }
                catWriter.write(text);
                catWriter.flush();
            }
        } catch (Exception ignored) {}
    }

    /**
     * 从现有日志文件末行恢复 logcat 断点（末行内容），避免重启后重复/丢失。
     * 返回 [已弃用的时间戳, 末行内容]。
     */
    private static String[] recoverState() {
        String[] res = new String[]{"", ""};
        try {
            File dir = logDir();
            File[] cats = dir.listFiles((d, name) -> name.endsWith("_logcat"));
            if (cats == null || cats.length == 0) return res;
            Arrays.sort(cats);              // 文件名以日期开头，字典序=时间序
            File newest = cats[cats.length - 1];
            String last = null;
            try (BufferedReader br = new BufferedReader(new FileReader(newest))) {
                String line;
                while ((line = br.readLine()) != null) if (!line.isEmpty()) last = line;
            }
            if (last != null) res[1] = last;
        } catch (Exception ignored) {}
        return res;
    }

    // ══════════════════ 容量控制（≤700MB，超限删最老） ══════════════════

    private static long lastPruneMs = 0;

    /** 至多每 5 分钟检查一次容量 */
    private static void pruneThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastPruneMs < 5 * 60_000L) return;
        pruneIfNeeded();
    }

    /** 目录超过 700MB 时按最老优先删除，直到回到上限内 */
    static void pruneIfNeeded() {
        lastPruneMs = System.currentTimeMillis();
        try {
            File dir = logDir();
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) return;
            long total = 0;
            for (File f : files) if (f.isFile()) total += f.length();
            if (total <= MAX_DIR_BYTES) return;
            File[] sorted = files.clone();
            Arrays.sort(sorted, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            String protect1 = today() + "_" + AppInfo.APP_NAME + "_log";
            String protect2 = today() + "_logcat";
            for (File f : sorted) {
                if (total <= MAX_DIR_BYTES) break;
                if (!f.isFile()) continue;
                String n = f.getName();
                if (n.equals(protect1) || n.equals(protect2)) continue; // 不删当天正在写的
                long len = f.length();
                if (f.delete()) total -= len;
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════ 工具 ══════════════════

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static String fullNow() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void closeQuietly(FileWriter w) {
        if (w != null) { try { w.close(); } catch (Exception ignored) {} }
    }
}
