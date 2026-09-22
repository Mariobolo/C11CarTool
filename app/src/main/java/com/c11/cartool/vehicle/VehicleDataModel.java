package com.c11.cartool.vehicle;

/**
 * 车辆数据模型
 *
 * 集中存储所有车辆状态数据，由 VehicleDataProvider 定时更新，
 * 各模块通过 refreshData(VehicleDataModel) 读取并显示。
 *
 * 设计原则：纯数据容器，无业务逻辑，线程安全（volatile 字段）
 *
 * 注意：部分字段的实际键名需要上机验证后填充，当前为预留字段
 */
public class VehicleDataModel {

    // ═══ 能源数据 ═══

    /** 电池电量百分比（0-100），待上机验证键名 */
    public volatile int batterySoc = -1;

    /** 续航里程（km） */
    public volatile int rangeKm = -1;

    /** 是否正在充电 */
    public volatile boolean isCharging = false;

    /** 充电功率（kW） */
    public volatile float chargePowerKw = 0;

    /** 预计充满时间（分钟） */
    public volatile int chargeFullMinutes = -1;

    // ═══ 行驶数据 ═══

    /** 当前车速（km/h） */
    public volatile int speedKmh = 0;

    /** 总里程（km） */
    public volatile int odometerKm = -1;

    /** 小计里程（km） */
    public volatile float tripDistanceKm = 0;

    /** 档位（P/R/N/D） */
    public volatile String gear = "P";

    /** 驾驶模式（0舒适/1运动/2自定义/3极致/4经济/5领跑） */
    public volatile int driveMode = 0;

    // ═══ 胎压数据 ═══

    /** 左前胎压（bar） */
    public volatile float tirePressureFL = -1;
    /** 右前胎压（bar） */
    public volatile float tirePressureFR = -1;
    /** 左后胎压（bar） */
    public volatile float tirePressureRL = -1;
    /** 右后胎压（bar） */
    public volatile float tirePressureRR = -1;

    /** 左前胎温（℃） */
    public volatile float tireTempFL = -1;
    /** 右前胎温（℃） */
    public volatile float tireTempFR = -1;
    /** 左后胎温（℃） */
    public volatile float tireTempRL = -1;
    /** 右后胎温（℃） */
    public volatile float tireTempRR = -1;

    // ═══ 空调数据 ═══

    /** 主驾温度（℃） */
    public volatile float driverTemp = -1;
    /** 副驾温度（℃） */
    public volatile float passengerTemp = -1;
    /** 风速档位（0-7） */
    public volatile int fanSpeed = 0;
    /** AC 制冷开关 */
    public volatile boolean acOn = false;
    /** 最大制冷 */
    public volatile boolean acMax = false;
    /** 后除霜 */
    public volatile boolean rearDefrost = false;
    /** 内循环（true=内循环，false=外循环） */
    public volatile boolean innerCycle = true;

    // ═══ 灯光数据 ═══

    /** 近光灯 */
    public volatile boolean headlightOn = false;
    /** 示廓灯 */
    public volatile boolean positionLampOn = false;
    /** 后雾灯 */
    public volatile boolean rearFogOn = false;
    /** 氛围灯开关 */
    public volatile boolean ambientLightOn = false;
    /** 氛围灯颜色（0红/1橙/3黄/7绿/10青/14蓝/16紫） */
    public volatile int ambientLightColor = 0;

    // ═══ 车辆状态 ═══

    /** 车辆锁状态（true=已锁） */
    public volatile boolean vehicleLocked = false;
    /** 儿童锁 */
    public volatile boolean childLockOn = false;
    /** 后视镜折叠 */
    public volatile boolean mirrorFolded = false;
    /** 后视镜加热 */
    public volatile boolean mirrorHeatOn = false;
    /** 尾门状态（true=打开） */
    public volatile boolean trunkOpen = false;
    /** 车窗锁 */
    public volatile boolean windowLockOn = false;
    /** 行人警示音 */
    public volatile boolean pedestrianAlertOn = false;

    // ═══ 场景模式 ═══

    public volatile boolean guardModeOn = false;      // 守护模式
    public volatile boolean restModeOn = false;       // 小憩模式
    public volatile boolean experienceModeOn = false;  // 体验模式
    public volatile boolean campingModeOn = false;     // 露营模式
    public volatile boolean powerSaveModeOn = false;   // 省电模式
    public volatile boolean sentinelModeOn = false;    // 哨兵模式

    // ═══ 系统设置 ═══

    public volatile boolean wifiOn = false;
    public volatile boolean bluetoothOn = false;
    public volatile boolean hotspotOn = false;
    public volatile boolean darkModeOn = false;
    public volatile boolean forceChargeOn = false;

    // ═══ 音量数据 ═══

    /** 媒体音量（0-15） */
    public volatile int musicVolume = -1;
    /** 导航音量（0-15） */
    public volatile int naviVolume = -1;
    /** 电话音量（0-15） */
    public volatile int callVolume = -1;

    // ═══ 系统信息 ═══

    /** ADB 是否已连接 */
    public volatile boolean adbConnected = false;
    /** 当前 UID */
    public volatile int currentUid = -1;
    /** Web 服务是否运行 */
    public volatile boolean webServerRunning = false;
    /** 内部 IP 地址 */
    public volatile String internalIp = "";

    // ═══ 数据时间戳 ═══

    /** 最后更新时间（毫秒） */
    public volatile long lastUpdateTime = 0;

    /**
     * 检查数据是否有效（非初始值）
     */
    public boolean isBatteryValid() { return batterySoc >= 0; }
    public boolean isRangeValid() { return rangeKm >= 0; }
    public boolean isSpeedValid() { return speedKmh >= 0; }
    public boolean isOdometerValid() { return odometerKm >= 0; }
    public boolean isTireValid() { return tirePressureFL >= 0; }
    public boolean isTempValid() { return driverTemp >= 0; }
    public boolean isVolumeValid() { return musicVolume >= 0; }

    /**
     * 重置所有数据为初始值
     */
    public void reset() {
        batterySoc = -1;
        rangeKm = -1;
        speedKmh = 0;
        odometerKm = -1;
        gear = "P";
        tirePressureFL = -1;
        tirePressureFR = -1;
        tirePressureRL = -1;
        tirePressureRR = -1;
        driverTemp = -1;
        passengerTemp = -1;
        musicVolume = -1;
        naviVolume = -1;
        callVolume = -1;
        lastUpdateTime = 0;
    }

    @Override
    public String toString() {
        return "VehicleData{" +
            "battery=" + batterySoc + "%" +
            ", range=" + rangeKm + "km" +
            ", speed=" + speedKmh + "km/h" +
            ", gear=" + gear +
            ", charging=" + isCharging +
            ", ac=" + (acOn ? driverTemp + "°" : "off") +
            '}';
    }
}
