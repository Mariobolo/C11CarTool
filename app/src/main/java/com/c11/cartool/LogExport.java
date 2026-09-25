package com.c11.cartool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文本导出工具：把任意文本写入 App 私有外部目录（无需存储权限），完成后回调路径。
 * 日志导出（{@link #exportLogs}）与诊断报告导出（{@link #saveText}）共用同一实现。
 */
public final class LogExport {

    public interface Callback { void onDone(String path, boolean ok); }

    private LogExport() {}

    /** 导出 App 运行日志 */
    public static void exportLogs(Context ctx, Callback cb) {
        saveText(ctx, "c11_log", Logger.exportAll(), cb);
    }

    /**
     * 保存任意文本。
     * @param filePrefix 文件名前缀（最终为 prefix_yyyyMMdd_HHmmss.txt）
     */
    public static void saveText(final Context ctx, final String filePrefix,
                                final String content, final Callback cb) {
        Sh.submitAsync(new Runnable() {
            @Override public void run() {
                String path = null;
                boolean ok = false;
                try {
                    File dir = Sh.exportDir();
                    String fn = filePrefix + "_" + new SimpleDateFormat(
                            "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
                    File f = new File(dir, fn);
                    FileOutputStream fos = new FileOutputStream(f);
                    try {
                        fos.write(content.getBytes("UTF-8"));
                    } finally {
                        fos.close();
                    }
                    path = f.getAbsolutePath();
                    ok = true;
                    Logger.ok("已导出: " + path);
                } catch (Exception e) {
                    Logger.error("LogExport", "导出失败: " + e.getMessage(), e);
                }
                final String p = path;
                final boolean o = ok;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { cb.onDone(p, o); }
                });
            }
        });
    }
}
