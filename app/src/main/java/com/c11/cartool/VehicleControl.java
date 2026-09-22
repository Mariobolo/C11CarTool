package com.c11.cartool;

import android.content.Intent;

/**
 * 车辆控制命令封装
 * 基于 c11assistant 项目确认的接口
 *
 * 三层控制模型:
 *   1. Settings.Global - 直接读写系统属性
 *   2. Broadcast - 发送广播控制车辆
 *   3. Logcat - 被动监控车辆状态
 */
public final class VehicleControl {

    // ═══ 广播 Action 常量 ═══
    public static final String ACTION_TO_CAR_CONTROL = "com.leapmotor.speech.tocarcontrol";
    public static final String ACTION_TO_AIR_CONDITIONER = "com.leapmotor.speech.toairconditioner";
    public static final String ACTION_TO_SETTINGS = "com.leapmotor.speech.tosettings";
    public static final String ACTION_TO_AUTONAVI = "com.leapmotor.speech.toautonavi";
    public static final String ACTION_TO_MEDIA = "com.leapmotor.speech.tomedia";
    public static final String ACTION_TO_PHONE = "com.leapmotor.speech.tophone";
    public static final String ACTION_BACK_TO_HOME = "com.leapmotor.speech.backtohome";
    public static final String ACTION_TO_JOURNEY = "com.leapmotor.speech.tojourney";
    public static final String ACTION_TO_DRIVE_RECORD = "com.leapmotor.speech.todriverecord";
    public static final String ACTION_TO_SPEECH = "com.iflytek.autofly.sendToSpeech.message";
    public static final String ACTION_HAND_MESSAGE = "com.iflytek.autofly.handMessage";
    public static final String PACKAGE_SPEECH_SERVICE = "com.leapmotor.leapmotoriflyspeechservice";

    // ═══ 读取 ═══

    public static String get(String key, String ns) {
        return getWithResult(key, ns).trim();
    }

    /**
     * 读取参数（返回完整 Result，包含 exit code/stdout/stderr）
     */
    public static Sh.Result getWithResult(String key, String ns) {
        String cmd;
        switch (ns) {
            case "prop":    cmd = "getprop " + key; break;
            case "setting": cmd = "settings get global " + key; break;
            case "system":  cmd = "settings get system " + key; break;
            default:        cmd = "getprop " + key; break;
        }
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
        return r;
    }

    // ═══ 写入 Settings.Global ═══

    public static void setSetting(String key, String value, String ns) {
        String cmd;
        switch (ns) {
            case "global": cmd = "settings put global " + key + " " + value; break;
            case "system": cmd = "settings put system " + key + " " + value; break;
            default:       cmd = "settings put global " + key + " " + value; break;
        }
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
    }

    // ═══ 广播控制 (已确认可用) ═══

    /**
     * 发送广播 (int extra)
     */
    public static void broadcastInt(String action, String extraName, int value) {
        String cmd = "am broadcast -a " + action + " --ei " + extraName + " " + value;
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
    }

    /**
     * 发送广播 (string extra)
     */
    public static void broadcastString(String action, String extraName, String value) {
        String cmd = "am broadcast -a " + action + " --es " + extraName + " \"" + value + "\"";
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
    }

    /**
     * 发送广播 (无 extra)
     */
    public static void broadcast(String action) {
        String cmd = "am broadcast -a " + action;
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
    }

    // ═══ 车辆控制广播 (正确协议: type + state) ═══

    /**
     * 发送车辆控制广播 —— 零跑车机标准协议
     *
     * 协议格式 (参考 vehicle_control_interfaces.md 第 4 节):
     *   am broadcast -a <action> --es type "<type>" --ei state <state>
     *
     * 接收方根据 type 分发控制类型，根据 state 执行开关/模式切换。
     * 注意: 旧版代码错误地将 type 值当作 extra 名称，导致控制全部失效。
     *
     * @param action 广播 Action，如 ACTION_TO_CAR_CONTROL
     * @param type   控制类型名称，如 "CARLIGHT_JINGUANG"
     * @param state  状态值，如 1=开/0=关，或模式编号
     */
    public static void broadcastControl(String action, String type, int state) {
        String cmd = "am broadcast -a " + action
                + " --es type \"" + type + "\""
                + " --ei state " + state;
        Sh.Result r = Sh.run(cmd);
        Logger.cmd(cmd, r);
    }

    // ═══ 灯光控制 ═══

    public static void setLowBeam(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "CARLIGHT_JINGUANG", on ? 1 : 0);
    }

    public static void setRearFog(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "CARLIGHT_REARFOGCTL", on ? 1 : 0);
    }

    public static void setPositionLight(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "CARLIGHT_SHEKUODENG", on ? 1 : 0);
    }

    public static void setPedestriansAlert(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "PEDESTRIANS_ALERT", on ? 1 : 0);
    }

    // ═══ 驾驶模式 ═══

    /** 0=舒适 1=运动 2=自定义 3=极致 4=经济 */
    public static void setDriverMode(int mode) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "MMI_DRIVER_MODE_SET", mode);
    }

    // ═══ 场景模式 ═══

    public static void setGuardMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "GUARD_MODE", on ? 1 : 0);
    }

    public static void setRestMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "REST_MODE", on ? 1 : 0);
    }

    public static void setCampingMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "CAMPING_MODE", on ? 1 : 0);
    }

    public static void setPowerSaveMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "POWER_SAVE_MODE", on ? 1 : 0);
    }

    public static void setSentinelMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "SENTINEL_MODE", on ? 1 : 0);
    }

    public static void setExperienceMode(boolean on) {
        broadcastControl(ACTION_TO_CAR_CONTROL, "EXPERIENCE_MODE", on ? 1 : 0);
    }

    // ═══ 空调 ═══

    public static void setAcMax(boolean on) {
        broadcastControl(ACTION_TO_AIR_CONDITIONER, "HVACACMAXREQ", on ? 1 : 0);
    }

    // ═══ 系统设置 ═══

    public static void setWifi(boolean on) {
        broadcastControl(ACTION_TO_SETTINGS, "wifi", on ? 1 : 0);
    }

    public static void setBluetooth(boolean on) {
        broadcastControl(ACTION_TO_SETTINGS, "bluetooth", on ? 1 : 0);
    }

    public static void setDayNightMode(boolean dayMode) {
        broadcastControl(ACTION_TO_SETTINGS, "mode", dayMode ? 1 : 0);
    }

    // ═══ 儿童锁 (JSON 协议) ═══

    public static void setChildLock(boolean open) {
        String operation = open ? "OPEN" : "CLOSE";
        String json = "{\"semantic\":{\"name\":\"儿童锁\",\"operation\":\"" + operation
                + "\",\"service\":\"CAR_CONTROL\"},\"focus\":\"carControl\","
                + "\"messageType\":\"REQUEST\",\"needResponse\":\"YES\","
                + "\"operationApp\":\"speech\",\"protocolId\":0,"
                + "\"requestCode\":\"10039\",\"statusCode\":0,\"version\":\"v1.0\"}";
        String cmd = "am broadcast -a " + ACTION_HAND_MESSAGE
                + " -p " + PACKAGE_SPEECH_SERVICE
                + " --es value '" + json + "'";
        Sh.Result r = Sh.run(cmd);
        Logger.cmd("儿童锁 " + operation, r);
    }

    // ═══ TTS 语音 ═══

    public static void speak(String text) {
        String cmd = "am startservice"
                + " -n com.iflytek.cutefly.speechclient.hmi/com.iflytek.autofly.voicecoreservice.tts.TtsService"
                + " --es operation PLAY"
                + " --es text \"" + text + "\""
                + " --es package leap"
                + " --es priority high"
                + " --ei streamType 3";
        Sh.Result r = Sh.run(cmd);
        Logger.cmd("TTS: " + text, r);
    }

    // ═══ 导航/媒体/电话 ═══

    public static void openAutonavi() {
        broadcast(ACTION_TO_AUTONAVI);
    }

    public static void openMedia() {
        broadcast(ACTION_TO_MEDIA);
    }

    public static void backToHome() {
        broadcast(ACTION_BACK_TO_HOME);
    }

    // ═══ Logcat 监控 ═══

    public static String getAllLeapProps() {
        return Sh.out("getprop | grep leap.");
    }

    public static String getAllCarSettings() {
        return Sh.out("settings list global | grep -i car");
    }

    public static String pollLogcat() {
        return Sh.out("logcat -d -s C11CarSomeIp:D C11CarXml:D C11AirConditioner:D BleControlService:D AroundService:I | tail -30");
    }

    public static String getLogcat(int lines) {
        return Sh.out("logcat -d -t " + lines);
    }

    public static void clearLogcat() {
        Sh.run("logcat -c");
    }
}
