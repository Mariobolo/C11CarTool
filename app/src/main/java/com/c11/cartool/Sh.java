package com.c11.cartool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

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

    /** 默认超时时间（毫秒） */
    public static final long DEFAULT_TIMEOUT_MS = 10000;

    // ═══ ADB 连接管理 ═══
    private static AdbClient adbClient = null;
    private static final Object adbLock = new Object();

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
            boolean ok = adbClient.connect(timeoutMs);
            if (ok) {
                Logger.ok(TAG, "ADB 模式已启用，命令将通过 adb shell 执行 (uid=2000)");
            } else {
                adbClient = null;
            }
            return ok;
        }
    }

    /** 便捷方法：连接本地 adbd */
    public static boolean connectLocalAdb() {
        return connectAdb("127.0.0.1", 5555, 5000);
    }

    /** 断开 ADB 连接 */
    public static void disconnectAdb() {
        synchronized (adbLock) {
            if (adbClient != null) {
                adbClient.close();
                adbClient = null;
                Logger.info(TAG, "ADB 已断开，恢复本地 shell 模式");
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

        public Result(int exit, String out, String err, long durationMs, boolean timeout) {
            this.exit = exit;
            this.out = out;
            this.err = err;
            this.durationMs = durationMs;
            this.timeout = timeout;
        }

        public boolean ok() { return exit == 0 && !timeout; }
        public String trim() { return out == null ? "" : out.trim(); }
        public boolean contains(String s) { return out != null && out.contains(s); }

        /** 获取完整的诊断信息（用于日志显示） */
        public String toDiagnosticString() {
            StringBuilder sb = new StringBuilder();
            sb.append("exit=").append(exit);
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
        if (isAdbConnected()) {
            synchronized (adbLock) {
                if (adbClient != null && adbClient.isConnected()) {
                    Result r = adbClient.shell(cmd, timeoutMs);
                    // 记录日志
                    if (!r.ok()) {
                        Logger.warn(TAG, "[ADB] 命令失败(exit=" + r.exit + "): " + cmd + "\n" + r.toDiagnosticString());
                    } else {
                        Logger.debug(TAG, "[ADB] 命令成功(" + r.durationMs + "ms): " + cmd);
                    }
                    Logger.onPerf(cmd, r.durationMs);
                    return r;
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
                Logger.warn(TAG, "命令超时(" + timeoutMs + "ms): " + cmd);
                return new Result(-1, outReader.getResult(), errReader.getResult(), duration, true);
            }

            int exit = p.exitValue();
            Result r = new Result(exit, outReader.getResult(), errReader.getResult(), duration, false);

            // 记录执行日志
            if (exit != 0) {
                Logger.warn(TAG, "命令失败(exit=" + exit + "): " + cmd + "\n" + r.toDiagnosticString());
            } else {
                Logger.debug(TAG, "命令成功(" + duration + "ms): " + cmd);
            }

            Logger.onPerf(cmd, duration);
            return r;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            Logger.error(TAG, "命令执行异常: " + cmd + " -> " + e.getMessage());
            if (p != null) {
                try { p.destroyForcibly(); } catch (Exception ignored) {}
            }
            return new Result(-1, "", e.getMessage(), duration, false);
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
     * 获取当前进程 uid
     */
    public static int uid() {
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
