package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.c11.cartool.Sh;
import com.c11.cartool.VehicleControl;
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
 *
 * <p>[FIX-20260928] 按用户反馈改造：
 * 开/关成对按钮合并为单个开关按钮，用小点标记状态（🟢已开 / 🔴已关 / ⚪未知）；
 * 空调模式按实测映射标注（0=自动 1=制冷 2=制热 3=制冷）；内外循环补自动模式；
 * 阅读灯/后视镜折叠/行人警示音/座椅改 handMessage 语音通道（见 VehicleController）。
 */
public class ControlListPage extends LinearLayout {

    /** 命令执行器：页面只负责触发，真正的后台执行与结果 Toast 由 Activity 提供 */
    public interface CommandRunner {
        void run(String label, Callable<Boolean> task);
    }

    /** [FIX-20260928] 命令项：offTask 非空 = 开关型（开/关合并单按钮）；stateKey = 状态回读键 */
    private static final class Cmd {
        final String label;
        final Callable<Boolean> onTask;
        final Callable<Boolean> offTask;
        final String stateKey;
        final boolean experimental;
        Cmd(String label, Callable<Boolean> onTask, boolean experimental) {
            this(label, onTask, experimental, null, null);
        }
        Cmd(String label, Callable<Boolean> onTask, boolean experimental,
            Callable<Boolean> offTask, String stateKey) {
            this.label = label;
            this.onTask = onTask;
            this.offTask = offTask;
            this.stateKey = stateKey;
            this.experimental = experimental;
        }
        boolean isToggle() { return offTask != null; }
    }

    private static final int PER_ROW = 4;

    private final LinearLayout content;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ControlListPage(Context ctx, VehicleController vc, CommandRunner runner) {
        super(ctx);
        setOrientation(VERTICAL);
        setBackgroundColor(DashboardTheme.BG);

        TextView hint = new TextView(ctx);
        hint.setText("点击即下发；🟢已开 🔴已关 ⚪未知；⚠ = 通道待标定。多通道同名按钮请分别试，观察实车确定正解。");
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
        hvac.add(tog("空调·语音", () -> vc.acOn(), () -> vc.acOff(), null, false));
        hvac.add(tog("空调·settings", () -> vc.acSwitchOn(), () -> vc.acSwitchOff(), "strCarAirSwitch", true));
        hvac.add(tog("最大制冷", () -> vc.acMaxOn(), () -> vc.acMaxOff(), null, false));
        hvac.add(tog("内循环", () -> vc.setAirInnerLoopMode(1), () -> vc.setAirInnerLoopMode(0), "strCarAirInner", false));
        hvac.add(cmd("自动循环", () -> vc.setAirInnerLoopMode(2), false));   // [FIX-20260928] 补自动模式
        hvac.add(tog("前除霜", () -> vc.frontDefrostOn(), () -> vc.frontDefrostOff(), "strCarFrontDefrost", false));
        hvac.add(tog("后除霜", () -> vc.rearDefrostOn(), () -> vc.rearDefrostOff(), "strCarRearDefrost", false));
        hvac.add(cmd("温度22℃", () -> vc.setAcTemperature(22), false));
        hvac.add(cmd("温度24℃", () -> vc.setAcTemperature(24), false));
        hvac.add(cmd("温度26℃", () -> vc.setAcTemperature(26), false));
        hvac.add(cmd("温度28℃", () -> vc.setAcTemperature(28), false));
        hvac.add(cmd("风量1", () -> vc.setAcFanSpeed(1), false));
        hvac.add(cmd("风量3", () -> vc.setAcFanSpeed(3), false));
        hvac.add(cmd("风量5", () -> vc.setAcFanSpeed(5), false));
        hvac.add(cmd("风量7", () -> vc.setAcFanSpeed(7), false));
        // [FIX-20260928] 空调模式按用户实测映射标注：0=自动 1=制冷 2=制热 3=制冷(待确认)
        hvac.add(cmd("模式·自动", () -> vc.setAirMode(0), false));
        hvac.add(cmd("模式·制冷", () -> vc.setAirMode(1), false));
        hvac.add(cmd("模式·制热", () -> vc.setAirMode(2), false));
        hvac.add(cmd("模式·制冷3", () -> vc.setAirMode(3), true));
        groups.put("空调座舱", hvac);

        // ── 灯光（旧语音广播 tocarcontrol）──
        List<Cmd> lights = new ArrayList<Cmd>();
        lights.add(tog("近光", () -> vc.lowBeamOn(), () -> vc.lowBeamOff(), null, false));
        lights.add(tog("远光", () -> vc.highBeamOn(), () -> vc.highBeamOff(), null, true));
        lights.add(tog("示廓灯", () -> vc.positionLightOn(), () -> vc.positionLightOff(), null, false));
        lights.add(tog("前雾灯", () -> vc.frontFogOn(), () -> vc.frontFogOff(), null, true));
        lights.add(tog("后雾灯", () -> vc.fogLightOn(), () -> vc.fogLightOff(), null, false));
        lights.add(cmd("自动灯光", () -> vc.autoLights(), true));
        lights.add(cmd("关闭全部灯光", () -> vc.closeAllLights(), true));
        // [FIX-20260928] 阅读灯：合并开/关；通道改 handMessage（实体名与真机事件名一致）
        lights.add(tog("前左阅读灯", () -> vc.readingLight("前左阅读灯", true), () -> vc.readingLight("前左阅读灯", false), null, true));
        lights.add(tog("前右阅读灯", () -> vc.readingLight("前右阅读灯", true), () -> vc.readingLight("前右阅读灯", false), null, true));
        lights.add(tog("后左阅读灯", () -> vc.readingLight("后左阅读灯", true), () -> vc.readingLight("后左阅读灯", false), null, true));
        lights.add(tog("后右阅读灯", () -> vc.readingLight("后右阅读灯", true), () -> vc.readingLight("后右阅读灯", false), null, true));
        groups.put("灯光", lights);

        // ── 车门锁 / 儿童锁 ──
        List<Cmd> locks = new ArrayList<Cmd>();
        locks.add(tog("车门锁", () -> vc.lockCar(), () -> vc.unlockCar(), "strCarVehicleLock", true));
        locks.add(tog("左童锁", () -> vc.leftChildLockOn(), () -> vc.leftChildLockOff(), "strCarChildLock", false));
        locks.add(tog("右童锁", () -> vc.rightChildLockOn(), () -> vc.rightChildLockOff(), "strCarChildLock", false));
        groups.put("车门锁 / 儿童锁", locks);

        // ── 车窗（handMessage SET，按窗组织每行 4 档）──
        List<Cmd> win = new ArrayList<Cmd>();
        addWindowRow(win, vc, "主驾", "front_left");
        addWindowRow(win, vc, "副驾", "front_right");
        addWindowRow(win, vc, "左后", "rear_left");
        addWindowRow(win, vc, "右后", "rear_right");
        groups.put("车窗（点击设定目标开度）", win);

        // ── 后备箱 ──
        List<Cmd> trunk = new ArrayList<Cmd>();
        trunk.add(tog("后备箱", () -> vc.openTrunk(), () -> vc.closeTrunk(), null, false));
        groups.put("后备箱", trunk);

        // ── 座椅 / 后视镜（语音通道）──
        // [FIX-20260928] 原 VehicleParams 的 leap.seat.* / 后视镜位置虚拟键无硬件消费者（读写均无效），
        // 改走 handMessage 语音家族；实体名 ⚠ 待实车确认（首轮请逐个试）。
        List<Cmd> seat = new ArrayList<Cmd>();
        seat.add(tog("主驾座椅加热⚠", () -> vc.seatFeature("主驾座椅加热", true), () -> vc.seatFeature("主驾座椅加热", false), null, true));
        seat.add(tog("副驾座椅加热⚠", () -> vc.seatFeature("副驾座椅加热", true), () -> vc.seatFeature("副驾座椅加热", false), null, true));
        seat.add(tog("主驾座椅通风⚠", () -> vc.seatFeature("主驾座椅通风", true), () -> vc.seatFeature("主驾座椅通风", false), null, true));
        seat.add(tog("副驾座椅通风⚠", () -> vc.seatFeature("副驾座椅通风", true), () -> vc.seatFeature("副驾座椅通风", false), null, true));
        seat.add(tog("方向盘加热⚠", () -> vc.seatFeature("方向盘加热", true), () -> vc.seatFeature("方向盘加热", false), null, true));
        seat.add(tog("后视镜折叠⚠", () -> vc.mirrorFold(true), () -> vc.mirrorFold(false), null, true));
        groups.put("座椅 / 后视镜（语音通道）", seat);

        // ── 其它 ──
        List<Cmd> other = new ArrayList<Cmd>();
        other.add(tog("后视镜加热", () -> vc.mirrorHeatOn(), () -> vc.mirrorHeatOff(), "strCarMirrorHeart", false));
        other.add(tog("车窗锁", () -> vc.windowForbitOn(), () -> vc.windowForbitOff(), "strCarWindowForbit", false));
        other.add(tog("行人警示音⚠", () -> vc.pedestrianAlert(true), () -> vc.pedestrianAlert(false), null, true));
        other.add(cmd("空调界面", () -> vc.openAcPage(), false));
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

    // [FIX-20260928] 便捷构造：动作型 / 开关型
    private static Cmd cmd(String label, Callable<Boolean> task, boolean experimental) {
        return new Cmd(label, task, experimental);
    }

    private static Cmd tog(String label, Callable<Boolean> on, Callable<Boolean> off,
                           String stateKey, boolean experimental) {
        return new Cmd(label, on, experimental, off, stateKey);
    }

    private void addWindowRow(List<Cmd> list, final VehicleController vc,
                              final String label, final String area) {
        list.add(cmd(label + "全关", () -> vc.setWindow(area, 0), false));
        list.add(cmd(label + "微开", () -> vc.setWindow(area, 10), false));
        list.add(cmd(label + "半开", () -> vc.setWindow(area, 50), false));
        list.add(cmd(label + "全开", () -> vc.setWindow(area, 100), false));
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
                    vb = new ViewBtn(cmds.get(idx), runner);
                } else {
                    vb = new ViewBtn(null, null);
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

    /** [FIX-20260928] 车控按钮：开关型带状态小点（🟢/🔴/⚪），点击翻转；动作型保持原样 */
    private final class ViewBtn {
        final TextView root;
        final Cmd cmd;
        Boolean state;   // null=未知

        ViewBtn(Cmd cmd, final CommandRunner runner) {
            this.cmd = cmd;
            root = new TextView(getContext());
            if (cmd == null) {
                root.setVisibility(INVISIBLE);
                return;
            }
            root.setTextSize(12);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(6), dp(12), dp(6), dp(12));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(8));
            d.setColor(DashboardTheme.SURFACE);
            root.setBackground(d);
            render();

            if (cmd.isToggle()) {
                root.setOnClickListener(v -> onToggleClick(runner));
                // 初始状态回读（有状态键才读）
                refreshState(null);
            } else {
                root.setOnClickListener(v ->
                        runner.run(cmd.label, cmd.onTask));
            }
        }

        private void render() {
            String text = cmd.experimental ? cmd.label + " ⚠" : cmd.label;
            if (cmd.isToggle()) {
                String dot = state == null ? "⚪" : (state ? "🟢" : "🔴");
                text = dot + " " + text;
            }
            root.setText(text);
        }

        private void onToggleClick(final CommandRunner runner) {
            final boolean next = !Boolean.TRUE.equals(state);
            state = next;
            render();
            runner.run(cmd.label + (next ? " 开" : " 关"), next ? cmd.onTask : cmd.offTask);
            refreshState(null);
        }

        /** 后台回读状态键并刷新小点（stateKey 为空则保持本地乐观态） */
        private void refreshState(final Runnable after) {
            if (cmd == null || cmd.stateKey == null) { if (after != null) after.run(); return; }
            Sh.submitAsync(() -> {
                Boolean real = readState(cmd.stateKey);
                main.post(() -> {
                    if (real != null) { state = real; render(); }
                    if (after != null) after.run();
                });
            });
        }
    }

    /** 读取开关状态：settings global 键 "1"=开 "0"=关，其它/无值=null（未知） */
    private static Boolean readState(String key) {
        try {
            com.c11.cartool.Sh.Result r = VehicleControl.getWithResult(key, "setting");
            String v = r == null ? "" : r.trim();
            if (v.isEmpty() || "null".equals(v)) return null;
            return "1".equals(v);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
