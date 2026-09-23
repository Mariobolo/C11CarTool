package com.c11.cartool;

import java.util.ArrayList;
import java.util.List;

/**
 * 车控顺序实验引擎。
 *
 * 设计目标（对应上机反馈）：
 *   - 所有可行方案集中，按顺序逐一执行，自动判断结果
 *   - 每个方案执行前弹框确认，让用户观察实车互动
 *   - 动作间固定间隔（默认 4 秒），避免 adbd 流冲突与响应未完成
 *   - 开关先后成对（先 ON 后 OFF）、依赖前置（如空调先开再设温度）
 *   - 主动查询状态判定（settings/props），不做 logcat 被动分析
 *
 * 本类只定义流程逻辑与步骤清单，UI 交互（确认弹框/进度/汇总）通过回调交给调用方。
 */
public final class CarControlExperiment {

    /** 步骤间固定间隔（毫秒），符合 3-5 秒要求 */
    public static final long STEP_INTERVAL_MS = 4000;

    /** 执行结果 */
    public enum StepResult { SUCCESS, FAILED, NEEDS_OBSERVE, SKIPPED, BLOCKED }

    /** 单个实验步骤 */
    public static final class Step {
        public final String id;
        public final String title;
        public final String channel;      // 使用的通道说明
        public final String confirmText;  // 弹框确认文案（含观察指引）
        public final boolean requiresAdb; // 是否必须 ADB shell 权限
        public final Runnable executor;   // 执行动作（在后台线程）
        public final Verifier verifier;   // 可选：主动查询判定

        public Step(String id, String title, String channel, String confirmText,
                    boolean requiresAdb, Runnable executor, Verifier verifier) {
            this.id = id;
            this.title = title;
            this.channel = channel;
            this.confirmText = confirmText;
            this.requiresAdb = requiresAdb;
            this.executor = executor;
            this.verifier = verifier;
        }
    }

    /** 结果判定器：执行后主动查询，返回 成功/失败/未知(需人工观察) */
    public interface Verifier {
        /** 返回 null 表示无法确认（人工观察）；true=成功；false=失败 */
        Boolean verify();
    }

    /** 流程回调（由 UI 实现） */
    public interface Callback {
        /** 请求确认某步骤；listener.onConfirmed() 执行 / onSkipped() 跳过 */
        void onConfirmRequest(Step step, ConfirmListener listener);
        void onProgress(String message);
        void onStepResult(Step step, StepResult result, String detail);
        void onFinished(String summary);
    }

    public interface ConfirmListener {
        void onConfirmed();
        void onSkipped();
    }

    private CarControlExperiment() {}

    /**
     * 构建默认实验步骤清单（含开关先后与依赖顺序）。
     * @param vc 车控控制器
     */
    public static List<Step> buildDefaultSteps(com.c11.cartool.vehicle.VehicleController vc) {
        List<Step> steps = new ArrayList<>();

        // ══════════ A. 空调座舱（settings global，0921 真机标定 ✅） ══════════
        steps.add(new Step("ac_on", "✅ 空调 ON", "settings global strCarAirSwitch",
                "写入 strCarAirSwitch=1 开启空调，观察出风。", true,
                () -> vc.acSwitchOn(), null));
        steps.add(new Step("ac_temp", "✅ 温度 SET 24°", "settings global strCar1409/1410",
                "主副驾温度写 48（=24℃×2），观察空调温度显示（回读见日志）。", true,
                () -> vc.setAcTemperature(24), null));
        steps.add(new Step("ac_fan", "✅ 风量 SET 4", "settings global strCarAirWind",
                "风量写 4（范围 0-7），观察风量变化（回读见日志）。", true,
                () -> vc.setAcFanSpeed(4), null));
        steps.add(new Step("defrost_f_on", "✅ 前除霜 ON", "settings global strCarFrontDefrost",
                "写入 strCarFrontDefrost=1，观察前挡出风与图标。", true,
                () -> vc.frontDefrostOn(), null));
        steps.add(new Step("defrost_f_off", "✅ 前除霜 OFF 收尾", "settings global strCarFrontDefrost",
                "写入 strCarFrontDefrost=0 收尾，确认除霜关闭。", true,
                () -> vc.frontDefrostOff(), null));
        steps.add(new Step("ac_max_on", "✅ 最大制冷 ON", "旧广播 HVACACMAXREQ",
                "发送最大制冷开启（旧语音广播，已验证），观察空调进入最大制冷。", true,
                () -> vc.acMaxOn(), null));
        steps.add(new Step("ac_max_off", "✅ 最大制冷 OFF", "旧广播 HVACACMAXREQ",
                "关闭最大制冷，验证关断逻辑。", true,
                () -> vc.acMaxOff(), null));
        steps.add(new Step("ac_ui", "✅ 打开空调界面", "settings put global strCar100006",
                "写入 strCar100006=1 打开空调界面，随后查询验证。", true,
                () -> Sh.run("settings put global strCar100006 1"),
                () -> {
                    Sh.Result r = Sh.run("settings get global strCar100006");
                    return "1".equals(r.trim());
                }));
        steps.add(new Step("ac_off", "✅ 空调 OFF 收尾", "settings global strCarAirSwitch",
                "写入 strCarAirSwitch=0 关闭空调，确认停止出风。", true,
                () -> vc.acSwitchOff(), null));

        // ══════════ B. 灯光（旧语音广播，真机验证 ✅） ══════════
        steps.add(new Step("lowbeam_on", "✅ 近光灯 ON", "旧广播 CARLIGHT_JINGUANG",
                "开启近光灯，观察灯光点亮。", true,
                () -> vc.lowBeamOn(), null));
        steps.add(new Step("lowbeam_off", "✅ 近光灯 OFF", "旧广播 CARLIGHT_JINGUANG",
                "关闭近光灯，观察灯光熄灭。", true,
                () -> vc.lowBeamOff(), null));

        // ══════════ C. 车身功能（讯飞 handMessage，真机验证 ✅） ══════════
        steps.add(new Step("childlock_l", "✅ 左儿童锁 ON", "讯飞 handMessage",
                "开启左后儿童锁，观察车机/仪表响应提示。", true,
                () -> vc.leftChildLockOn(), null));
        steps.add(new Step("childlock_r", "✅ 右儿童锁 ON", "讯飞 handMessage",
                "开启右后儿童锁，观察车机/仪表响应提示。", true,
                () -> vc.rightChildLockOn(), null));
        steps.add(new Step("view_360", "✅ 360 全景", "讯飞 handMessage",
                "开启 360 全景影像，观察中控显示。", true,
                () -> vc.open360View(), null));
        steps.add(new Step("trunk_open", "✅ 后备箱开", "讯飞 handMessage",
                "确认车尾无人无物后开启后备箱，观察尾门动作。", true,
                () -> vc.openTrunk(), null));
        steps.add(new Step("trunk_close", "✅ 后备箱关", "讯飞 handMessage",
                "关闭后备箱，观察尾门动作。", true,
                () -> vc.closeTrunk(), null));

        // ══════════ D. 整车锁（Rightware 原车通道，首次上机验证；最终保持解锁） ══════════
        // 人在车内、P 挡、带钥匙；目视两步是否真正落/解锁（type 已由 SystemUI 逆向校正为 VehicleLock）
        steps.add(new Step("lock_rw", "⚠ 整车锁 落锁", "Rightware VehicleLock state=1",
                "【实验性】Rightware 服务 type=VehicleLock、state=1，观察四门是否落锁。", true,
                vc::lockCar, null));
        steps.add(new Step("unlock_rw", "⚠ 整车锁 解锁收尾", "Rightware VehicleLock state=0",
                "【实验性】state=0 收尾，确认车门最终处于解锁状态。", true,
                vc::unlockCar, null));
        // 车窗：handMessage 格式已验证，实车动作尚未标定
        steps.add(new Step("win_fl_up", "⚠ 主驾车窗升", "讯飞 handMessage（实验性）",
                "【实验性】确认车窗无障碍，主驾车窗升到 100%，观察动作。", true,
                () -> vc.setWindow("front_left", 100), null));
        steps.add(new Step("win_fl_down", "⚠ 主驾车窗降", "讯飞 handMessage（实验性）",
                "【实验性】主驾车窗降到 0%，观察车窗动作。", true,
                () -> vc.setWindow("front_left", 0), null));

        return steps;
    }

    /**
     * 在后台线程顺序执行全部步骤。
     * 每个步骤先请求确认，确认后执行、间隔等待、主动判定，再进入下一步。
     */
    public static void runAsync(List<Step> steps, Callback cb) {
        Thread t = new Thread(() -> run(steps, cb), "CarCtrlExperiment");
        t.setDaemon(true);
        t.start();
    }

    private static void run(List<Step> steps, Callback cb) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== 车控顺序实验结果 ===\n");
        int ok = 0, fail = 0, observe = 0, skip = 0, blocked = 0;

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String prefix = "[" + (i + 1) + "/" + steps.size() + "] ";
            cb.onProgress(prefix + "等待确认: " + step.title);

            // 条件执行：依赖 ADB 的步骤在未连接时阻断（明确告知，不静默）
            if (step.requiresAdb && !Sh.isAdbConnected()) {
                StepResult r = StepResult.BLOCKED;
                cb.onStepResult(step, r, "需要 ADB shell 权限，当前未连接");
                summary.append("⛔ ").append(step.title).append(" — 阻断（未连接ADB）\n");
                blocked++;
                continue;
            }

            // 弹框确认（用 CountDownLatch 替代忙等待）
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            ConfirmHolder holder = new ConfirmHolder();
            cb.onConfirmRequest(step, new ConfirmListener() {
                @Override public void onConfirmed() { holder.skip = false; latch.countDown(); }
                @Override public void onSkipped()   { holder.skip = true;  latch.countDown(); }
            });
            try {
                if (!latch.await(5, java.util.concurrent.TimeUnit.MINUTES)) {
                    cb.onStepResult(step, StepResult.SKIPPED, "确认超时（5分钟）");
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (holder.skip) {
                cb.onStepResult(step, StepResult.SKIPPED, "用户跳过");
                summary.append("⏭️ ").append(step.title).append(" — 跳过\n");
                skip++;
                continue;
            }

            // 执行动作
            cb.onProgress(prefix + "执行中: " + step.title);
            String detail;
            StepResult result;
            try {
                step.executor.run();
                // 等待 3-5 秒，让车机处理完成（同时满足开关先后逻辑）
                cb.onProgress(prefix + "等待车机响应 " + (STEP_INTERVAL_MS / 1000) + "s ...");
                try { Thread.sleep(STEP_INTERVAL_MS); } catch (InterruptedException e) { return; }

                // 主动判定：有验证器则查询，无则需人工观察
                if (step.verifier != null) {
                    Boolean v = step.verifier.verify();
                    if (Boolean.TRUE.equals(v)) { result = StepResult.SUCCESS; detail = "主动查询验证通过"; }
                    else if (Boolean.FALSE.equals(v)) { result = StepResult.FAILED; detail = "主动查询验证未达预期"; }
                    else { result = StepResult.NEEDS_OBSERVE; detail = "无法自动判定，请人工确认"; }
                } else {
                    result = StepResult.NEEDS_OBSERVE;
                    detail = "命令已发送，请确认实车是否响应";
                }
            } catch (Exception e) {
                result = StepResult.FAILED;
                detail = "执行异常: " + e.getMessage();
            }

            cb.onStepResult(step, result, detail);
            switch (result) {
                case SUCCESS: summary.append("✅ ").append(step.title).append(" — ").append(detail).append("\n"); ok++; break;
                case FAILED:  summary.append("❌ ").append(step.title).append(" — ").append(detail).append("\n"); fail++; break;
                case NEEDS_OBSERVE: summary.append("👁️ ").append(step.title).append(" — ").append(detail).append("\n"); observe++; break;
                default: break;
            }
        }

        summary.append("\n=== 汇总: 成功 ").append(ok)
               .append(" | 失败 ").append(fail)
               .append(" | 待人工确认 ").append(observe)
               .append(" | 跳过 ").append(skip)
               .append(" | 阻断 ").append(blocked).append(" ===\n");
        cb.onFinished(summary.toString());
    }

    /** 简单确认信号量（等待 UI 线程返回用户选择） */
    private static final class ConfirmHolder {
        volatile boolean skip = false;
    }
}
