package com.c11.cartool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell 命令执行器（增强版）
 *
 * 改进：
 *   - 所有命令返回完整的 exit code / stdout / stderr
 *   - 支持超时（默认 10 秒）
 *   - 详细的执行日志记录
 *   - 命令执行性能统计
 *
 * 注意：
 *   普通 Android 应用通过 Runtime.exec() 获得的 shell 进程，
 *   其 uid 是应用自己的 uid（如 u0_a123），不是 shell uid(2000)，更不是 root。
 *   因此：
 *     - getprop / settings get：大部分可读，敏感属性可能被屏蔽
 *     - settings put：需要 WRITE_SECURE_SETTINGS，普通应用无此权限
 *     - am broadcast：可执行，但接收方可能有签名权限保护而拒绝
 *     - logcat：Android 4.1+ 限制普通应用只能读取自己进程的日志
 */
public final class Sh {

    private static final String TAG = "Shell";
    private static android.content.Context appContext;

    /** 设置应用上下文（用于 ADB 密钥存储） */
    public static void setContext(android.content.Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    /** 默认超时时间（毫秒） */
    public static final long DEFAULT_TIMEOUT_MS = 10000;

    // ═══ 连接状态监听（UI 实时刷新）═══
    public interface StateListener {
        void onAdbStateChanged(boolean connected, int uid);
    }

    private static final List<StateListener> stateListeners = new ArrayList<>();
    private static final Object listenerLock = new Object();

    /** 注册 ADB 状态监听器（主界面实时刷新连接状态） */
    public static void addStateListener(StateListener l) {
        synchronized (listenerLock) {
            if (!stateListeners.contains(l)) stateListeners.add(l);
        }
    }

    /** 注销状态监听器 */
    public static void removeStateListener(StateListener l) {
        synchronized (listenerLock) {
            stateListeners.remove(l);
        }
    }

    /** 通知所有监听器（在状态变化时调用） */
    private static void notifyStateChanged() {
        synchronized (listenerLock) {
            if (stateListeners.isEmpty()) return;
            final boolean conn = isAdbConnected();
            final int uid = adbUid;
            for (StateListener l : stateListeners) {
                try { l.onAdbStateChanged(conn, uid); } catch (Exception ignored) {}
            }
        }
    }

    // ═══ ADB 连接管理（连接+状态内聚）═══
    private static AdbClient adbClient = null;
    private static final Object adbLock = new Object();
    private static int adbUid = -1;          // -1=未知, 2000=shell, 0=root
    private static String adbHost = "127.0.0.1";
    private static int adbPort = 5555;
    private static long lastConnectTime = 0;

    // ═══ 心跳保活（每 20s 一次，断线自动重连）═══
    private static final long KEEPALIVE_INTERVAL_MS = 20000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static Thread keepAliveThread = null;
    private static volatile boolean keepAliveRunning = false;
    private static volatile long lastAdbStateChange = 0;

    /**
     * 启动 ADB 心跳保活线程（幂等，重复调用无副作用）。
     * 职责：
     *   - 连接存在但断线时自动重连（带退避与次数上限）
     *   - 周期刷新 uid（shell 权限状态保持准确）
     *   - 状态变化通知监听器（UI 实时刷新）
     */
    public static void startKeepAlive() {
        synchronized (adbLock) {
            if (keepAliveRunning) return;
            keepAliveRunning = true;
        }
        keepAliveThread = new Thread(() -> {
            int failStreak = 0;
            while (keepAliveRunning) {
                try { Thread.sleep(KEEPALIVE_INTERVAL_MS); } catch (InterruptedException e) { break; }
                synchronized (adbLock) {
                    if (adbClient == null) { failStreak = 0; continue; }
                    if (!adbClient.isConnected()) {
                        // 断线：尝试自动重连（带次数上限，避免无意义风暴）
                        if (failStreak >= MAX_RECONNECT_ATTEMPTS) {
                            Logger.warn(TAG, "自动重连已达上限(" + MAX_RECONNECT_ATTEMPTS + ")，暂停重试，等待用户手动操作");
                            failStreak = 0;
                            adbClient = null;
                            adbUid = -1;
                            notifyStateChanged();
                            continue;
                        }
                        failStreak++;
                        Logger.warn(TAG, "心跳检测到断线，自动重连(" + failStreak + "/" + MAX_RECONNECT_ATTEMPTS + ")...");
                        try { adbClient.close(); } catch (Exception ignored) {}
                        adbClient = new AdbClient(adbHost, adbPort);
                        if (appContext != null) adbClient.initKeys(appContext);
                        if (adbClient.connect(15000)) {
                            failStreak = 0;
                            lastConnectTime = System.currentTimeMillis();
                            Logger.ok(TAG, "自动重连成功");
                            detectUidAfterConnect();
                            notifyStateChanged();
                        } else {
                            Logger.error(TAG, "自动重连失败");
                        }
                        continue;
                    }
                    // 已连接：周期刷新 uid，保持 shell 状态准确
                    int oldUid = adbUid;
                    Result r = adbClient.shell("id -u", 5000);
                    if (r.exit == 0) {
                        try { adbUid = Integer.parseInt(r.out.trim().replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                    }
                    if (adbUid != oldUid) {
                        Logger.info(TAG, "uid 更新: " + oldUid + " → " + adbUid);
                        notifyStateChanged();
                    }
                }
            }
        }, "AdbKeepAlive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
        Logger.info(TAG, "ADB 心跳保活已启动(间隔 " + KEEPALIVE_INTERVAL_MS / 1000 + "s)");
    }

    /** 停止心跳保活线程 */
    public static void stopKeepAlive() {
        keepAliveRunning = false;
        if (keepAliveThread != null) keepAliveThread.interrupt();
    }

    /** 获取 ADB shell uid（-1=未连接或未知） */
    public static int getAdbUid() { synchronized(adbLock) { return adbUid; } }

    /** 获取权限描述文本 */
    public static String getPermissionLabel() {
        synchronized (adbLock) {
            if (adbClient == null || !adbClient.isConnected()) return "普通应用权限";
            if (adbUid == 2000) return "ADB shell (uid=2000)";
            if (adbUid == 0) return "ROOT (uid=0)";
            return "ADB已连接 (uid检测中...)";
        }
    }

    /** 连接后检测 uid（带重试，预热后执行） */
    private static void detectUidAfterConnect() {
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(i == 0 ? 400 : 300); } catch (Exception ignored) {}
            Result r = adbClient.shell("id -u", 5000);
            String s = r.out != null ? r.out.trim() : "";
            if (r.exit == 0 && !s.isEmpty()) {
                try {
                    adbUid = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                    Logger.ok(TAG, "UID 检测成功: " + adbUid);
                    return;
                } catch (Exception ignored) {}
            }
            Logger.warn(TAG, "UID 检测尝试" + (i+1) + "失败: '" + s + "'");
        }
        adbUid = -1;
        Logger.warn(TAG, "UID 检测3次均失败");
    }

    /**
     * 连接到本地 adbd (127.0.0.1:5555)
     * 连接成功后，所有命令将通过 adb shell 执行，获得 shell uid(2000) 权限
     */
    public static boolean connectAdb(String host, int port, int timeoutMs) {
        synchronized (adbLock) {
            if (adbClient != null && adbClient.isConnected()) {
                Logger.info(TAG, "ADB 已连接，无需重复连接");
                return true;
            }
            adbClient = new AdbClient(host, port);
            if (appContext == null || !adbClient.initKeys(appContext)) {
                Logger.error(TAG, "ADB 密钥初始化失败，终止连接（避免使用临时密钥导致反复弹授权框）");
                adbClient = null;
                adbUid = -1;
                notifyStateChanged();
                return false;
            }
            adbHost = host;
            adbPort = port;
            boolean ok = adbClient.connect(timeoutMs);
            if (ok) {
                lastConnectTime = System.currentTimeMillis();
                Logger.ok(TAG, "ADB 连接成功，预热中...");
                // 在后台检测 uid（不阻塞连接返回）
                new Thread(() -> {
                    synchronized (adbLock) {
                        if (adbClient != null && adbClient.isConnected()) {
                            detectUidAfterConnect();
                            notifyStateChanged();
                        }
                    }
                }, "AdbUidDetect").start();
            } else {
                adbClient = null;
                adbUid = -1;
            }
            notifyStateChanged();
            return ok;
        }
    }

    /** 便捷方法：连接本地 adbd */
    public static boolean connectLocalAdb() {
        return connectAdb("127.0.0.1", 5555, 15000);
    }

    /** 断开 ADB 连接 */
    public static void disconnectAdb() {
        synchronized (adbLock) {
            if (adbClient != null) {
                adbClient.close();
                adbClient = null;
                adbUid = -1;
                Logger.info(TAG, "ADB 已断开，恢复本地 shell 模式");
                notifyStateChanged();
            }
        }
    }

    /** 检查 ADB 是否已连接 */
    public static boolean isAdbConnected() {
        synchronized (adbLock) {
            return adbClient != null && adbClient.isConnected();
        }
    }

    public static class Result {
        public final int exit;
        public final String out;
        public final String err;
        public final long durationMs;
        public final boolean timeout;
        public final String channel;   // "adb"=通过 adb shell(uid2000) | "local"=应用本地 shell

        public Result(int exit, String out, String err, long durationMs, boolean timeout) {
            this(exit, out, err, durationMs, timeout, null);
        }

        public Result(int exit, String out, String err, long durationMs, boolean timeout, String channel) {
            this.exit = exit;
            this.out = out;
            this.err = err;
            this.durationMs = durationMs;
            this.timeout = timeout;
            this.channel = channel;
        }

        public boolean ok() { return exit == 0 && !timeout; }
        public String trim() { return out == null ? "" : out.trim(); }
        public boolean contains(String s) { return out != null && out.contains(s); }

        /** 获取完整的诊断信息（用于日志显示，含通道与执行结果） */
        public String toDiagnosticString() {
            StringBuilder sb = new StringBuilder();
            sb.append("channel=").append(channel == null ? "?" : channel);
            sb.append(", exit=").append(exit);
            sb.append(", duration=").append(durationMs).append("ms");
            if (timeout) sb.append(" [TIMEOUT]");
            if (out != null && !out.isEmpty()) {
                sb.append("\n--- stdout ---\n").append(out);
            }
            if (err != null && !err.isEmpty()) {
                sb.append("\n--- stderr ---\n").append(err);
            }
            return sb.toString();
        }
    }

    /**
     * 执行 shell 命令（默认超时 10 秒）
     */
    public static Result run(String cmd) {
        return run(cmd, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 执行 shell 命令（指定超时）
     *
     * @param cmd 命令字符串
     * @param timeoutMs 超时时间（毫秒），0 表示不超时
     */
    public static Result run(String cmd, long timeoutMs) {
        // 如果 ADB 已连接，通过 adb shell 执行（获得 shell uid=2000 权限）
        if (adbClient != null) {
            synchronized (adbLock) {
                if (adbClient != null && adbClient.isConnected()) {
                    Result r = adbClient.shell(cmd, timeoutMs);
                    // 记录日志（含通道标识，失败不静默）
                    if (!r.ok()) {
                        Logger.warn(TAG, "[adb] 命令失败: " + cmd + "\n" + r.toDiagnosticString());
                    } else {
                        Logger.debug(TAG, "[adb] 命令成功(" + r.durationMs + "ms): " + cmd);
                    }
                    Logger.onPerf(cmd, r.durationMs);
                    return new Result(r.exit, r.out, r.err, r.durationMs, r.timeout, "adb");
                }
                // 连接对象存在但已断开：交给心跳线程自动重连，本次按本地执行并明确标注
                if (adbClient != null) {
                    Logger.warn(TAG, "[adb] 连接已断开，本次命令按本地 shell 执行（心跳线程将自动重连）: " + cmd);
                }
            }
        }

        // 本地 shell 模式（应用 uid）
        long start = System.currentTimeMillis();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});

            // 读取 stdout 和 stderr（必须在独立线程，否则可能死锁）
            StreamReader outReader = new StreamReader(p.getInputStream());
            StreamReader errReader = new StreamReader(p.getErrorStream());
            outReader.start();
            errReader.start();

            // 等待进程结束或超时
            boolean finished;
            if (timeoutMs > 0) {
                finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } else {
                p.waitFor();
                finished = true;
            }

            // 等待输出读取线程完成
            outReader.join(500);
            errReader.join(500);

            long duration = System.currentTimeMillis() - start;

            if (!finished) {
                p.destroyForcibly();
                Logger.warn(TAG, "[local] 命令超时(" + timeoutMs + "ms): " + cmd);
                return new Result(-1, outReader.getResult(), errReader.getResult(), duration, true, "local");
            }

            int exit = p.exitValue();
            Result r = new Result(exit, outReader.getResult(), errReader.getResult(), duration, false, "local");

            // 记录执行日志（失败不静默）
            if (exit != 0) {
                Logger.warn(TAG, "[local] 命令失败: " + cmd + "\n" + r.toDiagnosticString());
            } else {
                Logger.debug(TAG, "[local] 命令成功(" + duration + "ms): " + cmd);
            }

            Logger.onPerf(cmd, duration);
            return r;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            Logger.error(TAG, "[local] 命令执行异常: " + cmd + " -> " + e.getMessage());
            if (p != null) {
                try { p.destroyForcibly(); } catch (Exception ignored) {}
            }
            return new Result(-1, "", e.getMessage(), duration, false, "local");
        }
    }

    /**
     * 执行命令并返回 stdout（trim 后）
     */
    public static String out(String cmd) {
        return run(cmd).trim();
    }

    /**
     * 执行命令并返回是否成功
     */
    public static boolean ok(String cmd) {
        return run(cmd).ok();
    }

    /**
     * 获取当前 uid（优先返回 ADB 缓存的 uid，避免重复执行命令）
     */
    public static int uid() {
        synchronized (adbLock) {
            if (adbClient != null && adbClient.isConnected() && adbUid >= 0) {
                return adbUid;
            }
        }
        // ADB 未连接或 uid 未知，实时查询本地 uid
        try {
            return Integer.parseInt(out("id -u").replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取当前进程完整 id 信息
     */
    public static String whoami() { return out("id"); }

    /**
     * 报告/采集文件导出目录：App 私有外部目录（Android/data/包名/files/exports），
     * 无需存储运行时权限即可直接写，ADB shell 与文件管理器可读。
     */
    public static File exportDir() {
        File base = null;
        try { if (appContext != null) base = appContext.getExternalFilesDir(null); } catch (Exception ignored) {}
        if (base == null) base = new File("/sdcard");
        File ex = new File(base, "exports");
        if (!ex.exists()) ex.mkdirs();
        return ex;
    }

    /**
     * 写文件到设备
     */
    public static boolean writeFile(String path, String content) {
        try {
            FileWriter fw = new FileWriter(new File(path));
            fw.write(content);
            fw.close();
            return true;
        } catch (Exception e) {
            // fallback: 用 shell 写
            Result r = run("echo '" + content.replace("'", "'\\''") + "' > " + path);
            return r.ok();
        }
    }

    /**
     * 从设备读文件
     */
    public static String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return out("cat " + path);
        }
    }

    /**
     * 读取 InputStream 全部内容
     */
    public static String read(InputStream is) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /**
     * 独立线程读取 InputStream，避免死锁
     */
    private static class StreamReader extends Thread {
        private final InputStream is;
        private volatile String result;

        StreamReader(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            result = read(is);
        }

        String getResult() {
            return result == null ? "" : result;
        }
    }
}
