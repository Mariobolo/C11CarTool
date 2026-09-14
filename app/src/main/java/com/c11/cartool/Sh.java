package com.c11.cartool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;

/**
 * Shell 命令执行工具
 */
public final class Sh {

    public static class Result {
        public final int exit;
        public final String out;
        public final String err;

        public Result(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }

        public boolean ok() { return exit == 0; }
        public String trim() { return out.trim(); }
        public boolean contains(String s) { return out.contains(s); }
    }

    public static Result run(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            int exit = p.waitFor();
            return new Result(exit, out, err);
        } catch (Exception e) {
            return new Result(-1, "", e.getMessage());
        }
    }

    public static Result run(String cmd, long timeoutMs) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(-2, out, "timeout");
            }
            return new Result(p.exitValue(), out, err);
        } catch (Exception e) {
            return new Result(-1, "", e.getMessage());
        }
    }

    public static String out(String cmd) {
        return run(cmd).trim();
    }

    public static boolean ok(String cmd) {
        return run(cmd).ok();
    }

    public static int uid() {
        try {
            String id = out("id -u");
            return Integer.parseInt(id.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    public static String whoami() {
        return out("id");
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
        } catch (Exception e) {
            return "";
        }
    }
}
