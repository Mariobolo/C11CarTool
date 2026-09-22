package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 车门状态卡（3×2）：左前/右前/左后/右后/后备箱/前机盖 6 个状态点。
 * 关=绿（安全）、开=橙（警示）、未知=灰。
 */
public class DoorsCardView extends SpanCardView {

    private static final String[] NAMES = {"左前", "右前", "左后", "右后", "后备箱", "前机盖"};
    private final View[] dots = new View[6];
    private final TextView[] labels = new TextView[6];

    public DoorsCardView(Context context, int cornerDp) {
        super(context, "车门 / 后备箱 / 前机盖", cornerDp);
    }

    @Override
    protected void buildContent() {
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                LinearLayout cell = new LinearLayout(ctx);
                cell.setOrientation(LinearLayout.HORIZONTAL);
                cell.setGravity(Gravity.CENTER);

                dots[idx] = new View(ctx);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
                cell.addView(dots[idx], dlp);

                labels[idx] = text(9, DashboardTheme.DIM, 0);
                labels[idx].setText(NAMES[idx]);
                labels[idx].setPadding(dp(4), 0, 0, 0);
                cell.addView(labels[idx]);

                row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        content().addView(grid);
        setAllUnknown();
    }

    /** 六门状态（0关1开，-1未知）：左前 右前 左后 右后 后备箱 前机盖 */
    public void setDoors(int[] states) {
        if (dots[0] == null) return; // buildContent 尚未执行（视图未挂载）
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            int s = states != null && i < states.length ? states[i] : -1;
            if (s == 0) { dots[i].setBackgroundColor(DashboardTheme.GREEN); any = true; }
            else if (s == 1) { dots[i].setBackgroundColor(DashboardTheme.ORANGE); any = true; }
            else dots[i].setBackgroundColor(DashboardTheme.FAINT);
        }
        setState(any ? State.NORMAL : State.ERROR);
    }

    private void setAllUnknown() {
        for (View d : dots) d.setBackgroundColor(DashboardTheme.FAINT);
        setState(State.ERROR);
    }
}
