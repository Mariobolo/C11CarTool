package com.c11.cartool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.InputStream;

/**
 * Shell 命令执行 + 文件读写
 */
public final class Sh {

    public static class Result {
        public final int exit;
        public final String out;
        public final String err;
        public Result(int exit, String out, String err) {
            this.exit = exit; this.out = out; this.err = err;
        }
        public boolean ok() { return exit == 0; }
        public String trim() { return out.trim(); }
        public boolean contains(String s) { return out.contains(s); }
    }

    public static Result run(String cmd) {
        long start = System.currentTimeMillis();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            int exit = p.waitFor();
            Result r = new Result(exit, out, err);
            Logger.onPerf(cmd, System.currentTimeMillis() - start);
            return r;
        } catch (Exception e) {
            return new Result(-1, "", e.getMessage());
        }
    }

    public static Result run(String cmd, long timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) { p.destroyForcibly(); return new Result(-2, out, "timeout"); }
            Result r = new Result(p.exitValue(), out, err);
            Logger.onPerf(cmd, System.currentTimeMillis() - start);
            return r;
        } catch (Exception e) {
            return new Result(-1, "", e.getMessage());
        }
    }

    public static String out(String cmd) { return run(cmd).trim(); }
    public static boolean ok(String cmd) { return run(cmd).ok(); }

    public static int uid() {
        try { return Integer.parseInt(out("id -u").replaceAll("[^0-9]", "")); }
        catch (Exception e) { return -1; }
    }

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
}
