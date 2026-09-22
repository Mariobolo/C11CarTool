package com.c11.cartool.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.c11.cartool.Logger;

/**
 * 二维码位图渲染（白底黑码），基于内嵌的 Nayuki QrCode，纯 Java 无第三方依赖。
 * 统一供仪表盘 Web 弹窗、工程模式 Web 页复用，避免各处重复绘制代码。
 */
public final class QrBitmap {

    private QrBitmap() {}

    /**
     * 把文本渲染为正方形二维码位图。
     *
     * @param text   二维码内容（不可为 null）
     * @param cellPx 每个码模块的像素边长（越大越清晰）
     * @return ARGB_8888 位图；内容过长或失败时返回 null
     */
    public static Bitmap toBitmap(String text, int cellPx) {
        try {
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
            int n = qr.size;
            Bitmap bmp = Bitmap.createBitmap(n * cellPx, n * cellPx, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            cv.drawColor(0xFFFFFFFF);
            Paint paint = new Paint();
            paint.setColor(0xFF000000);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (qr.getModule(x, y)) {
                        cv.drawRect(x * cellPx, y * cellPx,
                                (x + 1) * cellPx, (y + 1) * cellPx, paint);
                    }
                }
            }
            return bmp;
        } catch (Exception e) {
            Logger.warn("QrBitmap", "二维码生成失败: " + e.getMessage());
            return null;
        }
    }
}
