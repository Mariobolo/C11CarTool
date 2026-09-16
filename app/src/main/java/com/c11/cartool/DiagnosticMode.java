package com.c11.cartool;

/**
 * 诊断模式工具
 *
 * 一键执行基础命令，显示完整输出，用于排查：
 *   - 当前应用权限等级
 *   - shell 命令执行能力
 *   - 系统属性读取能力
 *   - 全局设置读取能力
 *   - 广播发送能力
 *   - logcat 读取能力
 */
public final class DiagnosticMode {

    private static final String TAG = "Diagnostic";

    public interface ProgressCallback {
        void onProgress(String message);
        void onComplete(String fullReport);
    }

    /**
     * 运行完整诊断（在后台线程调用）
     */
    public static String runFullDiagnostic(ProgressCallback cb) {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════\n");
        report.append("  C11 车控 APP 诊断报告\n");
        report.append("  版本: 0.2.20260916\n");
        report.append("═══════════════════════════════════════════════\n\n");

        // 1. 基本信息
        if (cb != null) cb.onProgress("1/8 检查基本信息...");
        report.append("【1. 基本信息】\n");
        report.append("  id: ").append(Sh.whoami()).append("\n");
        report.append("  uid: ").append(Sh.uid()).append("\n");
        report.append("  getprop ro.build.version.release: ").append(Sh.out("getprop ro.build.version.release")).append("\n");
        report.append("  getprop ro.build.display.id: ").append(Sh.out("getprop ro.build.display.id")).append("\n");
        report.append("  getprop ro.product.model: ").append(Sh.out("getprop ro.product.model")).append("\n");
        report.append("  getprop ro.product.brand: ").append(Sh.out("getprop ro.product.brand")).append("\n\n");

        // 2. 零跑相关属性
        if (cb != null) cb.onProgress("2/8 检查零跑系统属性...");
        report.append("【2. 零跑系统属性】\n");
        String[] leapProps = {
            "persist.sys.leap.wifiadb",
            "persist.sys.leap.vehicle_type",
            "ro.leapmotor.version",
            "ro.leapmotor.model"
        };
        for (String prop : leapProps) {
            String val = Sh.out("getprop " + prop);
            report.append("  ").append(prop).append(": ").append(val.isEmpty() ? "(空)" : val).append("\n");
        }
        report.append("\n");

        // 3. 全局设置读取测试
        if (cb != null) cb.onProgress("3/8 测试全局设置读取...");
        report.append("【3. 全局设置读取测试】\n");
        Sh.Result settingsResult = Sh.run("settings list global 2>&1 | head -20");
        report.append("  settings list global (前20行):\n");
        if (settingsResult.out != null && !settingsResult.out.isEmpty()) {
            for (String line : settingsResult.out.split("\n")) {
                report.append("    ").append(line).append("\n");
            }
        } else {
            report.append("    (空或无权限)\n");
        }
        report.append("  exit=").append(settingsResult.exit).append("\n\n");

        // 4. 车辆相关设置
        if (cb != null) cb.onProgress("4/8 检查车辆相关设置...");
        report.append("【4. 车辆相关设置】\n");
        Sh.Result carSettings = Sh.run("settings list global 2>&1 | grep -i -E 'car|vehicle|leap|ac|air|seat|light|window'");
        if (carSettings.out != null && !carSettings.out.isEmpty()) {
            for (String line : carSettings.out.split("\n")) {
                report.append("  ").append(line).append("\n");
            }
        } else {
            report.append("  (未找到车辆相关设置，或 grep 不可用)\n");
        }
        report.append("\n");

        // 5. 广播发送测试
        if (cb != null) cb.onProgress("5/8 测试广播发送...");
        report.append("【5. 广播发送测试】\n");
        String testBroadcast = "am broadcast -a com.leapmotor.speech.tocarcontrol --es type \"test\" --ei state 0 2>&1";
        Sh.Result bcResult = Sh.run(testBroadcast);
        report.append("  命令: ").append(testBroadcast).append("\n");
        report.append("  exit=").append(bcResult.exit).append("\n");
        report.append("  stdout: ").append(bcResult.out).append("\n");
        report.append("  stderr: ").append(bcResult.err).append("\n");
        if (bcResult.out != null && bcResult.out.contains("Broadcast completed")) {
            report.append("  结论: 广播命令执行成功（但接收方是否响应未知）\n");
        } else {
            report.append("  结论: 广播命令执行失败或无响应\n");
        }
        report.append("\n");

        // 6. logcat 读取测试
        if (cb != null) cb.onProgress("6/8 测试 logcat 读取...");
        report.append("【6. logcat 读取测试】\n");
        Sh.Result logcatResult = Sh.run("logcat -d -t 5 2>&1");
        report.append("  logcat -d -t 5:\n");
        if (logcatResult.out != null && !logcatResult.out.isEmpty()) {
            for (String line : logcatResult.out.split("\n")) {
                report.append("    ").append(line).append("\n");
            }
        } else {
            report.append("    (空或无权限读取系统日志)\n");
        }
        report.append("  exit=").append(logcatResult.exit).append("\n");
        report.append("  注意: Android 4.1+ 普通应用只能读取自己进程的日志\n\n");

        // 7. 网络状态
        if (cb != null) cb.onProgress("7/8 检查网络状态...");
        report.append("【7. 网络状态】\n");
        report.append("  ip addr: ").append(Sh.out("ip addr show wlan0 2>&1 | grep inet")).append("\n");
        report.append("  netcfg: ").append(Sh.out("netcfg 2>&1 | grep wlan0")).append("\n");
        report.append("  getprop dhcp.wlan0.ipaddress: ").append(Sh.out("getprop dhcp.wlan0.ipaddress")).append("\n\n");

        // 8. 权限总结
        if (cb != null) cb.onProgress("8/8 生成权限总结...");
        report.append("【8. 权限能力总结】\n");
        int uid = Sh.uid();
        report.append("  当前 uid: ").append(uid).append("\n");
        if (uid == 0) {
            report.append("  权限等级: ROOT (uid=0)\n");
            report.append("  能力: 全部功能可用\n");
        } else if (uid == 2000) {
            report.append("  权限等级: SHELL (uid=2000)\n");
            report.append("  能力: 大部分系统命令可用，am broadcast 可能被签名权限拒绝\n");
        } else if (uid >= 10000) {
            report.append("  权限等级: 普通应用 (uid=").append(uid).append(")\n");
            report.append("  能力限制:\n");
            report.append("    - getprop: 大部分可读\n");
            report.append("    - settings get: 大部分可读\n");
            report.append("    - settings put: 无权限（需 WRITE_SECURE_SETTINGS）\n");
            report.append("    - am broadcast: 可执行，但接收方可能拒绝\n");
            report.append("    - logcat: 只能读取本应用日志\n");
            report.append("    - setprop: 无权限\n");
            report.append("  建议: 如需更高权限，尝试通过 WiFi ADB 连接获取 shell uid(2000)\n");
        } else {
            report.append("  权限等级: 系统应用 (uid=").append(uid).append(")\n");
        }

        report.append("\n═══════════════════════════════════════════════\n");
        report.append("  诊断完成\n");
        report.append("═══════════════════════════════════════════════\n");

        String fullReport = report.toString();
        Logger.info(TAG, "诊断完成，报告长度: " + fullReport.length() + " 字符");

        if (cb != null) cb.onComplete(fullReport);
        return fullReport;
    }
}
