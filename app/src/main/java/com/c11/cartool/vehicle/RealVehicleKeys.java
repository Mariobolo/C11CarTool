package com.c11.cartool.vehicle;

/**
 * 真机实证的车控 / 车状态 settings global 键。
 *
 * <p>来源：一键全测「环境全量采集」(c11_env) 的 {@code settings list global}，
 * 每个键都在零跑 C11 真机上真实存在，且 ADB shell(uid=2000) 可 {@code settings get} 读取、
 * 部分可 {@code settings put} 写入并被系统服务 observe 执行（如 strCar100006 已证实可打开空调界面）。
 *
 * <p><b>与 {@link VehicleParams} 严格分开</b>：VehicleParams 多为推测的 leap.* 键（真机多为空），
 * 本类只收录真机实证键，作为实时车状态面板与 settings 直控的权威来源。
 *
 * <p>取值说明中标注「待标定」者，表示键已确认存在、但数值语义需在真机对照确认，代码不臆测。
 */
public final class RealVehicleKeys {

    private RealVehicleKeys() {}

    /** 命名空间固定为 global。每行：{settings 键, 中文名, 取值说明} */
    public static final String[][] KEYS = {
        // ── 空调座舱 ──
        {"strCarAirSwitch",      "空调开关",        "0关 1开"},
        {"strCarAirWind",        "空调风量",        "0-7档"},
        {"strCarAirStatus",      "空调运行模式",    "待标定"},
        {"strCarAirInner",       "内外循环",        "0外循环 1内循环(待标定)"},
        {"strCarFrontDefrost",   "前除霜(除雾)",    "0关 1开"},
        {"strCarRearDefrost",    "后除霜",          "0关 1开"},
        {"strCar1409",           "主驾设定温度",    "值/2=℃ (52→26℃,待最终确认)"},
        {"strCar1410",           "副驾设定温度",    "值/2=℃ (52→26℃,待最终确认)"},
        {"strCarAntiColdWindMode", "防冷风模式",    "0关 1开"},
        {"strCarMirrorHeart",    "后视镜加热",      "0关 1开"},
        {"strCarPTCOutTemp",     "PTC出风温度",     "℃"},
        // ── 锁 / 门 / 窗 ──
        {"strCarVehicleLock",    "整车锁",          "待标定(与Rightware state方向核对)"},
        {"strCarChildLock",      "儿童锁",          "0关 1开"},
        {"strCarWindowForbit",   "车窗锁(禁降)",    "0关 1开"},
        {"strCarTrunkState",     "后备箱状态",      "待标定"},
        // ── 灯 / 氛围 ──
        {"strCar1800",           "氛围灯",          "待标定"},
        // ── 连接 / 环境 / 模式 ──
        {"strCarBluetoothStatus","蓝牙状态",        "待标定"},
        {"strCarWifiStatus",     "WiFi状态",        "待标定"},
        {"strCarBleState",       "蓝牙BLE状态",     "待标定"},
        {"strCarPm25",           "车内PM2.5",       ""},
        {"strCarSentinelMode",   "哨兵模式",        "0关 1开"},
        // ── 音量（C11_* 为全局音量键）──
        {"C11_MUSIC",            "媒体音量",        ""},
        {"C11_NAVI",             "导航音量",        ""},
        {"C11_CALL",             "电话音量",        ""},
        {"C11_SPEECH",           "语音音量",        ""},
        {"SPEECH_SPEAK",         "语音播报开关",    "0关 1开"},
    };
}
