package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 必应每日壁纸：后台线程下载 → 缩放到屏幕尺寸 → RenderScript 一次性高斯模糊 → 本地缓存。
 *
 * <p>Android 9 使用 {@link ScriptIntrinsicBlur}（API 17+），不使用 AGSL / RenderEffect，
 * 也不做实时模糊。任何环节失败都回调 {@code null}，由 UI 层降级为纯色背景。
 */
public final class BingWallpaper {

    private static final String TAG = "BingWallpaper";
    private static final String ARCHIVE =
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1";
    private static final String CACHE_NAME = "bing_blur.jpg";

    /** @param blurred 模糊后的壁纸；null 表示失败，应降级纯色背景。 */
    public interface Callback {
        void onResult(Bitmap blurred);
    }

    public static void loadAsync(final Context ctx, final Callback cb) {
        new Thread(() -> {
            Bitmap result = null;
            try {
                File cache = new File(ctx.getFilesDir(), CACHE_NAME);
                result = decode(cache);          // 先用缓存兜底（可能是前一天的）
                Bitmap fresh = fetchAndBlur(ctx);
                if (fresh != null) {
                    save(fresh, cache);
                    result = fresh;
                }
            } catch (Exception e) {
                Log.w(TAG, "必应壁纸加载失败: " + e.getMessage());
            }
            final Bitmap r = result;
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(r));
        }).start();
    }

    private BingWallpaper() {}

    // ── 下载 → 缩放 → 模糊 ──

    private static Bitmap fetchAndBlur(Context ctx) throws Exception {
        byte[] json = httpGet(ARCHIVE);
        JSONObject root = new JSONObject(new String(json, "UTF-8"));
        String rel = root.getJSONArray("images").getJSONObject(0).getString("url");
        String full = rel.startsWith("http") ? rel : "https://cn.bing.com" + rel;

        Bitmap src = downsample(httpGet(full), 1920, 1080);
        return src == null ? null : blur(ctx, src, 20f);
    }

    private static Bitmap downsample(byte[] bytes, int targetW, int targetH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

        int sample = 1;
        while (bounds.outWidth / sample > targetW * 2
                && bounds.outHeight / sample > targetH * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
        if (bmp == null) return null;
        return Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
    }

    private static Bitmap blur(Context ctx, Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        RenderScript rs = RenderScript.create(ctx);
        try {
            ScriptIntrinsicBlur script =
                    ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation in = Allocation.createFromBitmap(rs, src);
            Allocation aOut = Allocation.createFromBitmap(rs, out);
            script.setRadius(radius);
            script.setInput(in);
            script.forEach(aOut);
            aOut.copyTo(out);
        } finally {
            rs.destroy();
        }
        return out;
    }

    private static byte[] httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 9) C11CarTool");
        try {
            InputStream is = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static Bitmap decode(File f) {
        if (f == null || !f.exists()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    private static void save(Bitmap bmp, File f) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } catch (Exception ignored) {}
    }
}
