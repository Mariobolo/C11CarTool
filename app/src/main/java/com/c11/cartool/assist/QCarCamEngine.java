package com.c11.cartool.assist;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [v0.3.28] QCarCam 原生摄像头引擎（高通 8155 / SA8155P，灵眸 DVR 机制）。
 *
 * <p>零跑 C11 车机摄像头不暴露给 Android Camera2，而是由高通 QCarCam 硬件抽象管理：
 * <ul>
 *   <li>{@code libais_client.so}（assets/qccam/lib）提供 {@code qcarcam_*} API；</li>
 *   <li>{@code cam_enum}（assets/qccam/bin）枚举全部 input 表（desc 可能被零跑 BSP 重映射）；</li>
 *   <li>{@code cam_probe --full} 逐个 input 开流标定视角（front/rear/left/right/cabin）；</li>
 *   <li>{@code xp_capture} 直采单路/多路：{@code --cam N --fps 30 --record file}（UYVY），
 *       {@code --cams 8,9,5,4 / --all（0:Front 1:Right 2:Rear 3:Left）/ --grid2x2 / --grid4k}。</li>
 * </ul>
 *
 * <p>全部工具为 NDK arm64 ELF（Android 28/30），执行需
 * {@code LD_LIBRARY_PATH=<filesDir>/qccam/lib}；App 侧释放到私有目录后 ProcessBuilder 运行，
 * 输出经 redirectErrorStream 归一（stdout+stderr 合并），同步带超时，避免阻塞主线程。
 */
public final class QCarCamEngine {

    private static final String TAG = "QCarCam";

    private QCarCamEngine() {}

    /** 释放 assets/qccam → filesDir/qccam，bin chmod 755；幂等（已存在且大小一致跳过）。 */
    public static synchronized File install(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "qccam");
        File bin = new File(dir, "bin");
        File lib = new File(dir, "lib");
        String[][] groups = {
                {"bin/cam_enum", "bin/cam_enum"},
                {"bin/cam_probe", "bin/cam_probe"},
                {"bin/xp_capture", "bin/xp_capture"},
                {"lib/libais_client.so", "lib/libais_client.so"},
                {"lib/libmmosal_proprietary.so", "lib/libmmosal_proprietary.so"},
                {"lib/libuhab.so", "lib/libuhab.so"},
        };
        try {
            for (String[] g : groups) {
                File out = new File(dir, g[1]);
                if (out.exists() && out.length() == ctx.getAssets().open("qccam/" + g[0]).available()) continue;
                out.getParentFile().mkdirs();
                InputStream is = ctx.getAssets().open("qccam/" + g[0]);
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close(); is.close();
            }
            // bin 可执行
            Runtime.getRuntime().exec(new String[]{"chmod", "755",
                    new File(bin, "cam_enum").getAbsolutePath(),
                    new File(bin, "cam_probe").getAbsolutePath(),
                    new File(bin, "xp_capture").getAbsolutePath()}).waitFor();
            Log.i(TAG, "QCarCam 引擎就绪: " + dir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "QCarCam 引擎释放失败: " + e.getMessage(), e);
        }
        return dir;
    }

    /** 运行工具：bin 相对名（cam_enum/cam_probe/xp_capture）+ 参数，同步超时（毫秒），合并输出。 */
    public static Result run(Context ctx, String tool, long timeoutMs, String... args) {
        File dir = install(ctx);
        File exe = new File(dir, "bin/" + tool);
        if (!exe.exists()) return new Result(false, "", "工具缺失: " + tool);
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.getAbsolutePath());
        cmd.addAll(Arrays.asList(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("LD_LIBRARY_PATH", new File(dir, "lib").getAbsolutePath());
            pb.redirectErrorStream(true);
            long t0 = System.currentTimeMillis();
            Process p = pb.start();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream is = p.getInputStream();
            byte[] buf = new byte[4096];
            while (true) {
                if (p.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)) break;
                while (is.available() > 0) {
                    int n = is.read(buf);
                    if (n < 0) break;
                    bo.write(buf, 0, n);
                }
                if (System.currentTimeMillis() - t0 > timeoutMs) {
                    p.destroy();
                    return new Result(false, bo.toString("UTF-8"),
                            tool + " 超时（>" + (timeoutMs / 1000) + "s），已终止");
                }
            }
            while (is.available() > 0) {
                int n = is.read(buf);
                if (n < 0) break;
                bo.write(buf, 0, n);
            }
            int exit = p.exitValue();
            String out = bo.toString("UTF-8");
            Log.i(TAG, tool + " exit=" + exit + " out=" + out.length() + "B");
            return new Result(exit == 0, out, "");
        } catch (Exception e) {
            Log.e(TAG, tool + " 执行异常: " + e.getMessage(), e);
            return new Result(false, "", "执行异常: " + e.getMessage());
        }
    }

    /** 枚举 input 全表（cam_enum；desc 语义可能被零跑 BSP 重映射）。 */
    public static Result enumInputs(Context ctx) {
        return run(ctx, "cam_enum", 30000);
    }

    /** 逐个 input 开流标定视角（cam_probe --full；耗时，超时 180s）。 */
    public static Result probeAll(Context ctx) {
        return run(ctx, "cam_probe", 180000, "--full");
    }

    /** 单路直采录制到文件（xp_capture --cam N --fps F --time S --record file，UYVY 原始帧）。 */
    public static Result capture(Context ctx, int camId, File out, int seconds, int fps) {
        return run(ctx, "xp_capture", (long) seconds * 1000 + 15000,
                "--cam", String.valueOf(camId), "--fps", String.valueOf(fps),
                "--time", String.valueOf(seconds), "--record", out.getAbsolutePath());
    }

    public static final class Result {
        public final boolean ok;
        public final String out;
        public final String err;
        Result(boolean ok, String out, String err) {
            this.ok = ok; this.out = out == null ? "" : out; this.err = err == null ? "" : err;
        }
        public String detail() {
            return err.isEmpty() ? out : (out.isEmpty() ? err : out + "\n" + err);
        }
    }
}
