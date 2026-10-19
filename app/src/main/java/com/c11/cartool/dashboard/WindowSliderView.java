package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 四车窗内嵌开度滑块卡（v0.3.12）：主界面直显 4 行「窗名 + SeekBar + 百分比」，
 * 拖动即时显示、松手（onStopTrackingTouch）下发目标开度，不再弹 Dialog。
 *
 * <p>与 {@link WindowCardView} / {@link WindowSliderDialog} 共用同一
 * {@link Listener#onWindowSet} 通道（讯飞 handMessage 四车窗 SET）。
 * 真实开度（eventId 21180-21183）经 {@link #setPosition} 回写滑块。
 */
public final class WindowSliderView extends LinearLayout {

    /** 与 WindowCardView.Listener 相同的下发通道 */
    public interface Listener {
        /** @param voiceName 讯飞 handMessage 车窗名；@param percent 目标开度 0-100 */
        void onWindowSet(String voiceName, int percent);
    }

    private final SeekBar[] bars = new SeekBar[4];
    private final TextView[] vals = new TextView[4];
    private Listener listener;

    public WindowSliderView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(6), dp(10), dp(6));

        String[] names = WindowCardView.voiceNames();   // 主驾/副驾/左后/右后
        for (int i = 0; i < names.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(context);
            name.setText(names[i]);
            name.setTextColor(DashboardTheme.TEXT);
            name.setTextSize(GridDimens.SP_LABEL);
            row.addView(name, new LinearLayout.LayoutParams(
                    dp(64), LayoutParams.WRAP_CONTENT));

            SeekBar bar = new SeekBar(context);
            bar.setMax(100);
            bar.setProgress(0);
            SeekBarStyler.style(bar);

            TextView val = new TextView(context);
            val.setText("--");
            val.setTextColor(DashboardTheme.CYAN);
            val.setTextSize(GridDimens.SP_STEP_VAL);
            val.setGravity(Gravity.CENTER);
            bars[i] = bar;
            vals[i] = val;

            final int idx = i;
            final String voiceName = names[i];
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser) vals[idx].setText(p + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    vals[idx].setText(sb.getProgress() + "%");
                    if (listener != null) listener.onWindowSet(voiceName, sb.getProgress());
                }
            });

            row.addView(bar, new LinearLayout.LayoutParams(0,
                    LayoutParams.WRAP_CONTENT, 1f));
            row.addView(val, new LinearLayout.LayoutParams(
                    dp(52), LayoutParams.WRAP_CONTENT));
            // [v0.3.20] 行高 44→64dp：SeekBar 触摸目标达标（车窗开度调节）
            addView(row, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(64)));
        }
    }

    public void setListener(Listener l) { this.listener = l; }

    /** 真实开度回写（Snapshot.windowPct 顺序：左前 右前 左后 右后）；-1 不清零 */
    public void setPosition(int i, int pct) {
        if (i < 0 || i >= bars.length || pct < 0) return;
        bars[i].setProgress(pct);
        vals[i].setText(pct + "%");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
