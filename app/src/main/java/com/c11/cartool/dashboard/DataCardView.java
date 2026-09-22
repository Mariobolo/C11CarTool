package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 只读数据方块：主值（大字）+ 单位 + 副行 + 来源角标。
 * 无值时主值显示 "--"（不放假数据）。
 */
public class DataCardView extends SpanCardView {

    private TextView mainView;
    private TextView unitView;
    private TextView subView;

    public DataCardView(Context context, String title, int cornerDp) {
        super(context, title, cornerDp);
    }

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout valueRow = new LinearLayout(ctx);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER);
        valueRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        mainView = text(30, DashboardTheme.TEXT, Typeface.BOLD);
        valueRow.addView(mainView);

        unitView = text(12, DashboardTheme.DIM, 0);
        unitView.setPadding(dp(3), dp(6), 0, 0);
        valueRow.addView(unitView);

        col.addView(valueRow);

        subView = text(12, DashboardTheme.DIM, 0);
        subView.setPadding(0, dp(2), 0, 0);
        col.addView(subView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content().addView(col);
    }

    /** 设置主值+单位+副行（null/无效值显示 --）；视图未构建时跳过（等待下一轮刷新） */
    public void setData(String main, String unit, String sub) {
        if (mainView == null || unitView == null) return; // buildContent 尚未执行（视图未挂载）
        boolean has = main != null && !main.isEmpty();
        mainView.setText(has ? main : DashboardTheme.NA);
        unitView.setText(has && unit != null ? unit : "");
        subView.setText(sub == null || sub.isEmpty() ? "" : sub);
        setState(has ? State.NORMAL : State.ERROR);
        setBadge(has ? "" : "等待数据");
    }

    /** 空态：无数据 */
    public void setEmpty() {
        if (mainView == null) return;
        mainView.setText(DashboardTheme.NA);
        unitView.setText("");
        subView.setText("等待数据");
        setState(State.ERROR);
        setBadge("");
    }
}
