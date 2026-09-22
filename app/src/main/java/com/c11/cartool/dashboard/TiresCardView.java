package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 胎压胎温四宫格（2×2）：左前/右前/左后/右后，每格显示 压力(kPa) + 温度(℃)。
 * 数据来自 logcat TPMSBean（真机已实测 231/233/231/239 kPa）。
 */
public class TiresCardView extends SpanCardView {

    private final TextView[] pressViews = new TextView[4];
    private final TextView[] tempViews = new TextView[4];

    public TiresCardView(Context context, int cornerDp) {
        super(context, "胎压 / 胎温", cornerDp);
    }

    @Override
    protected void buildContent() {
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        String[] names = {"左前", "右前", "左后", "右后"};
        int[] colors = {DashboardTheme.BLUE, DashboardTheme.CYAN, DashboardTheme.GREEN, DashboardTheme.YELLOW};

        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                LinearLayout cell = new LinearLayout(ctx);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);

                TextView name = text(9, colors[idx], 0);
                name.setText(names[idx]);
                cell.addView(name);

                pressViews[idx] = text(16, DashboardTheme.TEXT, Typeface.BOLD);
                pressViews[idx].setText(DashboardTheme.NA);
                cell.addView(pressViews[idx]);

                tempViews[idx] = text(9, DashboardTheme.DIM, 0);
                tempViews[idx].setText("");
                cell.addView(tempViews[idx]);

                row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        content().addView(grid);
    }

    /** @param pressKpa 4 胎压 kPa，-1 无效；@param tempC 4 胎温 ℃，-1 无效 */
    public void setTires(float[] pressKpa, float[] tempC) {
        if (pressViews[0] == null) return; // buildContent 尚未执行（视图未挂载）
        if (pressKpa != null) {
            for (int i = 0; i < 4 && i < pressKpa.length; i++) {
                if (pressKpa[i] > 0) {
                    pressViews[i].setText(String.valueOf(Math.round(pressKpa[i])));
                } else {
                    pressViews[i].setText(DashboardTheme.NA);
                }
            }
        }
        if (tempC != null) {
            for (int i = 0; i < 4 && i < tempC.length; i++) {
                tempViews[i].setText((tempC[i] != -1 && tempC[i] > -50 && tempC[i] < 120) ? Math.round(tempC[i]) + "°C" : "");
            }
        }
    }

    public void setEmpty() {
        if (pressViews[0] == null) return;
        for (TextView v : pressViews) v.setText(DashboardTheme.NA);
        for (TextView v : tempViews) v.setText("");
        setState(State.ERROR);
    }
}
