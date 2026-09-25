package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 锁车大卡（2×2）：锁车 / 解锁 两个大按钮。
 * ⚠ 实验性：整车锁 Rightware 通道实车标定中（讯飞语音不发 opcode1200），
 * 执行后请目视确认是否落锁；不保证生效，后期根据标定结果切换通道。
 */
public class LockCardView extends SpanCardView {

    public interface Listener {
        /** @param lock true=锁车，false=解锁 */
        void onLock(LockCardView card, boolean lock);
    }

    private Listener listener;

    public LockCardView(Context context, int cornerDp) {
        super(context, "车门锁 ⚠实验性", cornerDp);
    }

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView tip = text(8, DashboardTheme.YELLOW, 0);
        tip.setText("执行后请目视确认");
        col.addView(tip);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        row.addView(bigBtn("🔒 锁车", DashboardTheme.GREEN, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        row.addView(bigBtn("🔓 解锁", DashboardTheme.BLUE, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        col.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content().addView(col);
    }

    private TextView bigBtn(String label, int color, final boolean lock) {
        TextView btn = text(15, color, Typeface.BOLD);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener(v -> {
            if (listener != null) listener.onLock(LockCardView.this, lock);
        });
        return btn;
    }

    public void setListener(Listener l) { this.listener = l; }
}
