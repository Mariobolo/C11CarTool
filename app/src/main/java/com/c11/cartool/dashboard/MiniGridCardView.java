package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用迷你开关组卡（2×N）：一排若干 1×1 迷你开关（emoji+名称）。
 * 用于灯光/车身/车窗/系统开关等批量按钮；激活态变绿。
 */
public class MiniGridCardView extends SpanCardView {

    public interface Listener {
        /** @param id 开关 id（构建时传入）；@param checked 请求的新状态 */
        void onToggle(MiniGridCardView card, String id, boolean checked);
    }

    public static class Item {
        public final String id;
        public final String label;
        public final String emoji;
        public final boolean experimental; // 标注 ⚠（通道未最终标定）

        public Item(String id, String label, String emoji, boolean experimental) {
            this.id = id;
            this.label = label;
            this.emoji = emoji;
            this.experimental = experimental;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Map<String, View> views = new HashMap<>();
    private final Map<String, Boolean> states = new HashMap<>();
    private Listener listener;

    public MiniGridCardView(Context context, String title, List<Item> items, int cornerDp) {
        super(context, title, cornerDp);
        this.items.addAll(items);
    }

    @Override
    protected void buildContent() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int perRow = items.size() <= 4 ? items.size() : 4;
        int rows = (items.size() + perRow - 1) / perRow;

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < perRow; c++) {
                int idx = r * perRow + c;
                if (idx >= items.size()) break;
                Item it = items.get(idx);
                View cell = makeCell(it);
                row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
            col.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        content().addView(col);
    }

    private View makeCell(final Item it) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        TextView icon = text(14, DashboardTheme.DIM, 0);
        icon.setText(it.emoji);
        cell.addView(icon);

        TextView name = text(8, DashboardTheme.DIM, 0);
        name.setText(it.experimental ? it.label + " ⚠" : it.label);
        cell.addView(name);

        cell.setOnClickListener(v -> {
            if (listener == null) return;
            boolean next = !Boolean.TRUE.equals(states.get(it.id));
            listener.onToggle(MiniGridCardView.this, it.id, next);
        });

        views.put(it.id, cell);
        states.put(it.id, false);
        tint(cell, false);
        return cell;
    }

    /** 外部刷新某开关状态（-1 忽略） */
    public void setChecked(String id, int state) {
        if (state < 0) return;
        boolean on = state == 1;
        states.put(id, on);
        View v = views.get(id);
        if (v instanceof LinearLayout) tint((LinearLayout) v, on);
    }

    private void tint(LinearLayout cell, boolean on) {
        cell.setBackgroundColor(on ? 0x2222C55E : 0x00000000);
        for (int i = 0; i < cell.getChildCount(); i++) {
            if (cell.getChildAt(i) instanceof TextView) {
                ((TextView) cell.getChildAt(i)).setTextColor(on ? DashboardTheme.GREEN : DashboardTheme.DIM);
            }
        }
    }

    public void setListener(Listener l) { this.listener = l; }
}
