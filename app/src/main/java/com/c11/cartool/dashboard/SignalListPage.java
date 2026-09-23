package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 全车信号清单页（仪表盘第二页，状态条「📊 信号」切换进入）。
 *
 * <p>按分组渲染：每组一个标题条，下面逐行列出「中文名 | 值 | 渠道」。
 * 多渠道同名数据各占一行，全部可见。内容可纵向滚动，适配 720 / 1080 两档屏。
 */
public class SignalListPage extends ScrollView {

    private final LinearLayout content;

    public SignalListPage(Context ctx) {
        super(ctx);
        setBackgroundColor(DashboardTheme.BG);
        setFillViewport(true);

        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        content.setPadding(pad, dp(8), pad, dp(10));
        addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
    }

    /** 用最新一轮快照的信号行重建整页（主线程调用）。 */
    public void setRows(List<SignalRow> rows) {
        content.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            content.addView(headline("暂无信号（ADB 未连接或本轮未采集到数据）"));
            return;
        }
        String lastGroup = "";
        for (SignalRow r : rows) {
            if (!r.group.equals(lastGroup)) {
                content.addView(groupBar(r.group));
                lastGroup = r.group;
            }
            content.addView(row(r));
        }
    }

    private TextView groupBar(String title) {
        TextView t = new TextView(getContext());
        t.setText("  " + title);
        t.setTextColor(DashboardTheme.CYAN);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setBackgroundColor(DashboardTheme.SURFACE);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout row(SignalRow r) {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(8), dp(5), dp(8), dp(5));

        TextView name = cell(r.name, DashboardTheme.TEXT, 14, Gravity.START, 2.0f);
        TextView value = cell(r.value, DashboardTheme.GREEN, 14, Gravity.END, 1.3f);
        TextView channel = cell(r.channel, DashboardTheme.DIM, 11, Gravity.END, 1.6f);
        line.addView(name);
        line.addView(value);
        line.addView(channel);
        return line;
    }

    private TextView cell(String text, int color, int sp, int gravity, float weight) {
        TextView t = new TextView(getContext());
        t.setText(text != null ? text : "");
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setSingleLine(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView headline(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(DashboardTheme.DIM);
        t.setTextSize(14);
        t.setPadding(dp(8), dp(20), dp(8), dp(8));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
