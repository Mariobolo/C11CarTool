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
        public final boolean toggleable;   // true=有开/关状态（点击翻转，需外部回读校准）；false=一次性动作按钮

        public Item(String id, String label, String emoji, boolean experimental) {
            this(id, label, emoji, experimental, false);
        }

        public Item(String id, String label, String emoji, boolean experimental, boolean toggleable) {
            this.id = id;
            this.label = label;
            this.emoji = emoji;
            this.experimental = experimental;
            this.toggleable = toggleable;
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
            if (it.toggleable) {
                // 状态型：乐观翻转并高亮，外部回读会再次校准
                boolean next = !Boolean.TRUE.equals(states.get(it.id));
                states.put(it.id, next);
                tint(cell, next);
                listener.onToggle(MiniGridCardView.this, it.id, next);
            } else {
                // 动作型：不翻转、不保持高亮；具体动作由 Activity 按 id 决定（不依赖 checked）
                listener.onToggle(MiniGridCardView.this, it.id, true);
            }
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
