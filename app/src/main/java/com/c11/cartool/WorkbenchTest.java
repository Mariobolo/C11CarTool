package com.c11.cartool;

import com.c11.cartool.vehicle.VehicleController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 一键全测引擎（调试期“一次上机拿全部结论”）。
 *
 * 后台线程全自动顺序执行，固定间隔，结束产出一份可导出的完整报告：
 *   阶段0  ADB 连接准备（自动连 127.0.0.1:5555，等待 shell uid=2000）
 *   阶段1  通道能力探测（id / getenforce / settings 读 / settings 列举 / logcat 可读行数）
 *   阶段2  车辆数据快照（遍历 VehicleParams 200+ 项，逐项读取，记录值/状态/通道）
 *   阶段3  车控方案矩阵（VehicleController 真实通道：Rightware / 旧广播 / handMessage，
 *          成对、安全、固定 3s 间隔；可回读项 settings 回读自动判定，其余标注“已投递·待目视”）
 *   阶段4  logcat 回执（车控后抓取系统日志，Java 端过滤关键字，避免 shell 管道）
 *   阶段5  汇总 + 写 /sdcard/c11_workbench_<时间戳>.txt
 *
 * 判定约定：
 *   ✅ 可用        命令成功且（可回读项）回读一致
 *   ⚠️ 已投递·待目视  am 命令 exit=0、无权限异常，需人工确认车辆是否响应（广播类）
 *   ❌ 失败        权限/跨用户拒绝、组件不存在、exit!=0
 *   ⏭️ 跳过        未连接 ADB 或安全原因不自动执行（车窗/后备箱等）
 *
 * 本类只做流程与判定，不直接碰 UI；进度/报告通过回调交给调用方。
 */
public final class WorkbenchTest {

    public interface Callback {
        void onProgress(String message);
        void onFinished(String report, String savedPath);
    }

    /** 车控动作：标题、通道说明、执行体（调用 VehicleController 真实方法） */
    /** 可返回成功标志的车控任务（VehicleController 方法均返回 boolean） */
    private interface BoolTask { boolean run(); }

    private static final class Action {
        final String title;
        final String channel;
        final BoolTask task;
        Action(String title, String channel, BoolTask task) {
            this.title = title; this.channel = channel; this.task = task;
        }
    }

    private static final long ACTION_INTERVAL_MS = 3000;   // 车控动作间隔
    private static final long DATA_INTERVAL_MS   = 120;    // 数据读取间隔
    private static final String TAG = "Workbench";

    private WorkbenchTest() {}

    public static void start(final VehicleController vc, final Callback cb) {
        Thread t = new Thread(() -> {
            try {
                String report = run(vc, cb);
                String path = save(report);
                cb.onFinished(report, path);
            } catch (Exception e) {
                Logger.error(TAG, "一键全测异常", e);
                cb.onFinished("一键全测异常中断: " + e + "\n" + android.util.Log.getStackTraceString(e), null);
            }
        }, "WorkbenchTest");
        t.setDaemon(true);
        t.start();
    }

    private static String run(VehicleController vc, Callback cb) {
        StringBuilder rep = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        rep.append("════════════════════════════════════════════\n");
        rep.append("  C11CarTool 一键全测报告  ").append(AppInfo.VERSION).append('\n');
        rep.append("  时间: ").append(ts).append('\n');
        rep.append("════════════════════════════════════════════\n\n");

        // ── 阶段0：连接准备 ──
        cb.onProgress("阶段0/5：准备 ADB 连接…");
        boolean adb = ensureAdb(cb, rep);

        // ── 阶段1：通道能力探测 ──
        cb.onProgress("阶段1/5：通道能力探测…");
        probeChannels(adb, rep);

        // ── 阶段1B：环境全量采集（真实属性/服务/日志全集，离线标定用）──
        cb.onProgress("阶段1B：环境全量采集（getprop/settings/service/dumpsys/logcat）…");
        dumpEnvironment(adb, rep, cb);

        // ── 阶段2：车辆数据快照 ──
        cb.onProgress("阶段2/5：读取车辆数据（200+ 项，依次填入）…");
        snapshotData(adb, rep, cb);

        // ── 阶段3：车控方案矩阵 ──
        cb.onProgress("阶段3/5：车控方案矩阵（真实通道，成对执行）…");
        runControlMatrix(vc, adb, rep, cb);

        // ── 阶段4：logcat 回执 ──
        cb.onProgress("阶段4/5：抓取 logcat 回执…");
        sleep(2000);
        grabLogcat(adb, rep, cb);

        // ── 阶段5：结尾说明 ──
        rep.append("\n────────────────────────────────────────────\n");
        rep.append("说明：✅=回读/执行确认可用；⚠️=命令已投递需目视车辆是否响应；\n");
        rep.append("      ❌=权限拒绝/组件缺失/执行失败；⏭️=跳过。\n");
        rep.append("      车窗升降、后备箱开合存在夹伤/碰撞风险，未纳入自动测试，\n");
        rep.append("      请在“车控实验”页在有人监护下单项手动验证。\n");
        return rep.toString();
    }

    // ════════════════════════════════════════════════
    //  阶段0
    // ════════════════════════════════════════════════
    private static boolean ensureAdb(Callback cb, StringBuilder rep) {
        rep.append("【0. ADB 连接】\n");
        if (Sh.isAdbConnected() && Sh.getAdbUid() == 2000) {
            rep.append("  已连接 ADB，uid=").append(Sh.getAdbUid()).append("（shell）✅\n\n");
            return true;
        }
        cb.onProgress("未连接，尝试连接 127.0.0.1:5555 …");
        rep.append("  尝试连接 127.0.0.1:5555 …\n");
        boolean ok = false;
        try { ok = Sh.connectAdb("127.0.0.1", 5555, 15000); } catch (Exception e) {
            rep.append("  connectAdb 异常: ").append(e).append('\n');
        }
        // 等待 UID 检测（最多 ~12s）
        for (int i = 0; i < 40; i++) {
            if (Sh.isAdbConnected() && Sh.getAdbUid() == 2000) { ok = true; break; }
            sleep(500);
        }
        int uid = Sh.getAdbUid();
        rep.append("  连接: ").append(Sh.isAdbConnected() ? "已建立" : "未建立")
           .append("，uid=").append(uid)
           .append(ok && uid == 2000 ? "（shell）✅\n\n" : "（非 shell，多数车控将被拒）❌\n\n");
        return ok && uid == 2000;
    }

    // ════════════════════════════════════════════════
    //  阶段1：通道能力
    // ════════════════════════════════════════════════
    private static void probeChannels(boolean adb, StringBuilder rep) {
        rep.append("【1. 通道能力探测】\n");
        if (!adb) {
            rep.append("  未连接 ADB，跳过（普通应用通道将受 MANAGE_USERS / 跨用户限制）。\n\n");
            return;
        }
        cap(rep, "shell 身份", "id");
        cap(rep, "SELinux 模式", "getenforce");
        cap(rep, "settings 读取(strCar100006)", "settings get global strCar100006");
        Sh.Result ls = Sh.run("settings list global", 15000);
        int cnt = ls.out == null ? 0 : ls.out.split("\n").length;
        rep.append("  settings list global : ")
           .append(ls.exit == 0 ? "可列举，约 " + cnt + " 行 ✅" : "失败/受限 ❌")
           .append(ch(ls)).append('\n');
        Sh.Result lc = Sh.run("logcat -d -t 50", 15000);
        int lcnt = lc.out == null ? 0 : lc.out.split("\n").length;
        rep.append("  logcat 系统日志读取  : ")
           .append(lc.exit == 0 && lcnt > 0 ? "可读，约 " + lcnt + " 行 ✅" : "不可读/为空 ❌")
           .append(ch(lc)).append("\n\n");
    }

    private static void cap(StringBuilder rep, String name, String cmd) {
        Sh.Result r = Sh.run(cmd, 10000);
        String v = r.out == null ? "" : r.out.trim();
        boolean ok = r.exit == 0 && v.length() > 0 && !v.toLowerCase().contains("exception");
        rep.append("  ").append(pad(name, 26)).append(": ")
           .append(ok ? "✅ " : "❌ ").append(v.isEmpty() ? "(空)" : v).append(ch(r)).append('\n');
    }

    // ════════════════════════════════════════════════
    //  阶段2：数据快照
    // ════════════════════════════════════════════════
    private static void snapshotData(boolean adb, StringBuilder rep, Callback cb) {
        rep.append("【2. 车辆数据快照】\n");
        if (!adb) {
            rep.append("  未连接 ADB，settings/getprop 多不可读，跳过。\n\n");
            return;
        }
        String[][] params = VehicleParams.PARAMS;
        List<String> got = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        int total = params.length;
        for (int i = 0; i < total; i++) {
            String[] p = params[i];
            String key = p[0], cn = p[1], ns = p[3];
            String cmd;
            if ("prop".equals(ns)) cmd = "getprop " + key;
            else if ("system".equals(ns)) cmd = "settings get system " + key;
            else cmd = "settings get global " + key;
            Sh.Result r = Sh.run(cmd, 6000);
            String v = r.out == null ? "" : r.out.trim();
            if (v.isEmpty() || "null".equals(v)) {
                empty.add(key);
            } else if (v.toLowerCase().contains("exception") || v.toLowerCase().contains("permission")) {
                denied.add(key + "(" + v.replace("\n", " ").trim() + ")");
            } else {
                got.add(pad(cn, 14) + " " + pad(key, 30) + " = " + v);
            }
            if ((i % 25) == 0) cb.onProgress("阶段2/5：数据读取 " + i + "/" + total);
            sleep(DATA_INTERVAL_MS);
        }
        rep.append("  共 ").append(total).append(" 项：读到 ").append(got.size())
           .append("，空/不存在 ").append(empty.size()).append("，权限拒绝 ").append(denied.size()).append("\n\n");
        rep.append("  ── 读到有效值（").append(got.size()).append("）──\n");
        for (String s : got) rep.append("    ").append(s).append('\n');
        if (!denied.isEmpty()) {
            rep.append("\n  ── 权限拒绝（").append(denied.size()).append("）──\n");
            for (String s : denied) rep.append("    ").append(s).append('\n');
        }
        rep.append("\n  ── 空/不存在键（").append(empty.size()).append("，汇总）──\n    ");
        rep.append(String.join(" ", empty)).append("\n\n");

        // 真机实证 settings 键（车控状态镜像，shell uid2000 可读）
        rep.append("  ── 实证车控状态（settings global 真机键）──\n");
        for (String[] k : com.c11.cartool.vehicle.RealVehicleKeys.KEYS) {
            Sh.Result r = Sh.run("settings get global " + k[0], 6000);
            String v = r.out == null ? "" : r.out.trim();
            if (v.isEmpty() || "null".equals(v)) v = "--";
            rep.append("    ").append(pad(k[1], 14)).append(" ").append(pad(k[0], 24))
               .append(" = ").append(v);
            if (k[2] != null && !k[2].isEmpty()) rep.append("   (").append(k[2]).append(")");
            rep.append('\n');
            sleep(DATA_INTERVAL_MS);
        }
        rep.append('\n');
    }

    // ════════════════════════════════════════════════
    //  阶段3：车控矩阵
    // ════════════════════════════════════════════════
    private static void runControlMatrix(VehicleController vc, boolean adb,
                                         StringBuilder rep, Callback cb) {
        rep.append("【3. 车控方案矩阵】\n");
        if (!adb) {
            rep.append("  未连接 ADB（shell uid=2000），车控广播/服务跨用户必被拒，跳过。\n\n");
            return;
        }
        List<Action> actions = buildActions(vc);
        int ok = 0, observe = 0, fail = 0;
        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            cb.onProgress("阶段3/5：车控 " + (i + 1) + "/" + actions.size() + " " + a.title);
            Sh.Result before = VehicleController.lastResult();
            boolean ret = false;
            Exception ex = null;
            try { ret = a.task.run(); } catch (Exception e) { ex = e; }
            Sh.Result r = VehicleController.lastResult();
            if (r == before || r == null) r = before;
            String verdict;
            String detail;
            String out = r == null || r.out == null ? "" : r.out.trim();
            String err = r == null || r.err == null ? "" : r.err.trim();
            if (ex != null) {
                verdict = "❌ 失败"; fail++;
                detail = "异常: " + ex;
            } else if (containsAny(out, "Security exception", "INTERACT_ACROSS_USERS",
                    "Permission Denial", "requires ", "not allowed")) {
                verdict = "❌ 失败"; fail++;
                detail = "权限/跨用户拒绝";
            } else if (containsAny(out, "not found", "Unable to resolve", "does not exist",
                    "Error: didn't")) {
                verdict = "❌ 失败"; fail++;
                detail = "组件/服务不存在";
            } else if (r != null && r.exit == 0
                    && (out.contains("Broadcast completed") || out.contains("Starting service")
                        || out.contains("Service started") || out.isEmpty())) {
                verdict = "⚠️ 已投递·待目视"; observe++;
                detail = out.isEmpty() ? "(无回显)" : firstLine(out);
            } else if (r != null && r.exit == 0) {
                verdict = "⚠️ 已投递·待目视"; observe++;
                detail = firstLine(out);
            } else {
                verdict = "❌ 失败"; fail++;
                detail = "exit=" + (r == null ? "?" : r.exit) + " " + firstLine(err + " " + out);
            }
            rep.append("  ").append(pad(verdict, 16)).append(pad(a.title, 16))
               .append("[").append(a.channel).append("] ").append(detail).append('\n');
            Logger.cmd(a.title + " / " + a.channel, r == null ? new Sh.Result(-1, "", "no result", 0, false) : r);
            sleep(ACTION_INTERVAL_MS);
        }

        // ── settings global 直控回读验证（空调座舱，真机实证键；逐项 put→等待→get→比对）──
        cb.onProgress("阶段3/5：settings 直控回读验证（空调/温度/风量/除霜）…");
        String[][] setTests = {
            {"打开空调界面", "strCar100006", "1"},
            {"空调开关ON",   "strCarAirSwitch", "1"},
            {"主驾温度24℃",  "strCar1409", "48"},
            {"副驾温度24℃",  "strCar1410", "48"},
            {"风量设4档",    "strCarAirWind", "4"},
            {"前除霜ON",     "strCarFrontDefrost", "1"},
            {"后除霜ON",     "strCarRearDefrost", "1"},
            {"内循环ON",     "strCarAirInner", "1"},
        };
        for (String[] t : setTests) {
            String title = t[0], key = t[1], val = t[2];
            Sh.Result pw = Sh.run("settings put global " + key + " " + val, 8000);
            sleep(1300);
            Sh.Result pg = Sh.run("settings get global " + key, 6000);
            String rb = pg == null || pg.out == null ? "" : pg.out.trim();
            boolean match = pw != null && pw.exit == 0 && rb.equals(val);
            rep.append("  ").append(pad(match ? "✅ 回读一致" : "❌ 未生效", 16))
               .append(pad(title, 16)).append("[settings ").append(key).append("] ")
               .append("写入=").append(val).append(" 回读=").append(rb);
            if (!match) rep.append("  ← 请目视是否动作；温度若不符为编码(×2)待标定");
            rep.append('\n');
            if (match) ok++; else fail++;
            sleep(ACTION_INTERVAL_MS);
        }
        // 收尾：关闭前后除霜与空调界面，避免测试后持续除霜/停留在空调页
        Sh.run("settings put global strCarFrontDefrost 0", 8000); sleep(800);
        Sh.run("settings put global strCarRearDefrost 0", 8000); sleep(800);
        Sh.run("settings put global strCar100006 0", 8000); sleep(500);

        rep.append("\n  小计：✅确认 ").append(ok).append("，⚠️已投递待目视 ").append(observe)
           .append("，❌失败 ").append(fail).append("\n\n");
    }

    private static List<Action> buildActions(VehicleController vc) {
        List<Action> l = new ArrayList<>();
        // 旧广播（曾验证）：空调最大制冷 / 灯光，成对
        l.add(new Action("最大制冷ON", "旧广播toairconditioner", vc::acMaxOn));
        l.add(new Action("最大制冷OFF", "旧广播toairconditioner", vc::acMaxOff));
        l.add(new Action("近光灯ON", "旧广播tocarcontrol", vc::lowBeamOn));
        l.add(new Action("近光灯OFF", "旧广播tocarcontrol", vc::lowBeamOff));
        l.add(new Action("示廓灯ON", "旧广播tocarcontrol", vc::positionLightOn));
        l.add(new Action("示廓灯OFF", "旧广播tocarcontrol", vc::positionLightOff));
        l.add(new Action("后雾灯ON", "旧广播tocarcontrol", vc::fogLightOn));
        l.add(new Action("后雾灯OFF", "旧广播tocarcontrol", vc::fogLightOff));
        // handMessage：空调开关/360/儿童锁（已验证有效）。温度·风量·除霜改 settings 直控，见专项回读块
        l.add(new Action("空调ON", "讯飞handMessage", vc::acOn));
        l.add(new Action("空调OFF", "讯飞handMessage", vc::acOff));
        l.add(new Action("360全景", "讯飞handMessage", vc::open360View));
        l.add(new Action("左儿童锁ON", "讯飞handMessage", vc::leftChildLockOn));
        // 整车锁不在一键全测自动执行（安全；Rightware 通道实车不动作，改在「车控实验」人工逐项目视标定）
        return l;
    }

    // ════════════════════════════════════════════════
    //  阶段4：logcat
    // ════════════════════════════════════════════════
    private static void grabLogcat(boolean adb, StringBuilder rep, Callback cb) {
        rep.append("【4. logcat 实时车辆状态解析】\n");
        if (!adb) { rep.append("  未连接 ADB，跳过。\n"); return; }

        // 4A：核心 TAG 全缓冲（设备端 -s 过滤），按《零跑C11车辆日志解析规范》解析实时状态
        StringBuilder tags = new StringBuilder();
        for (String t : com.c11.cartool.vehicle.LogcatVehicleSource.CORE_TAGS) {
            if (tags.length() > 0) tags.append(' ');
            tags.append(t);
        }
        if (cb != null) cb.onProgress("抓取核心 TAG 日志（C11CarSomeIp/Xml/TPMS…）…");
        Sh.Result r = Sh.run("logcat -d -v brief -s " + tags, 30000);
        String text = r.out == null ? "" : r.out;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        java.io.File lf = new java.io.File(Sh.exportDir(), "c11_logcat_tags_" + stamp + ".txt");
        Sh.writeFile(lf.getAbsolutePath(), text);
        if (text.trim().isEmpty()) {
            rep.append("  未抓到核心 TAG 日志（exit=").append(r.exit)
               .append("），请先做几个车控动作再跑一次。\n");
        } else {
            rep.append(com.c11.cartool.vehicle.LogcatVehicleSource.summarize(text));
            rep.append("  原始核心 TAG 日志: ").append(lf.getAbsolutePath()).append('\n');
        }

        // 4B：车控动作回执（最近 2000 行里找广播/服务痕迹）
        if (cb != null) cb.onProgress("抓取车控回执（最近 2000 行）…");
        Sh.Result r2 = Sh.run("logcat -d -v brief -t 2000", 20000);
        rep.append("  ── 车控回执 ──\n");
        if (r2.out == null || r2.out.trim().isEmpty()) {
            rep.append("  未能读取 logcat（exit=").append(r2.exit).append("）。\n\n");
            return;
        }
        String[] kws = {"kanzi", "C11CarControl", "leapmotor.speech", "toairconditioner",
                "tocarcontrol", "iflytek", "handMessage", "strCar", "carcontrol", "C11CarTool"};
        int hit = 0;
        for (String line : r2.out.split("\n")) {
            String low = line.toLowerCase(Locale.ROOT);
            for (String k : kws) if (low.contains(k.toLowerCase(Locale.ROOT))) {
                rep.append("    ").append(line.trim()).append('\n');
                if (++hit >= 60) break;
            }
            if (hit >= 60) break;
        }
        if (hit == 0) rep.append("    最近 2000 行未匹配到车控回执关键字。\n");
        rep.append('\n');
    }

    // ════════════════════════════════════════════════
    //  阶段1B：环境全量采集（一次拿真实属性/车服务/日志全集，替代键名猜测）
    // ════════════════════════════════════════════════
    private static final String[] CAR_KW = {
        "car", "vehicle", "leap", "kanzi", "rightware", "iflytek", "hvac", "aircond",
        "thermal", "cabin", "tire", "door", "battery", "charge", "strcar", "vin",
        "mile", "speed", "power", "seat", "window", "light", "cluster", "meter"
    };

    private static void dumpEnvironment(boolean adb, StringBuilder rep, Callback cb) {
        rep.append("【1B. 环境全量采集】（用于离线标定真实属性键名 / 车服务 / 日志来源）\n");
        if (!adb) { rep.append("  未连接 ADB，跳过（需 shell 权限）。\n\n"); return; }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        java.io.File envFile = new java.io.File(Sh.exportDir(), "c11_env_" + stamp + ".txt");
        StringBuilder env = new StringBuilder(65536);
        env.append("C11CarTool 环境全量采集 ").append(AppInfo.VERSION).append('\n')
           .append("时间 ").append(new Date()).append("\n");

        cb.onProgress("环境采集：getprop / settings / service …");
        Sh.Result rProp = Sh.run("getprop", 20000);
        Sh.Result rSg = Sh.run("settings list global", 20000);
        Sh.Result rSs = Sh.run("settings list system", 20000);
        Sh.Result rSc = Sh.run("settings list secure", 20000);
        Sh.Result rSvc = Sh.run("service list", 20000);
        Sh.Result rDl = Sh.run("dumpsys -l", 20000);
        appendEnv(env, "GETPROP 全量", rProp);
        appendEnv(env, "SETTINGS LIST GLOBAL", rSg);
        appendEnv(env, "SETTINGS LIST SYSTEM", rSs);
        appendEnv(env, "SETTINGS LIST SECURE", rSc);
        appendEnv(env, "SERVICE LIST", rSvc);
        appendEnv(env, "DUMPSYS -L", rDl);

        java.util.LinkedHashSet<String> svcs = pickVehicleServices(
                rSvc.out == null ? "" : rSvc.out, rDl.out == null ? "" : rDl.out);
        env.append("\n==== 候选车服务 DUMPSYS（").append(svcs.size()).append("）====\n");
        int n = 0;
        for (String name : svcs) {
            if (n++ >= 12) { env.append("…(达到 12 个上限)\n"); break; }
            cb.onProgress("环境采集：dumpsys " + name);
            Sh.Result rd = Sh.run("dumpsys " + name, 10000);
            String o = rd.out == null ? "" : rd.out;
            if (o.length() > 10000) o = o.substring(0, 10000) + "\n…(截断)";
            env.append("\n---- dumpsys ").append(name).append(" (exit=").append(rd.exit).append(") ----\n")
               .append(o).append('\n');
        }

        cb.onProgress("环境采集：logcat 最近 3000 行 …");
        Sh.Result rLc = Sh.run("logcat -d -v brief -t 3000", 30000);
        appendEnv(env, "LOGCAT 最近3000行", rLc);

        boolean saved = Sh.writeFile(envFile.getAbsolutePath(), env.toString());

        rep.append("  getprop 全量   : ").append(lines(rProp.out)).append(" 行（车相关 ")
           .append(countMatch(rProp.out)).append("）\n");
        rep.append("  settings global: ").append(lines(rSg.out)).append(" 行；system: ")
           .append(lines(rSs.out)).append("；secure: ").append(lines(rSc.out)).append('\n');
        rep.append("  service list   : ").append(lines(rSvc.out)).append(" 行；候选车服务 ")
           .append(svcs.size()).append(" 个：\n    ").append(String.join(", ", svcs)).append('\n');
        rep.append("  logcat         : ").append(lines(rLc.out)).append(" 行（车相关 ")
           .append(countMatch(rLc.out)).append("）\n");
        rep.append("  原始全量已保存 : ")
           .append(saved ? envFile.getAbsolutePath() : "保存失败（报告结束弹框仍可见摘要）").append('\n');
        rep.append("  建议：上车后做一次开门/转向灯/调空调动作，再跑一次本采集，可抓到动态信号。\n\n");
    }

    private static void appendEnv(StringBuilder env, String title, Sh.Result r) {
        env.append("\n==== ").append(title).append(" (exit=").append(r.exit)
           .append(",").append(lines(r.out)).append("行) ====\n")
           .append(r.out == null ? "" : r.out);
        if (r.err != null && !r.err.isEmpty()) env.append("\n[stderr] ").append(r.err);
        env.append('\n');
    }

    private static int lines(String s) { return s == null ? 0 : s.split("\n").length; }

    private static int countMatch(String text) {
        if (text == null) return 0;
        int cnt = 0;
        for (String line : text.toLowerCase(Locale.ROOT).split("\n")) {
            for (String k : CAR_KW) if (line.contains(k)) { cnt++; break; }
        }
        return cnt;
    }

    private static java.util.LinkedHashSet<String> pickVehicleServices(String a, String b) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9_\\.]{2,40}");
        for (String text : new String[]{a, b}) {
            if (text == null) continue;
            for (String line : text.split("\n")) {
                java.util.regex.Matcher m = p.matcher(line);
                while (m.find()) {
                    String tok = m.group();
                    String low = tok.toLowerCase(Locale.ROOT);
                    for (String k : CAR_KW) {
                        if (low.contains(k)) { set.add(tok); break; }
                    }
                }
            }
        }
        return set;
    }

    // ════════════════════════════════════════════════
    //  导出 / 工具
    // ════════════════════════════════════════════════
    private static String save(String report) {
        String name = "c11_workbench_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
        java.io.File f = new java.io.File(Sh.exportDir(), name);
        String path = f.getAbsolutePath();
        try {
            boolean ok = Sh.writeFile(path, report);
            Logger.ok(TAG, ok ? "全测报告已保存: " + path : "报告保存失败（将在弹框中显示）");
            return ok ? path : null;
        } catch (Exception e) {
            Logger.error(TAG, "报告保存异常", e);
            return null;
        }
    }

    private static String ch(Sh.Result r) {
        if (r == null) return "";
        String c = r.channel;
        return (c == null || c.isEmpty()) ? "" : "  〈" + c + "〉";
    }

    private static boolean containsAny(String s, String... subs) {
        if (s == null) return false;
        String low = s.toLowerCase(Locale.ROOT);
        for (String x : subs) if (low.contains(x.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        s = s.trim();
        int n = s.indexOf('\n');
        s = n > 0 ? s.substring(0, n) : s;
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    private static String pad(String s, int w) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder(s);
        while (visualLen(b.toString()) < w) b.append(' ');
        return b.toString();
    }

    /** 中文按 2 个宽度计，保证等宽对齐 */
    private static int visualLen(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            n += s.charAt(i) > 127 ? 2 : 1;
        }
        return n;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
