package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.c11.cartool.vehicle.VehicleController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 全部车控页（仪表盘页之一，状态条「🎛 车控」进入）。
 *
 * <p>把当前已实现的车控命令全部平铺到前端，不藏在工程模式：
 * 分组渲染、点击即下发；同一功能存在多个通道时并列列出（标注通道），
 * 上机时分别试、观察实车即可确定正解；标 ⚠ 者为通道待真机标定。
 * 未找到有效通道的功能（如前备箱）以说明文字给出，不放假按钮、不制造假成功。
 */
public class ControlListPage extends LinearLayout {

    /** 命令执行器：页面只负责触发，真正的后台执行与结果 Toast 由 Activity 提供 */
    public interface CommandRunner {
        void run(String label, Callable<Boolean> task);
    }

    private static final class Cmd {
        final String label;
        final Callable<Boolean> task;
        final boolean experimental;
        Cmd(String label, Callable<Boolean> task, boolean experimental) {
            this.label = label;
            this.task = task;
            this.experimental = experimental;
        }
    }

    private static final int PER_ROW = 4;

    private final LinearLayout content;

    public ControlListPage(Context ctx, VehicleController vc, CommandRunner runner) {
        super(ctx);
        setOrientation(VERTICAL);
        setBackgroundColor(DashboardTheme.BG);

        TextView hint = new TextView(ctx);
        hint.setText("点击即下发；⚠ = 通道待标定。多通道同名按钮请分别试，观察实车确定正解。");
        hint.setTextColor(DashboardTheme.DIM);
        hint.setTextSize(11);
        hint.setPadding(dp(12), dp(8), dp(12), dp(4));
        addView(hint, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(ctx);
        content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), dp(12));
        sv.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        buildGroups(vc, runner);
    }

    private void buildGroups(final VehicleController vc, final CommandRunner runner) {
        LinkedHashMap<String, List<Cmd>> groups =
                new LinkedHashMap<String, List<Cmd>>();

        // ── 空调座舱 ──
        List<Cmd> hvac = new ArrayList<Cmd>();
        hvac.add(new Cmd("空调开·语音", new Callable<Boolean>() { public Boolean call() { return vc.acOn(); } }, false));
        hvac.add(new Cmd("空调关·语音", new Callable<Boolean>() { public Boolean call() { return vc.acOff(); } }, false));
        hvac.add(new Cmd("空调开·settings", new Callable<Boolean>() { public Boolean call() { return vc.acSwitchOn(); } }, true));
        hvac.add(new Cmd("空调关·settings", new Callable<Boolean>() { public Boolean call() { return vc.acSwitchOff(); } }, true));
        hvac.add(new Cmd("最大制冷开", new Callable<Boolean>() { public Boolean call() { return vc.acMaxOn(); } }, false));
        hvac.add(new Cmd("最大制冷关", new Callable<Boolean>() { public Boolean call() { return vc.acMaxOff(); } }, false));
        hvac.add(new Cmd("内循环", new Callable<Boolean>() { public Boolean call() { return vc.setAirInnerLoop(true); } }, false));
        hvac.add(new Cmd("外循环", new Callable<Boolean>() { public Boolean call() { return vc.setAirInnerLoop(false); } }, false));
        hvac.add(new Cmd("前除霜开", new Callable<Boolean>() { public Boolean call() { return vc.frontDefrostOn(); } }, false));
        hvac.add(new Cmd("前除霜关", new Callable<Boolean>() { public Boolean call() { return vc.frontDefrostOff(); } }, false));
        hvac.add(new Cmd("后除霜开", new Callable<Boolean>() { public Boolean call() { return vc.rearDefrostOn(); } }, false));
        hvac.add(new Cmd("后除霜关", new Callable<Boolean>() { public Boolean call() { return vc.rearDefrostOff(); } }, false));
        hvac.add(new Cmd("温度22℃", new Callable<Boolean>() { public Boolean call() { return vc.setAcTemperature(22); } }, false));
        hvac.add(new Cmd("温度24℃", new Callable<Boolean>() { public Boolean call() { return vc.setAcTemperature(24); } }, false));
        hvac.add(new Cmd("温度26℃", new Callable<Boolean>() { public Boolean call() { return vc.setAcTemperature(26); } }, false));
        hvac.add(new Cmd("温度28℃", new Callable<Boolean>() { public Boolean call() { return vc.setAcTemperature(28); } }, false));
        hvac.add(new Cmd("风量1", new Callable<Boolean>() { public Boolean call() { return vc.setAcFanSpeed(1); } }, false));
        hvac.add(new Cmd("风量3", new Callable<Boolean>() { public Boolean call() { return vc.setAcFanSpeed(3); } }, false));
        hvac.add(new Cmd("风量5", new Callable<Boolean>() { public Boolean call() { return vc.setAcFanSpeed(5); } }, false));
        hvac.add(new Cmd("风量7", new Callable<Boolean>() { public Boolean call() { return vc.setAcFanSpeed(7); } }, false));
        // 空调运行模式（自动/制冷/制热/通风）：strCarAirStatus 各模式整数值待真机标定，先给 raw 候选分别试
        hvac.add(new Cmd("空调模式raw0⚠", new Callable<Boolean>() { public Boolean call() { return vc.setAirStatusRaw(0); } }, true));
        hvac.add(new Cmd("空调模式raw1⚠", new Callable<Boolean>() { public Boolean call() { return vc.setAirStatusRaw(1); } }, true));
        hvac.add(new Cmd("空调模式raw2⚠", new Callable<Boolean>() { public Boolean call() { return vc.setAirStatusRaw(2); } }, true));
        hvac.add(new Cmd("空调模式raw3⚠", new Callable<Boolean>() { public Boolean call() { return vc.setAirStatusRaw(3); } }, true));
        groups.put("空调座舱", hvac);

        // ── 灯光（旧语音广播 tocarcontrol）──
        List<Cmd> lights = new ArrayList<Cmd>();
        lights.add(new Cmd("近光开", new Callable<Boolean>() { public Boolean call() { return vc.lowBeamOn(); } }, false));
        lights.add(new Cmd("近光关", new Callable<Boolean>() { public Boolean call() { return vc.lowBeamOff(); } }, false));
        lights.add(new Cmd("远光开", new Callable<Boolean>() { public Boolean call() { return vc.highBeamOn(); } }, true));
        lights.add(new Cmd("远光关", new Callable<Boolean>() { public Boolean call() { return vc.highBeamOff(); } }, true));
        lights.add(new Cmd("示廓灯开", new Callable<Boolean>() { public Boolean call() { return vc.positionLightOn(); } }, false));
        lights.add(new Cmd("示廓灯关", new Callable<Boolean>() { public Boolean call() { return vc.positionLightOff(); } }, false));
        lights.add(new Cmd("前雾灯开", new Callable<Boolean>() { public Boolean call() { return vc.frontFogOn(); } }, true));
        lights.add(new Cmd("前雾灯关", new Callable<Boolean>() { public Boolean call() { return vc.frontFogOff(); } }, true));
        lights.add(new Cmd("后雾灯开", new Callable<Boolean>() { public Boolean call() { return vc.fogLightOn(); } }, false));
        lights.add(new Cmd("后雾灯关", new Callable<Boolean>() { public Boolean call() { return vc.fogLightOff(); } }, false));
        lights.add(new Cmd("自动灯光", new Callable<Boolean>() { public Boolean call() { return vc.autoLights(); } }, true));
        lights.add(new Cmd("关闭全部灯光", new Callable<Boolean>() { public Boolean call() { return vc.closeAllLights(); } }, true));
        // 阅读灯（分位置，CARLIGHT_*DOMELAMPCTRL）
        lights.add(new Cmd("前左阅读灯开", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_FLDOMELAMPCTRL", true); } }, true));
        lights.add(new Cmd("前左阅读灯关", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_FLDOMELAMPCTRL", false); } }, true));
        lights.add(new Cmd("前右阅读灯开", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_FRDOMELAMPCTRL", true); } }, true));
        lights.add(new Cmd("前右阅读灯关", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_FRDOMELAMPCTRL", false); } }, true));
        lights.add(new Cmd("后左阅读灯开", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_RLDOMELAMPCTRL", true); } }, true));
        lights.add(new Cmd("后左阅读灯关", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_RLDOMELAMPCTRL", false); } }, true));
        lights.add(new Cmd("后右阅读灯开", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_RRDOMELAMPCTRL", true); } }, true));
        lights.add(new Cmd("后右阅读灯关", new Callable<Boolean>() { public Boolean call() { return vc.domeLight("CARLIGHT_RRDOMELAMPCTRL", false); } }, true));
        groups.put("灯光", lights);

        // ── 车门锁 ──
        List<Cmd> locks = new ArrayList<Cmd>();
        locks.add(new Cmd("车门锁·闭锁", new Callable<Boolean>() { public Boolean call() { return vc.lockCar(); } }, true));
        locks.add(new Cmd("车门锁·解锁", new Callable<Boolean>() { public Boolean call() { return vc.unlockCar(); } }, true));
        groups.put("车门锁", locks);

        // ── 儿童锁 ──
        List<Cmd> child = new ArrayList<Cmd>();
        child.add(new Cmd("左童锁开", new Callable<Boolean>() { public Boolean call() { return vc.leftChildLockOn(); } }, false));
        child.add(new Cmd("左童锁关", new Callable<Boolean>() { public Boolean call() { return vc.leftChildLockOff(); } }, false));
        child.add(new Cmd("右童锁开", new Callable<Boolean>() { public Boolean call() { return vc.rightChildLockOn(); } }, false));
        child.add(new Cmd("右童锁关", new Callable<Boolean>() { public Boolean call() { return vc.rightChildLockOff(); } }, false));
        groups.put("儿童锁", child);

        // ── 车窗（handMessage SET，按窗组织每行 4 档）──
        List<Cmd> win = new ArrayList<Cmd>();
        addWindowRow(win, vc, "主驾", "front_left");
        addWindowRow(win, vc, "副驾", "front_right");
        addWindowRow(win, vc, "左后", "rear_left");
        addWindowRow(win, vc, "右后", "rear_right");
        groups.put("车窗（点击设定目标开度）", win);

        // ── 后备箱 ──
        List<Cmd> trunk = new ArrayList<Cmd>();
        trunk.add(new Cmd("后备箱开", new Callable<Boolean>() { public Boolean call() { return vc.openTrunk(); } }, false));
        trunk.add(new Cmd("后备箱关", new Callable<Boolean>() { public Boolean call() { return vc.closeTrunk(); } }, false));
        groups.put("后备箱", trunk);

        // ── 其它 ──
        List<Cmd> other = new ArrayList<Cmd>();
        other.add(new Cmd("后视镜加热开", new Callable<Boolean>() { public Boolean call() { return vc.mirrorHeatOn(); } }, false));
        other.add(new Cmd("后视镜加热关", new Callable<Boolean>() { public Boolean call() { return vc.mirrorHeatOff(); } }, false));
        other.add(new Cmd("车窗锁开", new Callable<Boolean>() { public Boolean call() { return vc.windowForbitOn(); } }, false));
        other.add(new Cmd("车窗锁关", new Callable<Boolean>() { public Boolean call() { return vc.windowForbitOff(); } }, false));
        other.add(new Cmd("空调界面", new Callable<Boolean>() { public Boolean call() { return vc.openAcPage(); } }, false));
        groups.put("其它", other);

        // 渲染
        for (java.util.Map.Entry<String, List<Cmd>> e : groups.entrySet()) {
            addGroup(e.getKey(), e.getValue(), runner);
        }

        // 前机盖（=前储物/前备箱）：物理引擎盖由车外拉手开启，App 无控制通道；状态只读 eventId 9128
        TextView noFrunk = new TextView(getContext());
        noFrunk.setText("前机盖（前储物/前备箱）：物理引擎盖由车外拉手开启，App 无控制通道，故不放按钮；其开合状态（只读 eventId 9128）已在仪表盘门态卡与信号清单显示。");
        noFrunk.setTextColor(DashboardTheme.ORANGE);
        noFrunk.setTextSize(11);
        noFrunk.setPadding(dp(6), dp(10), dp(6), dp(6));
        content.addView(noFrunk);
    }

    private void addWindowRow(List<Cmd> list, final VehicleController vc,
                              final String label, final String area) {
        list.add(new Cmd(label + "全关", new Callable<Boolean>() { public Boolean call() { return vc.setWindow(area, 0); } }, false));
        list.add(new Cmd(label + "微开", new Callable<Boolean>() { public Boolean call() { return vc.setWindow(area, 10); } }, false));
        list.add(new Cmd(label + "半开", new Callable<Boolean>() { public Boolean call() { return vc.setWindow(area, 50); } }, false));
        list.add(new Cmd(label + "全开", new Callable<Boolean>() { public Boolean call() { return vc.setWindow(area, 100); } }, false));
    }

    private void addGroup(String title, List<Cmd> cmds, final CommandRunner runner) {
        TextView bar = new TextView(getContext());
        bar.setText("  " + title);
        bar.setTextColor(DashboardTheme.CYAN);
        bar.setTextSize(13);
        bar.setTypeface(Typeface.DEFAULT_BOLD);
        bar.setBackgroundColor(DashboardTheme.SURFACE);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(10), 0, dp(4));
        content.addView(bar, bp);

        int rows = (cmds.size() + PER_ROW - 1) / PER_ROW;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            for (int c = 0; c < PER_ROW; c++) {
                int idx = r * PER_ROW + c;
                ViewBtn vb;
                if (idx < cmds.size()) {
                    final Cmd cmd = cmds.get(idx);
                    vb = new ViewBtn(cmd.label, cmd.experimental, new Runnable() {
                        @Override public void run() { runner.run(cmd.label, cmd.task); }
                    });
                } else {
                    vb = new ViewBtn(null, false, null);
                }
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, LayoutParams.WRAP_CONTENT, 1f);
                if (c > 0) p.leftMargin = dp(6);
                row.addView(vb.root, p);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(6);
            content.addView(row, rp);
        }
    }

    /** 车控按钮（圆角块；占位时 root 为空透明） */
    private final class ViewBtn {
        final TextView root;
        ViewBtn(String label, boolean experimental, Runnable onClick) {
            root = new TextView(getContext());
            if (label == null) {
                root.setVisibility(INVISIBLE);
                return;
            }
            root.setText(experimental ? label + " ⚠" : label);
            root.setTextColor(DashboardTheme.TEXT);
            root.setTextSize(12);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(6), dp(12), dp(6), dp(12));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(8));
            d.setColor(DashboardTheme.SURFACE);
            root.setBackground(d);
            if (onClick != null) root.setOnClickListener(v -> onClick.run());
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
