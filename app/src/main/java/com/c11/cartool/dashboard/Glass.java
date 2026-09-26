package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/**
 * 毛玻璃（Glassmorphism）视觉统一入口。
 *
 * <p>Android 9 无实时背景模糊 API（RenderEffect / Window blur 均为 API 31+），
 * 故采用通行的"假玻璃"做法：彩色渐变光晕壁纸（{@link #wallpaper}，本身平滑无需模糊）
 * + 半透明卡片填充 + 顶部高光 + 1px 半透明描边 + 大圆角，叠出流行玻璃块观感。
 *
 * <p>所有卡片背景统一从这里取，保证风格一致、便于以后替换为真模糊。
 */
public final class Glass {

    private Glass() {}

    public static final int NORMAL = 0;
    public static final int BLUE   = 1;   // 激活 / 选中
    public static final int RED    = 2;   // 失败 / 告警

    /** 玻璃卡片背景。@param cornerDp 圆角；@param tone 色调 */
    public static Drawable bg(Context ctx, float cornerDp, int tone) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int corner = Math.round(cornerDp * d);

        int fill;   // 半透明填充
        int stroke; // 半透明描边
        switch (tone) {
            case BLUE:
                fill   = 0x2E3B82F6;
                stroke = 0x663B82F6;
                break;
            case RED:
                fill   = 0x29EF4444;
                stroke = 0x5CEF4444;
                break;
            default:
                fill   = 0x18FFFFFF;
                stroke = 0x33FFFFFF;
        }

        // 底层：圆角 + 半透明填充 + 细描边
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(corner);
        base.setColor(fill);
        base.setStroke(Math.max(1, Math.round(d)), stroke);

        // 顶层：顶部高光（自上而下白色衰减到透明；下半透明，底部直角不可见）
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x28FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadii(new float[]{
                corner, corner, corner, corner, 0, 0, 0, 0});

        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, sheen});
        return layers;
    }

    /**
     * 主背景：深色渐变底 + 蓝 / 紫 / 青三处彩色光晕（模拟被模糊的彩色壁纸）。
     */
    public static Drawable wallpaper(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;

        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0C1222, 0xFF070A13});

        // 光晕：椭圆 + 径向渐变，靠 inset 定位到角落
        Drawable glowBlue = glow(0xFF2E63F0, Math.round(760 * d));
        Drawable glowPurple = glow(0xFF7A3CF0, Math.round(680 * d));
        Drawable glowCyan = glow(0xFF12A6C8, Math.round(620 * d));

        LayerDrawable layers = new LayerDrawable(
                new Drawable[]{base, glowBlue, glowPurple, glowCyan});
        // 蓝光偏左上
        layers.setLayerInset(1, Math.round(-180 * d), Math.round(-260 * d),
                Math.round(520 * d), Math.round(300 * d));
        // 紫光偏右下
        layers.setLayerInset(2, Math.round(560 * d), Math.round(300 * d),
                Math.round(-260 * d), Math.round(-220 * d));
        // 青光偏中下
        layers.setLayerInset(3, Math.round(420 * d), Math.round(120 * d),
                Math.round(120 * d), Math.round(-160 * d));
        return layers;
    }

    /** 单个径向彩色光晕（椭圆，中心彩色 → 边缘透明）。 */
    private static GradientDrawable glow(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(radiusPx);
        g.setGradientCenter(0.5f, 0.5f);
        // 中心约 28% 不透明 → 边缘全透明
        g.setColors(new int[]{(color & 0x00FFFFFF) | 0x4D000000, color & 0x00FFFFFF});
        return g;
    }
}
