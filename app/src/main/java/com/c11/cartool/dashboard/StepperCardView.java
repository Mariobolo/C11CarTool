package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 步进型车控卡：[-] 数值 [+]（温度/风量等）。
 * 点击 ± 执行车控，当前值由外部刷新。
 */
public class StepperCardView extends SpanCardView {

    public interface Listener {
        /** @param delta +1 或 -1 */
        void onStep(StepperCardView card, int delta);
    }

    private TextView valueView;
    private TextView unitView;
    private Listener listener;

    public StepperCardView(Context context, String title, String unit, int cornerDp) {
        super(context, title, cornerDp);
        this.unitText = unit;
    }

    private String unitText = "";

    @Override
    protected void buildContent() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        row.addView(makeStepBtn("−", DashboardTheme.BLUE, -1));
        row.addView(makeValueBlock(), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        row.addView(makeStepBtn("＋", DashboardTheme.ORANGE, +1));

        content().addView(row);
    }

    private LinearLayout makeValueBlock() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        valueView = text(26, DashboardTheme.TEXT, Typeface.BOLD);
        col.addView(valueView);

        unitView = text(10, DashboardTheme.DIM, 0);
        unitView.setText(unitText);
        col.addView(unitView);

        return col;
    }

    private TextView makeStepBtn(String label, int color, final int delta) {
        TextView btn = text(24, color, Typeface.BOLD);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(10), 0, dp(10), 0);
        btn.setOnClickListener(v -> {
            if (listener != null) listener.onStep(StepperCardView.this, delta);
        });
        return btn;
    }

    public void setValue(String text) {
        if (valueView == null) return; // buildContent 尚未执行
        valueView.setText(text == null || text.isEmpty() ? DashboardTheme.NA : text);
        setState(text == null || text.isEmpty() ? State.ERROR : State.NORMAL);
    }

    public void setListener(Listener l) { this.listener = l; }
}
