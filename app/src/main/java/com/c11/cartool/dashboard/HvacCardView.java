package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 空调控制大卡（4×4 中控 / 4×2 紧凑）：
 * 主驾温度、副驾温度、风量 三个步进 + AC/最大制冷/内循环/前除霜/后除霜 开关组。
 * 所有动作只走 VehicleController 已验证方法，执行结果由外部（Activity）反馈。
 */
public class HvacCardView extends SpanCardView {

    public interface Listener {
        /** 温度步进：driver=true 主驾，false 副驾 */
        void onTempStep(HvacCardView card, boolean driver, int delta);
        void onFanStep(HvacCardView card, int delta);
        /** 开关组：action ∈ ac/max/inner/front/rear */
        void onToggle(HvacCardView card, String action, boolean checked);
    }

    private Listener listener;
    private final boolean compact;

    private TextView driverTempView;
    private TextView passengerTempView;
    private TextView fanView;

    // 开关状态缓存（避免重复 set 时闪烁）
    private boolean ac = false, max = false, inner = false, front = false, rear = false;

    public HvacCardView(Context context, boolean compact, int cornerDp) {
        super(context, "空调控制", cornerDp);
        this.compact = compact;
    }

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 行1：主驾温度 | 副驾温度
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TempBlock driver = new TempBlock("主驾", true);
        TempBlock passenger = new TempBlock("副驾", false);
        row1.addView(driver.root, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        row1.addView(passenger.root, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        driverTempView = driver.valueView;
        passengerTempView = passenger.valueView;
        col.addView(row1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 行2：风量 | 最大制冷
        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        FanBlock fan = new FanBlock();
        fanView = fan.valueView;
        row2.addView(fan.root, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f));
        row2.addView(makeMiniToggle("❄", "MAX", "max"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        col.addView(row2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 行3：开关组 AC / 内循环 / 前除霜 / 后除霜
        LinearLayout row3 = new LinearLayout(ctx);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(makeMiniToggle("⏻", "AC", "ac"));
        row3.addView(makeMiniToggle("↻", "循环", "inner"));
        row3.addView(makeMiniToggle("💧", "前除霜", "front"));
        row3.addView(makeMiniToggle("🔥", "后除霜", "rear"));
        col.addView(row3, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, compact ? 1f : 1f));

        content().addView(col);
    }

    // ── 温度步进块 ──
    private class TempBlock {
        final View root;
        final TextView valueView;

        TempBlock(String label, final boolean driver) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);

            TextView name = text(9, DashboardTheme.DIM, 0);
            name.setText(label);
            col.addView(name);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.addView(stepBtn("−", DashboardTheme.BLUE, d -> {
                if (listener != null) listener.onTempStep(HvacCardView.this, driver, d);
            }));
            valueView = text(20, DashboardTheme.TEXT, Typeface.BOLD);
            valueView.setText(DashboardTheme.NA);
            row.addView(valueView);
            row.addView(stepBtn("＋", DashboardTheme.ORANGE, d -> {
                if (listener != null) listener.onTempStep(HvacCardView.this, driver, d);
            }));
            col.addView(row);

            root = col;
        }
    }

    // ── 风量步进块 ──
    private class FanBlock {
        final View root;
        final TextView valueView;

        FanBlock() {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);

            TextView name = text(9, DashboardTheme.DIM, 0);
            name.setText("风量");
            col.addView(name);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.addView(stepBtn("−", DashboardTheme.BLUE, d -> {
                if (listener != null) listener.onFanStep(HvacCardView.this, d);
            }));
            valueView = text(20, DashboardTheme.TEXT, Typeface.BOLD);
            valueView.setText(DashboardTheme.NA);
            row.addView(valueView);
            row.addView(stepBtn("＋", DashboardTheme.ORANGE, d -> {
                if (listener != null) listener.onFanStep(HvacCardView.this, d);
            }));
            col.addView(row);

            root = col;
        }
    }

    private TextView stepBtn(String label, int color, final java.util.function.IntConsumer onStep) {
        TextView btn = text(20, color, Typeface.BOLD);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(8), 0, dp(8), 0);
        btn.setOnClickListener(v -> {
            if (onStep != null) onStep.accept(label.equals("−") ? -1 : +1);
        });
        return btn;
    }

    // ── 迷你开关（非高亮底色，激活变色） ──
    private View makeMiniToggle(final String emoji, String label, final String action) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(2), dp(2), dp(2), dp(2));

        TextView icon = text(14, DashboardTheme.DIM, 0);
        icon.setText(emoji);
        col.addView(icon);

        TextView name = text(8, DashboardTheme.DIM, 0);
        name.setText(label);
        col.addView(name);

        col.setOnClickListener(v -> {
            if (listener == null) return;
            boolean next;
            if ("ac".equals(action)) next = !ac;
            else if ("max".equals(action)) next = !max;
            else if ("inner".equals(action)) next = !inner;
            else if ("front".equals(action)) next = !front;
            else next = !rear;
            listener.onToggle(HvacCardView.this, action, next);
        });
        return col;
    }

    // ── 外部刷新 ──

    /** @param tempC 主/副驾设定温度，-1 无效 */
    public void setTemps(int driverC, int passengerC) {
        if (driverTempView != null)
            driverTempView.setText(driverC < 0 ? DashboardTheme.NA : driverC + "°");
        if (passengerTempView != null)
            passengerTempView.setText(passengerC < 0 ? DashboardTheme.NA : passengerC + "°");
    }

    public void setFan(int level) {
        if (fanView != null)
            fanView.setText(level < 0 ? DashboardTheme.NA : String.valueOf(level));
    }

    /** 开关组状态刷新（-1 无效不覆盖）；max 由外部显式传入 */
    public void setToggles(int acV, int maxV, int innerV, int frontV, int rearV) {
        if (acV >= 0) { ac = acV == 1; tintMini(toggles.get("ac"), ac); }
        if (maxV >= 0) { max = maxV == 1; tintMini(toggles.get("max"), max); }
        if (innerV >= 0) { inner = innerV == 1; tintMini(toggles.get("inner"), inner); }
        if (frontV >= 0) { front = frontV == 1; tintMini(toggles.get("front"), front); }
        if (rearV >= 0) { rear = rearV == 1; tintMini(toggles.get("rear"), rear); }
    }

    private final java.util.Map<String, LinearLayout> toggles = new java.util.HashMap<>();

    private void tintMini(LinearLayout col, boolean on) {
        if (col == null) return;
        for (int i = 0; i < col.getChildCount(); i++) {
            View c = col.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(on ? DashboardTheme.GREEN : DashboardTheme.DIM);
            }
        }
        col.setBackgroundColor(on ? 0x2222C55E : 0x00000000);
    }

    public void setListener(Listener l) { this.listener = l; }

    // 在内容构建完成后注册迷你开关引用
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (toggles.isEmpty() && contentContainer.getChildCount() >= 1
                && contentContainer.getChildAt(0) instanceof LinearLayout) {
            LinearLayout col = (LinearLayout) contentContainer.getChildAt(0);
            if (col.getChildCount() >= 3) {
                LinearLayout row3 = (LinearLayout) col.getChildAt(2);
                if (row3.getChildCount() >= 4) {
                    toggles.put("ac", (LinearLayout) row3.getChildAt(0));
                    toggles.put("inner", (LinearLayout) row3.getChildAt(1));
                    toggles.put("front", (LinearLayout) row3.getChildAt(2));
                    toggles.put("rear", (LinearLayout) row3.getChildAt(3));
                }
                LinearLayout row2 = (LinearLayout) col.getChildAt(1);
                if (row2.getChildCount() >= 2) {
                    toggles.put("max", (LinearLayout) row2.getChildAt(1));
                }
            }
        }
    }
}
