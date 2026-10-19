package com.c11.cartool.dashboard;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.SeekBar;

/**
 * 车机大滑块样式（v0.3.25）。
 *
 * <p>原生 SeekBar 是手机端细窄条，不匹配车机触摸与观感。这里统一替换为：
 * 粗圆角轨道（底 = 半透明白，进度 = 主题蓝）+ 大圆点 thumb（白底蓝描边），
 * 高度 ≥48dp 保证触摸目标，三处滑块（TileView / WindowSliderView /
 * WindowSliderDialog）共用，一处改样全项目生效。
 */
public final class SeekBarStyler {

    private SeekBarStyler() {}

    /** 应用车机化样式；调用处仍需保证外层行高 ≥48dp。 */
    public static void style(SeekBar bar) {
        int trackH = dp(bar, 8);
        int thumbD = dp(bar, 24);

        // 轨道底（半透明白，毛玻璃下自然融入）
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(trackH / 2f);
        bg.setColor(0x2EFFFFFF);
        // 进度层（主题蓝）
        GradientDrawable fg = new GradientDrawable();
        fg.setShape(GradientDrawable.RECTANGLE);
        fg.setCornerRadius(trackH / 2f);
        fg.setColor(DashboardTheme.BLUE);

        // [v0.3.25] ProgressBar.onMeasure 只看 progressDrawable 的 intrinsic 尺寸
        // （不看 View minimumHeight），必须显式给 intrinsic，否则轨道高度按 0 算。
        bg.setSize(trackH, trackH);
        fg.setSize(trackH, trackH);

        // ProgressBar 会对 id=progress 的层按进度比例裁剪，无需手工 ClipDrawable
        LayerDrawable layers = new LayerDrawable(new Drawable[]{bg, fg});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);

        // thumb：白色大圆点 + 蓝色描边
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(thumbD, thumbD);
        thumb.setColor(DashboardTheme.PURE_WHITE);
        thumb.setStroke(dp(bar, 3), DashboardTheme.BLUE);
        bar.setThumb(thumb);
        // 关掉 tint，避免系统色盖住自定义 drawable
        bar.setThumbTintList(null);
        bar.setProgressTintList(null);
        bar.setSplitTrack(false);
        // 触摸目标底线：48dp（View 级最小高度，resolveSize 时作为下限参与）
        if (bar.getMinimumHeight() < dp(bar, 48)) bar.setMinimumHeight(dp(bar, 48));
        bar.setPadding(0, dp(bar, 12), 0, dp(bar, 12));
    }

    private static int dp(View v, int d) {
        return Math.round(d * v.getResources().getDisplayMetrics().density);
    }
}
