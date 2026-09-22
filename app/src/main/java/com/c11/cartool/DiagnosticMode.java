package com.c11.cartool;

import java.util.HashMap;
import java.util.Locale;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * 增强版诊断模式工具（批量优化版）
 *
 * 优化：一次性 getprop / settings list global 获取全部数据，
 * 本地解析，避免 100+ 条独立 ADB 流导致的不稳定。
 *
 * 命令数量：100+ → ~15 条
 */
public final class DiagnosticMode {

    private static final String TAG = "Diagnostic";

    private static android.content.Context appContext;

    /** 设置应用上下文（用于诊断报告导出到文件） */
    public static void setContext(android.content.Context ctx) {
        appContext = ctx != null ? ctx.getApplicationContext() : null;
    }

    public interface ProgressCallback {
        void onProgress(String message);
        void onComplete(String fullReport);
    }

    /**
     * 一次性获取所有系统属性（getprop 不带参数）
     * 格式: [property.name]: [value]
     */
    private static Map<String, String> getAllProps() {
        Map<String, String> map = new HashMap<>();
        Sh.Result r = Sh.run("getprop");
        if (r.out != null) {
            for (String line : r.out.split("\n")) {
                // 格式: [ro.build.version.release]: [9]
                int lb = line.indexOf('[');
                int rb = line.indexOf("]:");
                if (lb >= 0 && rb > lb) {
                    String key = line.substring(lb + 1, rb);
                    String val = "";
                    int vb = line.indexOf("[", rb);
                    int ve = line.lastIndexOf("]");
                    if (vb >= 0 && ve > vb) {
                        val = line.substring(vb + 1, ve);
                    }
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    /**
     * 一次性获取所有全局设置（settings list global）
     * 格式: key=value
     */
    private static Map<String, String> getAllGlobalSettings() {
        Map<String, String> map = new HashMap<>();
        Sh.Result r = Sh.run("settings list global 2>&1");
        if (r.out != null) {
            for (String line : r.out.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    private static String prop(Map<String, String> props, String key) {
        String v = props.get(key);
        return (v == null || v.isEmpty()) ? "(空)" : v;
    }

    private static String setting(Map<String, String> settings, String key) {
        String v = settings.get(key);
        return (v == null || v.isEmpty()) ? "(空)" : v;
    }

    /**
     * 运行完整诊断（在后台线程调用）
     */
    public static String runFullDiagnostic(ProgressCallback cb) {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════════\n");
        report.append("  C11 车控 APP 全面诊断报告\n");
        report.append("  版本: ").append(AppInfo.VERSION).append("\n");
        report.append("  时间: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n");
        report.append("═══════════════════════════════════════════════════════\n\n");

        int total = 15;
        int step = 0;

        // ═══ 预获取：一次性拿全部属性和设置（2条命令）═══
        if (cb != null) cb.onProgress("预获取系统属性...");
        Map<String, String> props = getAllProps();
        Map<String, String> gsettings = getAllGlobalSettings();
        Logger.info(TAG, "预获取完成: " + props.size() + " 个属性, " + gsettings.size() + " 个全局设置");

        // 1. 基本信息（全部从 Map 读取，0 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 基本信息...");
        report.append("【1. 基本信息】\n");
        report.append("  id: ").append(Sh.whoami()).append("\n");
        report.append("  uid: ").append(Sh.uid()).append("\n");
        report.append("  ADB 模式: ").append(Sh.isAdbConnected() ? "✅ 已连接 (shell uid=2000)" : "❌ 未连接 (应用uid)").append("\n");
        report.append("  本地ADB公钥指纹: ").append(Sh.getAdbKeyFingerprint()).append("\n");
        report.append("    （与车机授权弹窗指纹比对：指纹恒定但车机仍每次重弹=车机端不保存密钥）\n");
        report.append("  Android 版本: ").append(prop(props, "ro.build.version.release")).append("\n");
        report.append("  SDK 版本: ").append(prop(props, "ro.build.version.sdk")).append("\n");
        report.append("  构建号: ").append(prop(props, "ro.build.display.id")).append("\n");
        report.append("  产品型号: ").append(prop(props, "ro.product.model")).append("\n");
        report.append("  品牌: ").append(prop(props, "ro.product.brand")).append("\n");
        report.append("  设备: ").append(prop(props, "ro.product.device")).append("\n");
        report.append("  硬件平台: ").append(prop(props, "ro.board.platform")).append("\n");
        report.append("  安全补丁: ").append(prop(props, "ro.build.version.security_patch")).append("\n");
        report.append("  构建类型: ").append(prop(props, "ro.build.type")).append("\n");
        report.append("  签名标签: ").append(prop(props, "ro.build.tags")).append("\n\n");

        // 2. ADB 与网络状态（合并为 2 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " ADB与网络...");
        report.append("【2. ADB 与网络状态】\n");
        report.append("  WiFi ADB 属性: ").append(prop(props, "persist.sys.leap.wifiadb")).append("\n");
        report.append("  adb_enabled: ").append(setting(gsettings, "adb_enabled")).append("\n");
        // 网络信息合并为一条命令（全部网卡，车机以太网为 eth0）
        // 网络信息：IP/MAC/接口用纯 Java 枚举（不走 shell，避免复杂管道在 ADB 复用流上失败）
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                byte[] mac = ni.getHardwareAddress();
                StringBuilder macS = new StringBuilder();
                if (mac != null) for (byte mb : mac) macS.append(String.format("%02x:", mb & 0xff));
                java.util.Enumeration<java.net.InetAddress> ae = ni.getInetAddresses();
                while (ae.hasMoreElements()) {
                    java.net.InetAddress ia = ae.nextElement();
                    if (ia.isLoopbackAddress() || (ia instanceof java.net.Inet6Address)) continue;
                    report.append("  网卡IP: ").append(ni.getName()).append(' ')
                          .append(ia.getHostAddress()).append(" (up=").append(ni.isUp()).append(")\n");
                }
                if (macS.length() > 0)
                    report.append("  网卡MAC: ").append(ni.getName()).append(' ')
                          .append(macS.substring(0, macS.length() - 1)).append("\n");
            }
        } catch (Exception e) {
            report.append("  网卡枚举失败: ").append(e.getMessage()).append("\n");
        }
        // 网关/DNS 用单条简单命令（无管道，ADB 流上稳定）
        Sh.Result route = Sh.run("ip route", 8000);
        if (route.out != null) for (String l : route.out.split("\n"))
            if (l.contains("default")) report.append("  网关: ").append(l.trim()).append("\n");
        Sh.Result dns = Sh.run("getprop net.dns1", 6000);
        if (dns.out != null && !dns.out.trim().isEmpty())
            report.append("  DNS: ").append(dns.out.trim()).append("\n");
        // 端口监听检测（Java Socket 自测，不依赖 shell，避免普通应用权限噪点）
        boolean adbPort = portListening("127.0.0.1", 5555);
        report.append("  5555端口监听(ADB): ").append(adbPort ? "✅ 正在监听" : "❌ 未监听").append("\n");
        int webPort = WebServer.lastStartedPort();
        boolean webUp = webPort > 0 && portListening("127.0.0.1", webPort);
        report.append("  Web端口监听(:").append(webPort > 0 ? String.valueOf(webPort) : "8080")
              .append("): ").append(webUp ? "✅ 正在监听" : "❌ 未监听/未启动").append("\n\n");

        // 3. SELinux 与安全状态（从 Map 读取，1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " SELinux与安全...");
        report.append("【3. SELinux 与安全状态】\n");
        report.append("  SELinux 模式: ").append(prop(props, "ro.build.selinux")).append("\n");
        report.append("  enforce(读节点): ").append(selinuxEnforce()).append("\n");
        if (Sh.isAdbConnected()) {
            Sh.Result getenforce = Sh.run("getenforce 2>&1");
            report.append("  getenforce(ADB): ").append(getenforce.out != null && !getenforce.out.trim().isEmpty() ? getenforce.out.trim() : "(失败)").append("\n");
        } else {
            report.append("  getenforce(ADB): 跳过（未连接 ADB）\n");
        }
        report.append("  dm_verity: ").append(prop(props, "ro.boot.veritymode")).append("\n");
        report.append("  加密状态: ").append(prop(props, "ro.crypto.state")).append("\n");
        report.append("  加密类型: ").append(prop(props, "ro.crypto.type")).append("\n");
        Sh.Result vsomeipCheck = Sh.run("ls /vendor/etc/vsomeip/ 2>&1");
        report.append("  vsomeip 配置访问: ").append((vsomeipCheck.exit == 0 && !vsomeipCheck.out.contains("Permission denied")) ? "✅ 可访问" : "❌ 无权限").append("\n");
        if (vsomeipCheck.out != null && !vsomeipCheck.out.isEmpty()) {
            for (String l : vsomeipCheck.out.split("\n")) report.append("    ").append(l).append("\n");
        }
        report.append("\n");

        // 4. 零跑系统属性全量（从 Map 过滤，0 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 零跑系统属性...");
        report.append("【4. 零跑系统属性全量】\n");
        int leapCount = 0;
        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey();
            if (k.contains("leap") || k.contains("leapmotor")) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    report.append("  ").append(k).append(" = ").append(e.getValue()).append("\n");
                    leapCount++;
                }
            }
        }
        report.append("  (共 ").append(leapCount).append(" 个非空零跑属性)\n\n");

        // 5. 全局设置全量（从 Map 过滤，0 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 全局设置读取...");
        report.append("【5. 全局设置（车辆相关）】\n");
        if (gsettings.isEmpty()) {
            report.append("  ❌ 无法读取全局设置\n");
        } else {
            String[] keywords = {"car", "vehicle", "leap", "ac_", "air", "seat", "light", "window", "door", "tire", "battery", "speed", "range", "odo", "gear", "charge", "nav", "media", "display", "volume", "brightness", "adb", "wifi", "strCar", "hvac"};
            int count = 0;
            for (Map.Entry<String, String> e : gsettings.entrySet()) {
                String lower = e.getKey().toLowerCase();
                for (String kw : keywords) {
                    if (lower.contains(kw)) {
                        report.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
                        count++;
                        break;
                    }
                }
            }
            report.append("  共找到 ").append(count).append(" 条车辆相关设置 (总计 ").append(gsettings.size()).append(" 条)\n");
        }
        report.append("\n");

        // 6. 车辆状态属性逐个测试（从 Map 读取，0 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 车辆状态测试...");
        report.append("【6. 车辆状态属性测试】\n");
        String[][] vehicleKeys = {
            {"vehicle_speed", "车速"}, {"battery_soc", "电量"},
            {"battery_voltage", "电池电压"}, {"battery_current", "电池电流"},
            {"vehicle_range", "续航里程"}, {"vehicle_odo", "总里程"},
            {"tire_pressure_fl", "左前胎压"}, {"tire_pressure_fr", "右前胎压"},
            {"tire_pressure_rl", "左后胎压"}, {"tire_pressure_rr", "右后胎压"},
            {"gear", "档位"}, {"charging_state", "充电状态"},
            {"door_state", "车门状态"}, {"window_state", "车窗状态"},
            {"ac_temperature", "空调温度"}, {"ac_fan_speed", "空调风速"},
            {"ac_mode", "空调模式"}, {"vehicle_power_mode", "电源模式"},
            {"outside_temperature", "室外温度"}, {"inside_temperature", "室内温度"},
            // strCar 系列（零跑实际使用的键名）
            {"strCar1409", "主驾温度(写)"}, {"strCar1410", "副驾温度(写)"}
        };
        for (String[] kv : vehicleKeys) {
            String val = gsettings.get(kv[0]);
            boolean ok = val != null && !val.isEmpty() && !val.equals("null");
            report.append("  ").append(ok ? "✅" : "❌").append(" ").append(kv[1]).append("(").append(kv[0]).append("): ").append(ok ? val : "(空/失败)").append("\n");
        }
        report.append("\n");

        // 7. 零跑系统应用状态（合并为 1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 零跑系统应用...");
        report.append("【7. 零跑系统应用状态】\n");
        Sh.Result pkgList = Sh.run("pm list packages 2>&1 | grep -i leapmotor");
        String installedPkgs = pkgList.out != null ? pkgList.out.toLowerCase() : "";
        String[] leapApps = {
            "com.leapmotor.system", "com.leapmotor.leapmotoriflyspeechservice",
            "com.leapmotor.autonavi", "com.leapmotor.carcontrol",
            "com.leapmotor.mediaclient", "com.leapmotor.appcenter",
            "com.leapmotor.pet", "com.leapmotor.settings", "com.leapmotor.launcher"
        };
        for (String pkg : leapApps) {
            boolean installed = installedPkgs.contains(pkg.toLowerCase());
            report.append("  ").append(pkg).append(": ").append(installed ? "✅ 已安装" : "❌ 未安装").append("\n");
        }
        report.append("\n");

        // 8. 车控广播协议验证（3 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 车控广播验证...");
        report.append("【8. 车控广播协议验证】\n");
        String[] testActions = {
            "com.leapmotor.speech.tocarcontrol",
            "com.leapmotor.speech.toairconditioner",
            "com.leapmotor.speech.tosettings"
        };
        for (String action : testActions) {
            String cmd = "am broadcast -a " + action + " --es type \"diagnostic_test\" --ei state 0 2>&1";
            Sh.Result r = Sh.run(cmd);
            report.append("  Action: ").append(action).append("\n");
            report.append("    exit=").append(r.exit).append(" channel=").append(r.channel == null ? "?" : r.channel).append("\n");
            if (r.out != null && !r.out.isEmpty()) report.append("    out: ").append(r.out.trim()).append("\n");
            if (r.err != null && !r.err.isEmpty()) report.append("    err: ").append(r.err.trim()).append("\n");
        }
        // handMessage 通道自测（语音服务在线性）
        Sh.Result handMsg = Sh.run("am broadcast -a com.iflytek.autofly.handMessage -p com.leapmotor.leapmotoriflyspeechservice --es value '{\"messageType\":\"REQUEST\",\"needResponse\":\"YES\",\"focus\":\"carControl\",\"semantic\":{\"service\":\"CAR_CONTROL\",\"operation\":\"QUERY\"},\"operationApp\":\"speech\",\"protocolId\":0,\"requestCode\":\"10039\",\"statusCode\":0,\"version\":\"v1.0\"}' 2>&1");
        report.append("  handMessage(QUERY) 通道: exit=").append(handMsg.exit)
              .append(handMsg.out != null && handMsg.out.contains("Abort") ? " (被拒)" : "")
              .append("\n");
        if (handMsg.out != null && !handMsg.out.isEmpty()) report.append("    out: ").append(handMsg.out.trim()).append("\n");
        // Rightware 车锁服务自测（仅查服务存在性，不实际触发）
        Sh.Result rwCheck = Sh.run("dumpsys activity services com.rightware.kanzi.c11carcontrol202008 2>&1 | head -5");
        report.append("  Rightware车锁服务: ").append((rwCheck.out != null && rwCheck.out.contains("ServiceRecord")) ? "✅ 服务存在" : "⚠️ 未发现(可能未运行)").append("\n\n");

        // 9. logcat 读取能力（2 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " logcat能力...");
        report.append("【9. logcat 读取能力】\n");
        Sh.Result logcatResult = Sh.run("logcat -d -t 10 2>&1");
        report.append("  exit=").append(logcatResult.exit).append("\n");
        if (logcatResult.out != null && !logcatResult.out.isEmpty()) {
            report.append("  最近10条日志:\n");
            for (String l : logcatResult.out.split("\n")) report.append("    ").append(l).append("\n");
        } else {
            report.append("  ❌ 无法读取系统日志\n");
            report.append("  stderr: ").append(logcatResult.err).append("\n");
        }
        Sh.Result logBuf = Sh.run("logcat -g 2>&1");
        if (logBuf.out != null && !logBuf.out.isEmpty()) {
            report.append("  日志缓冲区:\n");
            for (String l : logBuf.out.split("\n")) report.append("    ").append(l).append("\n");
        }
        // avc denied 检查（仅 ADB 连接时能读到系统日志）
        if (Sh.isAdbConnected()) {
            Sh.Result avc = Sh.run("logcat -d -b events 2>/dev/null | grep -i avc | tail -20; logcat -d 2>/dev/null | grep -iE 'avc:.*denied' | tail -20");
            if (avc.out != null && !avc.out.trim().isEmpty()) {
                report.append("  avc denied 记录:\n");
                for (String l : avc.out.split("\n")) report.append("    ").append(l.trim()).append("\n");
            } else {
                report.append("  avc denied: 未发现（或读不到系统日志）\n");
            }
        } else {
            report.append("  avc denied: 跳过（需 ADB shell 权限）\n");
        }
        report.append("\n");

        // 10. 存储与文件系统（2 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 存储与文件系统...");
        report.append("【10. 存储与文件系统】\n");
        Sh.Result storage = Sh.run("df -h /data /sdcard /system /vendor 2>&1");
        if (storage.out != null) for (String l : storage.out.split("\n")) report.append("  ").append(l).append("\n");
        java.io.File diagDir = Sh.exportDir();
        Sh.Result writeTest = Sh.run("echo test > " + diagDir.getAbsolutePath() + "/c11_diag_write.txt && echo OK && rm " + diagDir.getAbsolutePath() + "/c11_diag_write.txt");
        report.append("  导出目录写入: ").append(writeTest.out != null && writeTest.out.contains("OK") ? "✅ 可写入 " + diagDir.getAbsolutePath() : "❌ 不可写 (" + writeTest.err + ")").append("\n");
        report.append("  APP私有目录: ").append(Sh.out("pwd")).append("\n");
        Sh.Result vendorCheck = Sh.run("ls /vendor/etc/");
        report.append("  /vendor/etc 访问: ").append(vendorCheck.exit == 0 ? "✅ 可访问" : "❌ 无权限").append("\n\n");

        // 11. 进程与服务状态（1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 进程与服务...");
        report.append("【11. 进程与服务状态】\n");
        Sh.Result procs = Sh.run("ps -A");
        int shown = 0;
        if (procs.out != null) {
            for (String l : procs.out.split("\n")) {
                String ll = l.toLowerCase(Locale.ROOT);
                if (ll.contains("leapmotor") || ll.contains("adbd") || ll.contains("surfaceflinger") || ll.contains("system_server")) {
                    report.append("  ").append(l).append("\n");
                    if (++shown >= 15) break;
                }
            }
        }
        boolean adbdRunning = procs.out != null && procs.out.contains("adbd");
        report.append("  adbd 进程: ").append(adbdRunning ? "✅ 运行中" : "❌ 未运行").append("\n\n");

        // 12. 电池与充电（1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 电池与充电...");
        report.append("【12. 电池与充电】\n");
        Sh.Result battery = Sh.run("dumpsys battery");
        if (battery.out != null) {
            int bi = 0;
            for (String l : battery.out.split("\n")) { if (++bi > 20) break; report.append("  ").append(l).append("\n"); }
        }
        report.append("\n");

        // 13. 无障碍服务状态（1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 无障碍服务...");
        report.append("【13. 无障碍服务状态】\n");
        Sh.Result accInfo = Sh.run("settings get secure enabled_accessibility_services 2>&1; settings get secure accessibility_default_on 2>&1");
        if (accInfo.out != null) {
            String[] lines = accInfo.out.split("\n");
            if (lines.length > 0) report.append("  已启用无障碍服务: ").append(lines[0].trim()).append("\n");
            if (lines.length > 1) report.append("  默认开启: ").append(lines[1].trim()).append("\n");
        }
        boolean ourAcc = accInfo.out != null && accInfo.out.contains("com.c11.cartool");
        report.append("  本应用无障碍服务: ").append(ourAcc ? "✅ 已启用" : "❌ 未启用").append("\n\n");

        // 14. 音频与媒体（1 条命令）
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 音频与媒体...");
        report.append("【14. 音频与媒体】\n");
        Sh.Result media = Sh.run("dumpsys media_session");
        int mi = 0;
        if (media.out != null) {
            for (String l : media.out.split("\n")) {
                String ll = l.toLowerCase(Locale.ROOT);
                if (ll.contains("package") || ll.contains("state") || ll.contains("description")) {
                    report.append("  ").append(l.trim()).append("\n");
                    if (++mi >= 10) break;
                }
            }
        }
        if (mi == 0) report.append("  (无活跃媒体会话)\n");
        Sh.Result audio = Sh.run("dumpsys audio");
        int ai = 0;
        if (audio.out != null) {
            for (String l : audio.out.split("\n")) {
                if (l.contains("STREAM_")) { report.append("    ").append(l.trim()).append("\n"); if (++ai >= 8) break; }
            }
        }
        report.append("\n");

        // 15. 权限能力总结与建议
        step++;
        if (cb != null) cb.onProgress(step + "/" + total + " 生成总结...");
        report.append("【15. 权限能力总结与建议】\n");
        int uid = Sh.uid();
        report.append("  当前 uid: ").append(uid).append("\n");
        if (uid == 0) {
            report.append("  权限等级: 🔴 ROOT (uid=0)\n");
        } else if (uid == 2000) {
            report.append("  权限等级: 🟡 SHELL (uid=2000)\n");
            report.append("  能力: 大部分系统命令可用\n");
            report.append("  建议: 尝试讯飞 handMessage 广播进行车控\n");
        } else if (uid >= 10000) {
            report.append("  权限等级: 🔵 普通应用 (uid=").append(uid).append(")\n");
            report.append("  ⚠️ 必须先开启 WiFi ADB，再通过 ADB 连接获取 shell 权限\n");
            report.append("  开启方式: 系统 demo 软件 → 点击 \"turn on adb wifi\" 按钮\n");
        } else {
            report.append("  权限等级: 🟢 系统应用 (uid=").append(uid).append(")\n");
        }
        report.append("\n");
        report.append("  下一步操作建议:\n");
        if (!Sh.isAdbConnected()) {
            report.append("    1. 开启 WiFi ADB（系统 demo 软件）\n");
            report.append("    2. 点击 \"连接本地 adbd\"\n");
            report.append("    3. 连接成功后重新运行诊断\n");
        } else {
            report.append("    1. ADB 已连接，检查车辆属性是否有值\n");
            report.append("    2. 如属性全空，车辆可能未启动（仅通电未点火）\n");
            report.append("    3. 测试讯飞 handMessage 车控广播\n");
            report.append("    4. 导出完整日志供分析\n");
        }

        report.append("\n═══════════════════════════════════════════════════════\n");
        report.append("  诊断完成 — 请将此报告完整复制用于问题分析\n");
        report.append("═══════════════════════════════════════════════════════\n");

        String fullReport = report.toString();
        Logger.info(TAG, "诊断完成，报告长度: " + fullReport.length() + " 字符");

        // 自动导出报告到文件（便于回传，路径显示在日志中）
        try {
            if (appContext != null) {
                java.io.File dir = appContext.getExternalFilesDir(null);
                if (dir == null) dir = appContext.getFilesDir();
                java.io.File out = new java.io.File(dir, "diagnostic_" +
                        new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) + ".txt");
                java.io.FileWriter fw = new java.io.FileWriter(out);
                fw.write(fullReport);
                fw.close();
                Logger.ok(TAG, "诊断报告已导出: " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            Logger.warn(TAG, "诊断报告导出失败: " + e.getMessage());
        }

        if (cb != null) cb.onComplete(fullReport);
        return fullReport;
    }

    /** TCP 端口监听自测（不依赖 shell） */
    private static boolean portListening(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读 /sys/fs/selinux/enforce（应用可读时），失败返回未知 */
    private static String selinuxEnforce() {
        try {
            String v = Sh.readFile("/sys/fs/selinux/enforce");
            if (v == null) return "(不可读)";
            v = v.trim();
            if ("1".equals(v)) return "Enforcing";
            if ("0".equals(v)) return "Permissive";
            return v.isEmpty() ? "(不可读)" : v;
        } catch (Exception e) {
            return "(不可读)";
        }
    }
}
