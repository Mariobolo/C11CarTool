package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 统一小模块（瓷砖）视图，1×1 / 2×1 等小格，玻璃底。
 *
 * <p>四种类型：
 * <ul>
 *   <li>{@link Type#VALUE} 只读数值：标签 + 大数值/单位 + 副行；</li>
 *   <li>{@link Type#STEP} 步进：标签 + [− 当前值 +]（建议占 2×1，−/+ ≥76dp）；</li>
 *   <li>{@link Type#TOGGLE} 开关：整格可点，按 checked 切换玻璃色调与"开/关"；</li>
 *   <li>{@link Type#ACTION} 动作：整格可点，显示图标 + 文字。</li>
 * </ul>
 * 数据三态：{@link #setLoading} / {@link #setValue} / {@link #setFailed}，不放假数据。
 */
public class TileView extends FrameLayout {

    public enum Type { VALUE, STEP, TOGGLE, ACTION }

    public interface Listener {
        default void onStep(int delta) {}
        default void onToggle(boolean checked) {}
        default void onPress() {}
    }

    private final Type type;
    private final String labelText;

    private LinearLayout root;
    private TextView labelView;
    private TextView valueView;
    private TextView unitView;
    private TextView subView;
    private TextView stateView;
    private TextView badgeView;

    private Listener listener;
    private boolean checked;
    private boolean failed;

    public TileView(Context context, Type type, String label) {
        super(context);
        this.type = type;
        this.labelText = label;
        setBackground(Glass.bg(context, 12, Glass.NORMAL));
        setPadding(dp(8), dp(6), dp(8), dp(6));
        build();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    // ── 构建 ──

    private void build() {
        root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        labelView = make(GridDimens.SP_LABEL, DashboardTheme.DIM, false);
        labelView.setText(labelText);

        switch (type) {
            case VALUE:
                buildValue();
                break;
            case STEP:
                buildStep();
                break;
            case TOGGLE:
                buildToggle();
                break;
            case ACTION:
                buildAction();
                break;
        }

        // 渠道角标（右下）
        badgeView = make(GridDimens.SP_BADGE, DashboardTheme.FAINT, false);
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM | Gravity.END;
        addView(badgeView, blp);
    }

    private void buildValue() {
        root.addView(labelView);
        LinearLayout valueRow = new LinearLayout(getContext());
        valueRow.setGravity(Gravity.CENTER | Gravity.BOTTOM);
        valueView = make(GridDimens.SP_VALUE, DashboardTheme.TEXT, true);
        valueView.setText("--");
        unitView = make(GridDimens.SP_LABEL, DashboardTheme.DIM, false);
        valueRow.addView(valueView);
        valueRow.addView(unitView);
        root.addView(valueRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        subView = make(GridDimens.SP_LABEL, DashboardTheme.FAINT, false);
        root.addView(subView);
    }

    private void buildStep() {
        root.addView(labelView);
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        TextView minus = stepButton("−");
        valueView = make(GridDimens.SP_STEP_VAL, DashboardTheme.TEXT, true);
        valueView.setText("--");
        valueView.setGravity(Gravity.CENTER);
        TextView plus = stepButton("+");
        row.addView(minus, new LinearLayout.LayoutParams(0, dp(76), 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.2f));
        row.addView(plus, new LinearLayout.LayoutParams(0, dp(76), 1f));
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        minus.setOnClickListener(v -> { if (listener != null) listener.onStep(-1); });
        plus.setOnClickListener(v -> { if (listener != null) listener.onStep(1); });
    }

    private void buildToggle() {
        stateView = make(GridDimens.SP_ACTION, DashboardTheme.DIM, true);
        stateView.setText("关");
        root.addView(labelView);
        root.addView(stateView);
        applyToggleVisual();
        setClickable(true);
        setOnClickListener(v -> {
            checked = !checked;          // 乐观反馈：视觉立即切换
            applyToggleVisual();
            if (listener != null) listener.onToggle(checked);
        });
    }

    private void buildAction() {
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, GridDimens.SP_ACTION);
        labelView.setTextColor(DashboardTheme.TEXT);
        root.addView(labelView);
        setClickable(true);
        setOnClickListener(v -> { if (listener != null) listener.onPress(); });
    }

    private TextView stepButton(String s) {
        TextView b = make(GridDimens.SP_STEP_BTN, DashboardTheme.TEXT, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Glass.bg(getContext(), 10, Glass.NORMAL));
        b.setClickable(true);
        return b;
    }

    // ── 数据更新 ──

    public void setValue(String value, String unit) {
        failed = false;
        if (valueView != null) {
            valueView.setText(value == null ? "--" : value);
            valueView.setTextColor(DashboardTheme.TEXT);
        }
        if (unitView != null) unitView.setText(unit == null ? "" : unit);
        setBackground(Glass.bg(getContext(), 12, Glass.NORMAL));
    }

    public void setSub(String sub) {
        if (subView != null) subView.setText(sub == null ? "" : sub);
    }

    public void setStepValue(String value) {
        failed = false;
        if (valueView != null) {
            valueView.setText(value == null ? "--" : value);
            valueView.setTextColor(DashboardTheme.TEXT);
        }
    }

    public void setChecked(boolean c) {
        this.checked = c;
        this.failed = false;
        applyToggleVisual();
    }

    public void setLoading() {
        if (valueView != null) {
            valueView.setText("··");
            valueView.setTextColor(DashboardTheme.DIM);
        }
        if (stateView != null) stateView.setText("··");
    }

    public void setFailed() {
        this.failed = true;
        if (valueView != null) {
            valueView.setText("--");
            valueView.setTextColor(DashboardTheme.RED);
        }
        if (stateView != null) {
            stateView.setText("失败");
            stateView.setTextColor(DashboardTheme.RED);
        }
        setBackground(Glass.bg(getContext(), 12, Glass.RED));
    }

    /** 动态标签（ACTION 型用，如车窗 "主驾 50%"） */
    public void setLabel(String text) {
        if (labelView != null) labelView.setText(text == null ? "" : text);
    }

    public void setChannel(String channel) {
        badgeView.setText(channel == null ? "" : channel);
    }

    private void applyToggleVisual() {
        if (stateView == null) return;
        stateView.setText(checked ? "开" : "关");
        stateView.setTextColor(checked ? DashboardTheme.GREEN : DashboardTheme.DIM);
        setBackground(Glass.bg(getContext(), 12, checked ? Glass.BLUE : Glass.NORMAL));
    }

    // ── 工具 ──

    private TextView make(int sp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(false);
        return tv;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
