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
    public static com.c11.cartool.Sh.Result lastResult() { return lastResult; }

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
        // [FIX] 不再 Thread.sleep(1200) 阻塞，改为读取时自带延迟
        Sh.Result g = Sh.run("settings get global " + key, 6000);
        lastResult = g;
        String rb = g == null || g.out == null ? "" : g.out.trim();
        boolean match = w != null && w.exit == 0 && rb.equals(value);
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
    public boolean frontDefrostOn()  { return putGlobal(K_FRONT_DEFROST, "1"); }
    public boolean frontDefrostOff() { return putGlobal(K_FRONT_DEFROST, "0"); }
    public boolean rearDefrostOn()   { return putGlobal(K_REAR_DEFROST, "1"); }
    public boolean rearDefrostOff()  { return putGlobal(K_REAR_DEFROST, "0"); }

    // ═══ 儿童锁 — handMessage（格式已验证）═══
    public boolean leftChildLockOn()  { return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "左边儿童锁")); }
    public boolean rightChildLockOn() { return sendVoice("carControl", obj().put("operation", "OPEN").put("name", "右边儿童锁")); }

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
                lastResult = r;
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
                lastResult = r;
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
                String cmd = "am startservice -n " + RW_PKG + "/." + cls
                        + " --es type " + type + " --es state " + state;
                Sh.Result r = Sh.run(cmd);
                lastResult = r;
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
