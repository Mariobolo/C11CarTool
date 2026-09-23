package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 工具入口图标卡（1×1）：emoji + 名称，点击跳转/执行。
 */
public class ToolEntryCardView extends SpanCardView {

    public interface Listener {
        void onTap(ToolEntryCardView card);
    }

    private Listener listener;

    public ToolEntryCardView(Context context, String title, String emoji, int cornerDp) {
        super(context, title, cornerDp);
        this.emoji = emoji;
    }

    private String emoji;

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView iconView = text(24, DashboardTheme.CYAN, 0);
        iconView.setText(emoji);
        col.addView(iconView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f));

        TextView nameView = text(10, DashboardTheme.TEXT, 0);
        col.addView(nameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content().addView(col);

        setOnClickListener(v -> {
            if (listener != null) listener.onTap(ToolEntryCardView.this);
        });
    }

    public void setListener(Listener l) { this.listener = l; }
}
