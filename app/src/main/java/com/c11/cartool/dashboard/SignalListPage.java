package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * 全车信号清单页（仪表盘页之一，状态条「📊 信号」进入）。
 *
 * <p>能力：
 * <ul>
 *   <li>顶部搜索框：按信号名 / 渠道 / 分组名实时过滤；</li>
 *   <li>分组标题条可点击折叠 / 展开（搜索时强制展开匹配项）；</li>
 *   <li>每行「中文名 | 值 | 渠道」，多渠道同名各占一行，全部可见。</li>
 * </ul>
 */
public class SignalListPage extends LinearLayout {

    private EditText searchBox;
    private LinearLayout content;
    private List<SignalRow> allRows = new ArrayList<SignalRow>();
    private final Set<String> collapsed = new HashSet<String>();

    public SignalListPage(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
        setBackgroundColor(DashboardTheme.BG);

        searchBox = new EditText(ctx);
        searchBox.setHint("搜索信号名 / 渠道 / 分组…");
        searchBox.setHintTextColor(DashboardTheme.FAINT);
        searchBox.setTextColor(DashboardTheme.TEXT);
        searchBox.setTextSize(13);
        searchBox.setSingleLine(true);
        searchBox.setBackgroundColor(DashboardTheme.SURFACE);
        searchBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        addView(searchBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { rebuild(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        ScrollView sv = new ScrollView(ctx);
        content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        int pad = dp(12);
        content.setPadding(pad, dp(8), pad, dp(10));
        sv.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** 用最新一轮快照的信号行重建（主线程）。 */
    public void setRows(List<SignalRow> rows) {
        allRows = rows == null ? new ArrayList<SignalRow>() : rows;
        rebuild();
    }

    private void rebuild() {
        content.removeAllViews();

        String q = searchBox.getText().toString().trim().toLowerCase(Locale.US);
        boolean searching = !q.isEmpty();

        // 按分组聚合（rows 已按分组排序，LinkedHashMap 保序）
        LinkedHashMap<String, List<SignalRow>> groups =
                new LinkedHashMap<String, List<SignalRow>>();
        for (SignalRow r : allRows) {
            List<SignalRow> l = groups.get(r.group);
            if (l == null) { l = new ArrayList<SignalRow>(); groups.put(r.group, l); }
            l.add(r);
        }

        int shown = 0;
        for (java.util.Map.Entry<String, List<SignalRow>> e : groups.entrySet()) {
            String group = e.getKey();
            List<SignalRow> rows = e.getValue();

            // 搜索过滤：组名命中则整组保留，否则保留组内命中行
            List<SignalRow> matched = new ArrayList<SignalRow>();
            if (searching) {
                boolean groupHit = group.toLowerCase(Locale.US).contains(q);
                for (SignalRow r : rows) {
                    if (groupHit || contains(r.name, q) || contains(r.channel, q)) matched.add(r);
                }
                if (matched.isEmpty()) continue;
            } else {
                matched = rows;
            }

            content.addView(groupBar(group, matched.size(), searching));
            shown++;
            // 折叠（仅非搜索态）：跳过该组明细
            if (!searching && collapsed.contains(group)) continue;
            for (SignalRow r : matched) content.addView(row(r));
        }

        if (shown == 0) {
            content.addView(headline(searching
                    ? "无匹配信号" : "暂无信号（ADB 未连接或本轮未采集到数据）"));
        }
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.US).contains(q);
    }

    private TextView groupBar(String title, int count, boolean searching) {
        TextView t = new TextView(getContext());
        t.setText((searching || !collapsed.contains(title) ? "▾ " : "▸ ")
                + title + "（" + count + "）");
        t.setTextColor(DashboardTheme.CYAN);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackgroundColor(DashboardTheme.SURFACE);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        t.setOnClickListener(v -> {
            if (searching) return; // 搜索态不折叠
            if (collapsed.contains(title)) collapsed.remove(title); else collapsed.add(title);
            rebuild();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout row(SignalRow r) {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(8), dp(5), dp(8), dp(5));

        // 渠道并入中文名：「中文名（渠道）」，多渠道同名据此区分
        TextView name = cell(r.name + "（" + shortChannel(r.channel) + "）",
                DashboardTheme.TEXT, 14, Gravity.START, 2.4f);
        TextView value = cell(r.value, DashboardTheme.GREEN, 14, Gravity.END, 1.4f);
        line.addView(name);
        line.addView(value);
        return line;
    }

    /** 渠道源 → 简短括号标注（取 channel 第一段并统一命名）。 */
    private static String shortChannel(String ch) {
        if (ch == null || ch.isEmpty()) return "未知";
        int slash = ch.indexOf('/');
        String head = slash > 0 ? ch.substring(0, slash) : ch;
        if ("node".equals(head)) return "XML";
        if ("LocationDataC23".equals(head)) return "GPS";
        if ("EnergyDataBinder".equals(head)) return "行程";
        return head; // settings / event / TPMS
    }

    private TextView cell(String text, int color, int sp, int gravity, float weight) {
        TextView t = new TextView(getContext());
        t.setText(text != null ? text : "");
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setSingleLine(false);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return t;
    }

    private TextView headline(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(DashboardTheme.DIM);
        t.setTextSize(14);
        t.setPadding(dp(8), dp(20), dp(8), dp(8));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
