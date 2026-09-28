package com.c11.cartool.vehicle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.c11.cartool.Logger;
import com.c11.cartool.Sh;

import org.json.JSONObject;

/**
 * 零跑 C11 车控控制器（多通道架构）。
 *
 * 通道分配（基于上机实测 + egoLauncher 反编译分析）：
 * - Rightware Intent: 车锁（已验证有效）
 * - tocarcontrol 旧广播: 灯光（已验证有效）
 * - toairconditioner 旧广播: 空调最大制冷（已验证有效）
 * - handMessage: 空调开关/儿童锁/后备箱/车窗/360（温度·风量·除霜的语音语义在本车失败，已改 settings 直控）
 * - settings global: 温度/风量/前后除霜/内外循环/空调开关（真机 strCar* 键，带回读）
 */
public class VehicleController {

    private static final String TAG = "VehicleCtrl";

    private static final String HAND_MESSAGE_ACTION = "com.iflytek.autofly.handMessage";
    private static final String HAND_MESSAGE_PACKAGE = "com.leapmotor.leapmotoriflyspeechservice";
    private static final String ACTION_TO_CAR_CONTROL = "com.leapmotor.speech.tocarcontrol";
    private static final String ACTION_TO_AIR_CONDITIONER = "com.leapmotor.speech.toairconditioner";
    private static final String RW_PKG = "com.rightware.kanzi.c11carcontrol202008";
    private static final String RW_CLS = "com.rightware.kanzi.c11carcontrol202008.C11CarControl202008";

    private final Context context;

    /** 最近一次 shell 通道执行结果（一键全测读取，含 exit/stdout/stderr/通道）；Intent 通道为 null */
    private static volatile com.c11.cartool.Sh.Result lastResult;
    /** 线程本地镜像：并发命令不串味（一键全测按线程取本次结果） */
    private static final ThreadLocal<com.c11.cartool.Sh.Result> lastResultTL = new ThreadLocal<>();
    public static com.c11.cartool.Sh.Result lastResult() { return lastResult; }
    /** 当前线程最近一次 shell 结果（供 WorkbenchTest 判定，免受其他线程命令干扰） */
    public static com.c11.cartool.Sh.Result lastResultOfThread() { return lastResultTL.get(); }
    private static void setLastResult(com.c11.cartool.Sh.Result r) {
        lastResult = r;
        lastResultTL.set(r);
    }

    public VehicleController(Context ctx) {
        this.context = ctx != null ? ctx.getApplicationContext() : null;
    }

    // ═══ 整车锁 — Rightware Kanzi 服务（原车 BottomBar 同款通道）═══
    // 逆向依据：SystemUI CarLockItemController.changeCarLockStatus() → RouterUtil.setCarLock(ctx, 0/1)
    //   → startForegroundService 到 com.rightware.kanzi.c11carcontrol202008/.C11CarControl202008，
    //   extras type="VehicleLock"，state 与原车一致（0=解锁、1=闭锁，埋点 dock_bar_car_lock 可证）。
    // 早期失败原因：误用 type="vehicle_lock"，服务不识别，故只显示成功而实车不动。
    public boolean lockCar()   { return sendRightware("VehicleLock", "1"); }
    public boolean unlockCar() { return sendRightware("VehicleLock", "0"); }
    /** settings 整车锁回写（备用/对照通道：shell 直写实车不动作，仅用于回读） */
    public boolean lockCarSettings(boolean lock) { return putGlobal("strCarVehicleLock", lock ? "1" : "0"); }

    // ═══ 灯光 — tocarcontrol 旧广播（已验证）═══
    public boolean lowBeamOn()    { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_JINGUANG", 1); }
    public boolean lowBeamOff()   { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_JINGUANG", 0); }
    public boolean fogLightOn()   { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_REARFOGCTL", 1); }
    public boolean fogLightOff()  { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_REARFOGCTL", 0); }
    public boolean positionLightOn()  { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_SHEKUODENG", 1); }
    public boolean positionLightOff() { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_SHEKUODENG", 0); }
    // 远光（CarHeadUtils CARLIGHT_HIGHTCTRL，注意原车拼写为 HIGHT）
    public boolean highBeamOn()    { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_HIGHTCTRL", 1); }
    public boolean highBeamOff()   { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_HIGHTCTRL", 0); }
    // 自动灯光（CARLIGHT_AURO，state=2=AUTO）
    public boolean autoLights()    { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_AURO", 2); }
    // 关闭全部灯光（CARLIGHT_CLOSE，state=0）
    public boolean closeAllLights(){ return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_CLOSE", 0); }
    // 前雾灯（CARLIGHT_FRONTFOGCTL）
    public boolean frontFogOn()    { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_FRONTFOGCTL", 1); }
    public boolean frontFogOff()   { return sendLegacy(ACTION_TO_CAR_CONTROL, "CARLIGHT_FRONTFOGCTL", 0); }
    /** 阅读灯/氛围灯旧广播通道（CARLIGHT_*DOMELAMPCTRL，state 0关 1开；type 为逆向猜测串） */
    public boolean domeLight(String type, boolean on) {
        return sendLegacy(ACTION_TO_CAR_CONTROL, type, on ? 1 : 0);
    }

    /**
     * 阅读灯（新主通道）。
     * [FIX-20260928] 用户反馈旧广播无效，改走 handMessage 语音家族（已验证家族，
     * 实体名与真机事件名一致：前阅读灯/前左阅读灯/前右阅读灯/后左阅读灯/后右阅读灯，
     * 见 LogcatVehicleSource eventId 1007-1012）；失败再退回旧广播兜底。
     */
    public boolean readingLight(String name, boolean on) {
        boolean ok = sendVoice("carControl", obj()
                .put("operation", on ? "OPEN" : "CLOSE").put("name", name));
        if (!ok) {
            // 旧广播兜底（type 猜测串，保留供对照）
            String opcode = "CARLIGHT_" + READING_LIGHT_OPCODE.get(name);
            if (READING_LIGHT_OPCODE.get(name) != null)
                ok = sendLegacy(ACTION_TO_CAR_CONTROL, opcode, on ? 1 : 0);
        }
        return ok;
    }

    private static final java.util.Map<String, String> READING_LIGHT_OPCODE =
            new java.util.HashMap<String, String>();
    static {
        READING_LIGHT_OPCODE.put("前左阅读灯", "FLDOMELAMPCTRL");
        READING_LIGHT_OPCODE.put("前右阅读灯", "FRDOMELAMPCTRL");
        READING_LIGHT_OPCODE.put("后左阅读灯", "RLDOMELAMPCTRL");
        READING_LIGHT_OPCODE.put("后右阅读灯", "RRDOMELAMPCTRL");
        READING_LIGHT_OPCODE.put("前阅读灯", "FDDOMELAMPCTRL");
        READING_LIGHT_OPCODE.put("后阅读灯", "RDDOMELAMPCTRL");
    }

    // ═══ 空调 — 混合通道 ═══
    public boolean acMaxOn()  { return sendLegacy(ACTION_TO_AIR_CONDITIONER, "HVACACMAXREQ", 1); }
    public boolean acMaxOff() { return sendLegacy(ACTION_TO_AIR_CONDITIONER, "HVACACMAXREQ", 0); }
    // 空调开/关：讯飞 handMessage 已验证有效，保留语音通道
    public boolean acOn()  { return sendVoice("airControl", obj().put("operation", "OPEN").put("name", "空调")); }
    public boolean acOff() { return sendVoice("airControl", obj().put("operation", "CLOSE").put("name", "空调")); }

    // ── 温度/风量/除霜：讯飞语义在本车解析失败（车机埋点记“异常”），改走 settings global 真机键直控 ──
    private static final String K_AC_SWITCH = "strCarAirSwitch";
    private static final String K_FAN = "strCarAirWind";
    private static final String K_AIR_INNER = "strCarAirInner";
    private static final String K_FRONT_DEFROST = "strCarFrontDefrost";
    private static final String K_REAR_DEFROST = "strCarRearDefrost";
    private static final String K_TEMP_DRIVER = "strCar1409";
    private static final String K_TEMP_PASSENGER = "strCar1410";

    /** 通用写键（实验性通道：供仪表盘未单独封装的开关，如 strCarMirrorHeart/strCarWindowForbit） */
    public boolean setGlobalKey(String key, String value) {
        return putGlobal(key, value);
    }

    /** [SECURITY] Shell 参数安全过滤 */
    private static String sanitizeShellArg(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[;&|`$(){}<>\\\\\\n\\r\\t\\x00-\\x1f]", "");
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200);
        return cleaned;
    }

    /** settings put global 后回读校验；回读与写入一致才返回 true */
    private boolean putGlobal(String key, String value) {
        key = sanitizeShellArg(key);
        value = sanitizeShellArg(value);
        Sh.Result w = Sh.run("settings put global " + key + " " + value, 8000);
        // [FIX-20260927] 回读带重试：车机镜像/写入落地存在时延，写后立即回读会误报失败
        // （原实现声称“读取自带延迟”但实际没有任何等待）。
        // 最多回读 3 次（间隔 250/500ms），任一次一致即判成功；写入本身带 timeout 判定（w.ok()）。
        String rb = "";
        boolean match = false;
        Sh.Result g = null;
        for (int i = 0; i < 3 && !match; i++) {
            if (i > 0) {
                try { Thread.sleep(250L * i); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            g = Sh.run("settings get global " + key, 6000);
            rb = g == null || g.out == null ? "" : g.out.trim();
            match = w != null && w.ok() && rb.equals(value);
        }
        setLastResult(g);
        Logger.cmd("settings put global " + key + "=" + value + " → 回读=" + rb + (match ? " ✅" : " ❌"),
                   g != null ? g : w);
        return match;
    }

    public boolean acSwitchOn()  { return putGlobal(K_AC_SWITCH, "1"); }
    public boolean acSwitchOff() { return putGlobal(K_AC_SWITCH, "0"); }

    /** 设定温度(℃)：真机 strCar1409/1410=52 对应 26℃，按 0.5℃/单位写入（编码待真机最终确认，回读可见实际值） */
    public boolean setAcTemperature(int temp) {
        temp = clampTemp(temp);
        String v = String.valueOf(temp * 2);
        putGlobal(K_AC_SWITCH, "1"); // 调温前确保空调开启
        boolean a = putGlobal(K_TEMP_DRIVER, v);
        boolean b = putGlobal(K_TEMP_PASSENGER, v);
        return a && b;
    }

    /** 仅主驾温度（仪表盘分区分步进用） */
    public boolean setAcTemperatureDriver(int temp) {
        temp = clampTemp(temp);
        putGlobal(K_AC_SWITCH, "1");
        return putGlobal(K_TEMP_DRIVER, String.valueOf(temp * 2));
    }

    /** 仅副驾温度（仪表盘分区分步进用） */
    public boolean setAcTemperaturePassenger(int temp) {
        temp = clampTemp(temp);
        putGlobal(K_AC_SWITCH, "1");
        return putGlobal(K_TEMP_PASSENGER, String.valueOf(temp * 2));
    }

    private static int clampTemp(int temp) {
        return Math.max(16, Math.min(32, temp));
    }

    /** 设定风量档位 1-7 */
    public boolean setAcFanSpeed(int level) {
        level = Math.max(1, Math.min(7, level));
        putGlobal(K_AC_SWITCH, "1");
        return putGlobal(K_FAN, String.valueOf(level));
    }

    public boolean setAirInnerLoop(boolean inner) { return putGlobal(K_AIR_INNER, inner ? "1" : "0"); }

    // [FIX-20260928] 内外循环补自动模式：0=外循环 1=内循环 2=自动（自动值待实车回读确认）
    public static final int LOOP_OUT = 0, LOOP_IN = 1, LOOP_AUTO = 2;
    public boolean setAirInnerLoopMode(int mode) {
        if (mode < 0 || mode > 2) return false;
        return putGlobal(K_AIR_INNER, String.valueOf(mode));
    }
    public boolean frontDefrostOn()  { return putGlobal(K_FRONT_DEFROST, "1"); }
    public boolean frontDefrostOff() { return putGlobal(K_FRONT_DEFROST, "0"); }
    public boolean rearDefrostOn()   { return putGlobal(K_REAR_DEFROST, "1"); }
    public boolean rearDefrostOff()  { return putGlobal(K_REAR_DEFROST, "0"); }

    // ═══ 其它 settings 真机键直控 ═══
    public boolean mirrorHeatOn()    { return putGlobal("strCarMirrorHeart", "1"); }
    public boolean mirrorHeatOff()   { return putGlobal("strCarMirrorHeart", "0"); }
    public boolean windowForbitOn()  { return putGlobal("strCarWindowForbit", "1"); }
    public boolean windowForbitOff() { return putGlobal("strCarWindowForbit", "0"); }
    // [FIX-20260928] 空调界面：strCar100006 只有值变化才触发 HMI 开页（边缘触发），
    // 原实现直接写 1，键已是 1 时无响应（用户反馈“空调界面无响应”即此因）。
    public boolean openAcPage() {
        Sh.run("settings put global strCar100006 0", 6000);
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return putGlobal("strCar100006", "1");
    }

    /**
     * 空调运行模式（strCarAirStatus）。
     * [FIX-20260928] 用户实测映射：0=自动 1=制冷 2=制热 3=制冷（3 或为强冷/除湿，待实车确认）。
     */
    public static final int AIR_MODE_AUTO = 0, AIR_MODE_COOL = 1, AIR_MODE_HEAT = 2, AIR_MODE_COOL2 = 3;
    public boolean setAirMode(int mode) { return putGlobal("strCarAirStatus", String.valueOf(mode)); }
    /** 兼容旧名（raw 写入） */
    public boolean setAirStatusRaw(int mode) { return setAirMode(mode); }

    // ═══ 儿童锁 — handMessage（开/关成对，格式已验证）═══
    public boolean leftChildLockOn()  { return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "左边儿童锁")); }
    public boolean rightChildLockOn() { return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "右边儿童锁")); }
    public boolean leftChildLockOff()  { return sendVoice("carControl", obj().put("operation", "CLOSE").put("name", "左边儿童锁")); }
    public boolean rightChildLockOff() { return sendVoice("carControl", obj().put("operation", "CLOSE").put("name", "右边儿童锁")); }

    // ═══ 后备箱 — handMessage ═══
    public boolean openTrunk()  { return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "后备箱")); }
    public boolean closeTrunk() { return sendVoice("carControl", obj().put("operation", "CLOSE").put("name", "后备箱")); }

    // ═══ 车窗 — handMessage ═══
    public boolean setWindow(String area, int percent) {
        percent = Math.max(0, Math.min(100, percent));
        String name;
        switch (area) {
            case "front_left":  name = "主驾车窗"; break;
            case "front_right": name = "副驾车窗"; break;
            case "rear_left":   name = "左后车窗"; break;
            case "rear_right":  name = "右后车窗"; break;
            default: return false;
        }
        return sendVoice("carControl", obj()
                .put("operation", "SET").put("name", name)
                .put("nameValue", percent + "%").put("targetScope", "vehicle"));
    }

    /** 按讯飞车窗名直接设定开度（仪表盘车窗档位卡用，名字→区域映射归车辆层） */
    public boolean setWindowByName(String voiceName, int percent) {
        String area;
        switch (voiceName) {
            case "主驾车窗": area = "front_left";  break;
            case "副驾车窗": area = "front_right"; break;
            case "左后车窗": area = "rear_left";   break;
            case "右后车窗": area = "rear_right";  break;
            default: return false;
        }
        return setWindow(area, percent);
    }

    // ═══ 360 全景 — handMessage ═══
    public boolean open360View() {
        return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "360"));
    }

    // ═══ 语音释放：释放语音助手对麦克风的占用（stopvr），便于其它应用录音 ═══
    private static final String VOICE_RELEASE_PKG = "com.iflytek.cutefly.speechclient.hmi";
    private static final String VOICE_RELEASE_CLS = "com.iflytek.auto.speechclient.sdk.SpeechClientService";

    public boolean releaseVoice() {
        try {
            Log.i(TAG, "语音释放 stopvr");
            if (Sh.isAdbConnected()) {
                String cmd = "am startservice -n " + VOICE_RELEASE_PKG + "/" + VOICE_RELEASE_CLS
                        + " --ez stopvr true";
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0;
            } else {
                Intent i = new Intent();
                i.setComponent(new ComponentName(VOICE_RELEASE_PKG, VOICE_RELEASE_CLS));
                i.putExtra("stopvr", true);
                if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
                else context.startService(i);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "语音释放失败: " + e.getMessage(), e);
            return false;
        }
    }

    // ═══ 场景模式（tocarcontrol 广播：小憩 / 露营 / 省电 / 守护 / 哨兵 / 行人警示，待真机验证）═══
    public boolean scene(String type) {
        return sendLegacy(ACTION_TO_CAR_CONTROL, type, 1);
    }

    /**
     * 座椅/方向盘功能开关（加热/通风/按摩等）。
     * [FIX-20260928] 原 VehicleParams 的 leap.seat.* 虚拟键无硬件消费者（读写均无效），
     * 改走 handMessage 语音家族；实体名 ⚠ 待实车确认（与真机信号命名风格一致）。
     */
    public boolean seatFeature(String name, boolean on) {
        return sendVoice("carControl", obj()
                .put("operation", on ? "OPEN" : "CLOSE").put("name", name));
    }

    /**
     * 后视镜折叠/展开。
     * [FIX-20260928] 原实现 shellSetProp("leap.vehicle.mirror_fold") 必然无效
     * （普通 shell 对 leap.* 虚拟键无硬件消费者）；改走 handMessage 语音家族（已验证）。
     */
    public boolean mirrorFold(boolean fold) {
        return sendVoice("carControl", obj()
                .put("operation", fold ? "CLOSE" : "OPEN").put("name", "后视镜"));
    }

    /**
     * 行人警示音开关。
     * [FIX-20260928] 主通道改 handMessage 语音家族（实体名“行人警示音”）；
     * 失败退回旧 tocarcontrol 广播 type=PEDESTRIANS_ALERT（原实现仅有此猜测串）。
     */
    public boolean pedestrianAlert(boolean on) {
        boolean ok = sendVoice("carControl", obj()
                .put("operation", on ? "OPEN" : "CLOSE").put("name", "行人警示音"));
        if (!ok) ok = sendLegacy(ACTION_TO_CAR_CONTROL, "PEDESTRIANS_ALERT", on ? 1 : 0);
        return ok;
    }

    /** 实验通道：shell setprop（如后视镜折叠 leap.vehicle.mirror_fold；普通 shell 对 leap.* 多为空）。 */
    public boolean shellSetProp(String key, String val) {
        try {
            key = sanitizeShellArg(key);
            val = sanitizeShellArg(val);
            String cmd = "setprop " + key + " " + val;
            Sh.Result r = Sh.run(cmd);
            setLastResult(r);
            Logger.cmd(cmd, r);
            return r.exit == 0;
        } catch (Exception e) {
            Log.e(TAG, "setprop 失败: " + e.getMessage(), e);
            return false;
        }
    }

    /** 媒体按键（input keyevent：85 播放暂停 / 87 下一首 / 88 上一首），播放器降级通道。 */
    public boolean mediaKey(int code) {
        String cmd = "input keyevent " + code;
        Sh.Result r = Sh.run(cmd);
        setLastResult(r);
        Logger.cmd(cmd, r);
        return r.exit == 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  通道实现
    // ═══════════════════════════════════════════════════════════

    /** 旧版语音广播（tocarcontrol / toairconditioner），已验证 */
    private boolean sendLegacy(String action, String type, int state) {
        try {
            action = sanitizeShellArg(action);
            type = sanitizeShellArg(type);
            Log.i(TAG, "旧广播: " + type + "=" + state);
            if (Sh.isAdbConnected()) {
                String cmd = "am broadcast -a " + action + " --es type \"" + type + "\" --ei state " + state;
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0;
            } else {
                Intent intent = new Intent(action);
                intent.putExtra("type", type);
                intent.putExtra("state", state);
                context.sendBroadcast(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "旧广播失败: " + e.getMessage(), e);
            return false;
        }
    }

    /** 讯飞 handMessage，semantic 自动注入 service 字段 */
    private boolean sendVoice(String focus, JsonObj sb) {
        JSONObject semantic = sb.build();
        try {
            semantic.put("service", "carControl".equals(focus) ? "CAR_CONTROL" : "AIR_CONTROL");
            String payload = buildPayload(focus, semantic);
            Log.i(TAG, "handMessage: " + payload);

            if (Sh.isAdbConnected()) {
                String cmd = "am broadcast -a " + HAND_MESSAGE_ACTION
                        + " -p " + HAND_MESSAGE_PACKAGE
                        + " --es value " + shellQuote(payload);
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0 && (r.out == null || !r.out.contains("Abort"));
            } else {
                Intent intent = new Intent(HAND_MESSAGE_ACTION);
                intent.setPackage(HAND_MESSAGE_PACKAGE);
                intent.putExtra("value", payload);
                context.sendBroadcast(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "handMessage失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Rightware Kanzi 车控服务（原车 SystemUI BottomBar 通道，startForegroundService）。
     * @param type  原车驼峰协议，如 VehicleLock
     * @param state 目标状态字符串（VehicleLock：0 解锁 / 1 闭锁）
     */
    private boolean sendRightware(String type, String state) {
        try {
            type = sanitizeShellArg(type);
            state = sanitizeShellArg(state);
            Log.i(TAG, "Rightware: " + type + "=" + state);
            if (Sh.isAdbConnected()) {
                String cls = RW_CLS.substring(RW_CLS.lastIndexOf('.') + 1);
                // 与原车 SystemUI startForegroundService 对齐（Android 9 后台服务策略，startservice 可能不被执行）
                String cmd = "am start-foreground-service -n " + RW_PKG + "/." + cls
                        + " --es type " + type + " --es state " + state;
                Sh.Result r = Sh.run(cmd);
                setLastResult(r);
                Logger.cmd(cmd, r);
                return r.exit == 0;
            } else {
                Intent svc = new Intent();
                svc.setComponent(new ComponentName(RW_PKG, RW_CLS));
                svc.putExtra("type", type);
                svc.putExtra("state", state);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Rightware失败: " + e.getMessage(), e);
            return false;
        }
    }

    private String buildPayload(String focus, JSONObject semantic) {
        try {
            JSONObject p = new JSONObject();
            p.put("semantic", semantic);
            p.put("focus", focus);
            p.put("messageType", "REQUEST");
            p.put("needResponse", "YES");
            p.put("operationApp", "speech");
            p.put("protocolId", 0);
            p.put("requestCode", "10039");
            p.put("statusCode", 0);
            p.put("version", "v1.0");
            return p.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static JsonObj obj() { return new JsonObj(); }

    public static class JsonObj {
        private final JSONObject jo = new JSONObject();
        public JsonObj put(String k, Object v) {
            try { jo.put(k, v); } catch (Exception ignored) {}
            return this;
        }
        public JSONObject build() { return jo; }
    }
}
