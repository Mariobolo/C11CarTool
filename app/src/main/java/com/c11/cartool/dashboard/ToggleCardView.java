package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 开关型车控卡（1×1/2×1）：图标位 + 名称 + 状态高亮。
 * 点击执行车控，执行结果通过 {@link Listener#onToggle(SpanCardView, boolean)} 回传。
 */
public class ToggleCardView extends SpanCardView {

    public interface Listener {
        /** @param checked 点击时请求的新状态（true=开） */
        void onToggle(ToggleCardView card, boolean checked);
    }

    private final String emoji;
    private TextView nameView;
    private TextView stateView;
    private View dotView;
    private Listener listener;
    private boolean checked = false;

    /** @param experimental 标注实验性通道（锁车等），名称后加小标记 */
    public ToggleCardView(Context context, String title, String emoji, boolean experimental, int cornerDp) {
        super(context, experimental ? title + " ⚠" : title, cornerDp);
        this.emoji = emoji;
    }

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView iconView = text(20, DashboardTheme.TEXT, 0);
        iconView.setText(emoji);
        col.addView(iconView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f));

        nameView = text(11, DashboardTheme.TEXT, 0);
        col.addView(nameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout stateRow = new LinearLayout(ctx);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER);
        dotView = new View(ctx);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dlp.topMargin = dp(4);
        dotView.setLayoutParams(dlp);
        stateRow.addView(dotView);

        stateView = text(9, DashboardTheme.DIM, 0);
        stateView.setPadding(dp(3), dp(2), 0, 0);
        stateRow.addView(stateView);
        col.addView(stateRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content().addView(col);

        setOnClickListener(v -> {
            if (listener == null) return;
            listener.onToggle(ToggleCardView.this, !checked);
        });
    }

    /** 更新状态显示（主线程） */
    public void setChecked(boolean on) {
        this.checked = on;
        if (nameView == null || stateView == null || dotView == null) return; // buildContent 尚未执行
        setState(on ? State.ACTIVE : State.NORMAL);
        stateView.setText(on ? "已开" : "已关");
        stateView.setTextColor(on ? DashboardTheme.GREEN : DashboardTheme.DIM);
        dotView.setBackgroundColor(on ? DashboardTheme.GREEN : DashboardTheme.FAINT);
    }

    /** 执行中提示（禁用点击，防连点） */
    public void setBusy(boolean busy) {
        setEnabled(!busy);
        if (stateView != null && busy) { stateView.setText("执行中…"); stateView.setTextColor(DashboardTheme.YELLOW); }
    }

    public void setListener(Listener l) { this.listener = l; }
    public boolean isChecked() { return checked; }

    /** 强制重绘当前状态（如采集刷新后） */
    public void refreshView() { setChecked(checked); }
}
