package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 只读数据方块：主值（大字）+ 单位 + 副行 + 状态角标。
 *
 * <p>三态：
 * <ul>
 *   <li>LOADING 加载中（从未获取，卡片构建后的默认初始态）</li>
 *   <li>NORMAL 已获取（主值正常显示）</li>
 *   <li>ERROR 获取失败（主值 "--"，红态明确标注，不静默）</li>
 * </ul>
 * 后续轮次获取成功会自动恢复 NORMAL；数据渠道由基类 {@link #setChannel} 在标题括号标注。
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

        // 初始态：加载中（等待第一轮采集）
        setLoading();
    }

    /** 设置主值+单位+副行；null/无效值按"获取失败"处理；视图未构建时跳过（等待下一轮刷新） */
    public void setData(String main, String unit, String sub) {
        if (mainView == null || unitView == null) return; // buildContent 尚未执行（视图未挂载）
        boolean has = main != null && !main.isEmpty();
        if (has) {
            mainView.setText(main);
            unitView.setText(unit != null ? unit : "");
            subView.setText(sub == null ? "" : sub);
            subView.setTextColor(DashboardTheme.DIM);
            setState(State.NORMAL);
            setBadge("");
        } else {
            setFailed();
            if (sub != null && !sub.isEmpty()) subView.setText(sub);
        }
    }

    /** 加载中（从未获取）：主值 "--"，灰态提示 */
    public void setLoading() {
        if (mainView == null) return;
        mainView.setText(DashboardTheme.NA);
        unitView.setText("");
        subView.setText("加载中...");
        subView.setTextColor(DashboardTheme.DIM);
        setState(State.LOADING);
        setBadge("加载中");
    }

    /** 获取失败：主值 "--"，红态明确提示，不静默 */
    public void setFailed() {
        if (mainView == null) return;
        mainView.setText(DashboardTheme.NA);
        unitView.setText("");
        subView.setText("获取失败");
        subView.setTextColor(DashboardTheme.RED);
        setState(State.ERROR);
        setBadge("获取失败");
    }

    /** 空态（兼容旧调用）：等同 {@link #setFailed()} */
    public void setEmpty() {
        setFailed();
    }
}
