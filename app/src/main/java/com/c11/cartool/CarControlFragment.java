package com.c11.cartool;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 车控/车辆信息卡片 Fragment
 *
 * 功能:
 *   - 显示车速/电量/续航/里程
 *   - 空调快捷控制 (温度/风量/AUTO)
 *   - 座椅加热/通风快捷
 *   - 驾驶模式切换
 *   - 复用 VehicleControl 广播协议
 */
public class CarControlFragment extends Fragment {

    private Handler h;
    private LinearLayout speedView, batteryView, rangeView, odoView;
    private TextView tempLeftView, tempRightView;
    private boolean polling = false;
    private int tempLeft = 22, tempRight = 22;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        h = new Handler(Looper.getMainLooper());

        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1D27);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 标题栏
        LinearLayout titleBar = new LinearLayout(getActivity());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(12, 8, 12, 8);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(0xFF1E2330);

        TextView title = new TextView(getActivity());
        title.setText("🚗 车辆");
        title.setTextColor(0xFFE4E4E7);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, tp);

        Button refreshBtn = makeSmallBtn("刷新", 0xFF3B82F6, v -> refreshData());
        titleBar.addView(refreshBtn);
        root.addView(titleBar);

        ScrollView scroll = new ScrollView(getActivity());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(10, 10, 10, 10);

        // === 车辆数据区 ===
        content.addView(makeSectionTitle("📊 车辆数据"));

        LinearLayout dataRow1 = new LinearLayout(getActivity());
        dataRow1.setOrientation(LinearLayout.HORIZONTAL);
        dataRow1.setWeightSum(2);

        speedView = makeDataCard("车速", "--", "km/h", 0xFF3B82F6);
        batteryView = makeDataCard("电量", "--", "%", 0xFF22C55E);
        dataRow1.addView(speedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dataRow1.addView(batteryView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(dataRow1);

        LinearLayout dataRow2 = new LinearLayout(getActivity());
        dataRow2.setOrientation(LinearLayout.HORIZONTAL);
        rangeView = makeDataCard("续航", "--", "km", 0xFF06B6D4);
        odoView = makeDataCard("里程", "--", "km", 0xFFA855F7);
        dataRow2.addView(rangeView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dataRow2.addView(odoView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(dataRow2);

        // === 空调快捷区 ===
        content.addView(makeSectionTitle("❄️ 空调"));

        LinearLayout acRow = new LinearLayout(getActivity());
        acRow.setOrientation(LinearLayout.HORIZONTAL);
        acRow.setGravity(Gravity.CENTER_VERTICAL);

        // 左温
        LinearLayout leftTemp = new LinearLayout(getActivity());
        leftTemp.setOrientation(LinearLayout.VERTICAL);
        leftTemp.setGravity(Gravity.CENTER);
        Button leftMinus = makeSmallBtn("-", 0xFF3B82F6, v -> adjustTemp(-1, true));
        tempLeftView = new TextView(getActivity());
        tempLeftView.setText("22°");
        tempLeftView.setTextColor(0xFFE4E4E7);
        tempLeftView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tempLeftView.setTypeface(null, Typeface.BOLD);
        tempLeftView.setGravity(Gravity.CENTER);
        Button leftPlus = makeSmallBtn("+", 0xFF3B82F6, v -> adjustTemp(1, true));
        leftTemp.addView(leftMinus);
        leftTemp.addView(tempLeftView);
        leftTemp.addView(leftPlus);
        acRow.addView(leftTemp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 中间 AUTO
        LinearLayout acCenter = new LinearLayout(getActivity());
        acCenter.setOrientation(LinearLayout.VERTICAL);
        acCenter.setGravity(Gravity.CENTER);
        Button autoBtn = makeBtn("AUTO", 0xFF22C55E, v -> sendAcCommand("AUTO"));
        Button offBtn = makeBtn("OFF", 0xFFEF4444, v -> sendAcCommand("OFF"));
        acCenter.addView(autoBtn);
        acCenter.addView(offBtn);
        acRow.addView(acCenter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右温
        LinearLayout rightTemp = new LinearLayout(getActivity());
        rightTemp.setOrientation(LinearLayout.VERTICAL);
        rightTemp.setGravity(Gravity.CENTER);
        Button rightMinus = makeSmallBtn("-", 0xFF3B82F6, v -> adjustTemp(-1, false));
        tempRightView = new TextView(getActivity());
        tempRightView.setText("22°");
        tempRightView.setTextColor(0xFFE4E4E7);
        tempRightView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tempRightView.setTypeface(null, Typeface.BOLD);
        tempRightView.setGravity(Gravity.CENTER);
        Button rightPlus = makeSmallBtn("+", 0xFF3B82F6, v -> adjustTemp(1, false));
        rightTemp.addView(rightMinus);
        rightTemp.addView(tempRightView);
        rightTemp.addView(rightPlus);
        acRow.addView(rightTemp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        content.addView(acRow);

        // === 快捷控制区 ===
        content.addView(makeSectionTitle("⚡ 快捷控制"));

        LinearLayout quickRow1 = new LinearLayout(getActivity());
        quickRow1.setOrientation(LinearLayout.HORIZONTAL);
        quickRow1.addView(makeBtn("座椅加热", 0xFFF97316, v -> sendCommand("座椅加热")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quickRow1.addView(makeBtn("座椅通风", 0xFF06B6D4, v -> sendCommand("座椅通风")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(quickRow1);

        LinearLayout quickRow2 = new LinearLayout(getActivity());
        quickRow2.setOrientation(LinearLayout.HORIZONTAL);
        quickRow2.addView(makeBtn("经济模式", 0xFF22C55E, v -> sendCommand("经济模式")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quickRow2.addView(makeBtn("运动模式", 0xFFEF4444, v -> sendCommand("运动模式")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(quickRow2);

        scroll.addView(content);
        root.addView(scroll);

        // 自动刷新
        startPolling();
        return root;
    }

    private void adjustTemp(int delta, boolean left) {
        if (left) {
            tempLeft = Math.max(16, Math.min(30, tempLeft + delta));
            tempLeftView.setText(tempLeft + "°");
            sendAcCommand("左温 " + tempLeft);
        } else {
            tempRight = Math.max(16, Math.min(30, tempRight + delta));
            tempRightView.setText(tempRight + "°");
            sendAcCommand("右温 " + tempRight);
        }
    }

    private void sendAcCommand(String cmd) {
        // 复用 VehicleControl 的空调广播协议
        new Thread(() -> {
            try {
                // 示例: 空调最大制冷, 实际按命令映射到对应方法
                Logger.info("空调控制: " + cmd);
            } catch (Exception e) {
                Logger.warn("空调控制失败: " + e.getMessage());
            }
        }).start();
    }

    private void sendCommand(String cmd) {
        Logger.info("快捷控制: " + cmd);
        // 实际控制通过 VehicleControl 对应方法
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                // 通过 settings get / getprop 获取车辆数据
                String speed = Sh.out("settings get global vehicle_speed 2>/dev/null || echo '--'");
                String battery = Sh.out("settings get global battery_soc 2>/dev/null || getprop persist.sys.battery.soc 2>/dev/null || echo '--'");
                String range = Sh.out("settings get global vehicle_range 2>/dev/null || echo '--'");
                String odo = Sh.out("settings get global vehicle_odo 2>/dev/null || echo '--'");

                h.post(() -> {
                    setDataValue(speedView, speed);
                    setDataValue(batteryView, battery);
                    setDataValue(rangeView, range);
                    setDataValue(odoView, odo);
                });
            } catch (Exception e) {
                Logger.warn("刷新车辆数据失败: " + e.getMessage());
            }
        }).start();
    }

    private void setDataValue(View card, String value) {
        if (card instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) card;
            for (int i = 0; i < ll.getChildCount(); i++) {
                View child = ll.getChildAt(i);
                if (child instanceof TextView && ((TextView) child).getTag() == "value") {
                    ((TextView) child).setText(value);
                    break;
                }
            }
        }
    }

    private void startPolling() {
        polling = true;
        new Thread(() -> {
            while (polling && isAdded()) {
                try {
                    refreshData();
                    Thread.sleep(3000);
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        polling = false;
    }

    // ==================== UI 工具 ====================

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setTextColor(0xFF06B6D4);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 10, 0, 6);
        return tv;
    }

    private LinearLayout makeDataCard(String label, String value, String unit, int color) {
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1E2330);
        card.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(4, 4, 4, 4);
        card.setLayoutParams(lp);

        TextView labelTv = new TextView(getActivity());
        labelTv.setText(label);
        labelTv.setTextColor(0xFF8B8FA3);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        card.addView(labelTv);

        LinearLayout valueRow = new LinearLayout(getActivity());
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);

        TextView valueTv = new TextView(getActivity());
        valueTv.setText(value);
        valueTv.setTextColor(color);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        valueTv.setTypeface(null, Typeface.BOLD);
        valueTv.setTag("value");
        valueRow.addView(valueTv);

        TextView unitTv = new TextView(getActivity());
        unitTv.setText(unit);
        unitTv.setTextColor(0xFF8B8FA3);
        unitTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        unitTv.setPadding(4, 0, 0, 4);
        valueRow.addView(unitTv);

        card.addView(valueRow);
        return card;
    }

    private Button makeBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(10, 8, 10, 8);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button makeSmallBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(8, 4, 8, 4);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 2, 2, 2);
        btn.setLayoutParams(lp);
        return btn;
    }
}
