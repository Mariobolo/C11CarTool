package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 四车窗档位卡：每车窗一列，四个动作档位（微开 10% / 半开 50% / 全开 100% / 全关 0%）。
 *
 * <p>点击即下发目标开度（讯飞 handMessage 四车窗 SET，真机标定有效），不使用 toggle，
 * 从根本上避免"开关方向搞反"；真实开度（eventId 21180-21183）回写后自动高亮最近档位。
 */
public class WindowCardView extends SpanCardView {

    public interface Listener {
        /** @param voiceName 讯飞 handMessage 车窗名；@param percent 目标开度 0-100 */
        void onWindowSet(String voiceName, int percent);
    }

    /** 车窗展示顺序与 Snapshot.windowPct 一致：左前 右前 左后 右后 */
    private static final String[] TITLES = {"主驾车窗", "副驾车窗", "左后车窗", "右后车窗"};
    private static final int[] LEVELS = {10, 50, 100, 0};
    private static final String[] LEVEL_LABELS = {"微开", "半开", "全开", "全关"};
    /** 真实开度与档位差值 ≤ 此值才高亮（避免误导） */
    private static final int HIGHLIGHT_TOLERANCE = 15;

    private final boolean compact;
    private Listener listener;
    /** 每车窗 4 个档位按钮：[windowIndex][levelIndex] */
    private final TextView[][] levelBtns;

    public WindowCardView(Context context, boolean compact) {
        super(context, "车窗（handMessage）", 12);
        this.compact = compact;
        this.levelBtns = new TextView[TITLES.length][LEVELS.length];
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void buildContent() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int w = 0; w < TITLES.length; w++) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (w > 0) cp.leftMargin = dp(4);
            row.addView(col, cp);

            TextView name = text(compact ? 10 : 11, DashboardTheme.DIM, 0);
            name.setText(TITLES[w]);
            col.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout grid = new LinearLayout(ctx);
            grid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            gp.topMargin = dp(3);
            col.addView(grid, gp);

            for (int pair = 0; pair < 2; pair++) {
                LinearLayout btnRow = new LinearLayout(ctx);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                grid.addView(btnRow, rp);
                for (int half = 0; half < 2; half++) {
                    final int li = pair * 2 + half;
                    final int wi = w;
                    TextView btn = makeBtn(LEVEL_LABELS[li]);
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    bp.rightMargin = half == 0 ? dp(3) : 0;
                    btnRow.addView(btn, bp);
                    btn.setOnClickListener(v -> {
                        if (listener != null) listener.onWindowSet(TITLES[wi], LEVELS[li]);
                    });
                    levelBtns[w][li] = btn;
                }
            }
        }
        content().addView(row);
    }

    private TextView makeBtn(String label) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(DashboardTheme.DIM);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 9 : 10);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(6));
        d.setColor(DashboardTheme.CARD);
        tv.setBackground(d);
        return tv;
    }

    /**
     * 回写真实开度（顺序 左前 右前 左后 右后，-1=未知），高亮最近档位；
     * 视图未构建时跳过（等待下一轮刷新）。
     */
    public void setPositions(int[] pct) {
        if (levelBtns[0][0] == null) return; // buildContent 尚未执行（视图未挂载）
        for (int w = 0; w < TITLES.length; w++) {
            int p = (pct != null && w < pct.length) ? pct[w] : -1;
            int nearest = -1;
            if (p >= 0) {
                int bestDiff = Integer.MAX_VALUE;
                for (int li = 0; li < LEVELS.length; li++) {
                    int diff = Math.abs(LEVELS[li] - p);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        nearest = li;
                    }
                }
                if (bestDiff > HIGHLIGHT_TOLERANCE) nearest = -1;
            }
            for (int li = 0; li < LEVELS.length; li++) {
                styleBtn(levelBtns[w][li], li == nearest);
            }
        }
    }

    private void styleBtn(TextView btn, boolean active) {
        GradientDrawable d = (GradientDrawable) btn.getBackground();
        d.setColor(active ? DashboardTheme.CARD_ACT : DashboardTheme.CARD);
        btn.setTextColor(active ? DashboardTheme.CYAN : DashboardTheme.DIM);
        btn.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }
}
