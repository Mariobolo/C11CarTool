package com.c11.cartool.dashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 四车窗自定义开度 Dialog：每窗一行 SeekBar(0-100)，松手（onStopTrackingTouch）下发目标开度。
 *
 * <p>与 {@link WindowCardView} 档位卡共用同一 {@link WindowCardView.Listener#onWindowSet}
 * 通道（讯飞 handMessage 四车窗 SET），保证同通道复用、不另造接口。
 */
public final class WindowSliderDialog {

    private WindowSliderDialog() {}

    public static void show(Context ctx, final String[] names, int[] currentPct,
                            final WindowCardView.Listener listener) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 12);
        box.setPadding(pad, pad, pad, pad);

        final TextView[] vals = new TextView[names.length];

        for (int i = 0; i < names.length; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(ctx);
            name.setText(names[i]);
            name.setTextColor(0xFFE5E7EB);
            name.setTextSize(12);
            row.addView(name, new LinearLayout.LayoutParams(
                    dp(ctx, 70), LinearLayout.LayoutParams.WRAP_CONTENT));

            SeekBar bar = new SeekBar(ctx);
            bar.setMax(100);
            int cur = (currentPct != null && i < currentPct.length && currentPct[i] >= 0)
                    ? currentPct[i] : 0;
            bar.setProgress(cur);

            TextView val = new TextView(ctx);
            val.setText(cur + "%");
            val.setTextColor(0xFF22D3EE);
            val.setTextSize(12);
            val.setGravity(Gravity.CENTER);
            vals[i] = val;

            final int idx = i;
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    vals[idx].setText(p + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    if (listener != null) listener.onWindowSet(names[idx], sb.getProgress());
                }
            });

            row.addView(bar, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(val, new LinearLayout.LayoutParams(
                    dp(ctx, 48), LinearLayout.LayoutParams.WRAP_CONTENT));
            box.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        new AlertDialog.Builder(ctx)
                .setTitle("🎚 车窗自定义开度")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
