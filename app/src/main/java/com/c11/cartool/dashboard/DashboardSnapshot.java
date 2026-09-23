package com.c11.cartool.dashboard;

/**
 * 仪表盘数据快照（一次采集周期的只读结果）。
 *
 * <p>字段只收录真机实证来源（settings global 真机键 + logcat 解析），
 * 数值语义见 {@code RealVehicleKeys} 与《零跑C11车辆日志解析规范》。
 * 无值一律用 -1 / false / ""，UI 层统一显示 "--"，不写死、不放假数据。
 */
public class DashboardSnapshot {

    /** 本次采集时间（毫秒） */
    public volatile long ts = 0;

    /** ADB 是否连接（未连接则 settings/logcat 均不可采集） */
    public volatile boolean adbConnected = false;
    public volatile int adbUid = -1;

    // ── settings global 真机键（开关/温度/风量等状态量） ──
    public volatile int  acSwitch    = -1;   // strCarAirSwitch 0关1开
    public volatile int  fanSpeed    = -1;   // strCarAirWind 0-7
    public volatile int  innerCycle  = -1;   // strCarAirInner 0外1内(待标定)
    public volatile int  frontDefrost = -1;  // strCarFrontDefrost
    public volatile int  rearDefrost  = -1;  // strCarRearDefrost
    public volatile int  driverTempHalf  = -1; // strCar1409 值/2=℃
    public volatile int  passengerTempHalf = -1; // strCar1410 值/2=℃
    public volatile int  vehicleLock  = -1;  // strCarVehicleLock
    public volatile int  childLock    = -1;  // strCarChildLock
    public volatile int  windowForbit = -1;  // strCarWindowForbit
    public volatile int  mirrorHeat   = -1;  // strCarMirrorHeart
    public volatile int  pm25         = -1;  // strCarPm25
    public volatile int  musicVol     = -1;  // C11_MUSIC
    public volatile int  naviVol      = -1;  // C11_NAVI
    public volatile int  speechVol    = -1;  // C11_SPEECH
    public volatile int  callVol      = -1;  // C11_CALL

    // ── logcat 解析（高频实时量） ──
    public volatile int  batterySoc   = -1;  // figure / eventId 3162 %
    public volatile float voltage     = -1;  // eventId 3130 V
    public volatile float current     = -1;  // eventId 3131 A
    public volatile int  speedKmh     = -1;  // eventId 1108
    public volatile String gear       = "";  // eventId 1110 meaning(R/N/D)
    public volatile int  outsideTemp  = -1;  // eventId 33110 ℃
    public volatile int  rangeStd     = -1;  // C11CarXml rangeStandard
    public volatile int  rangeDyn     = -1;  // C11CarXml rangeDynamic

    /** 四轮胎压 kPa（0=左前,1=右前,2=左后,3=右后），-1 无值 */
    public volatile float[] tirePressKpa = {-1, -1, -1, -1};
    /** 四轮胎温 ℃ */
    public volatile float[] tireTempC = {-1, -1, -1, -1};

    /** 六门状态（0关1开）：左前 右前 左后 右后 后备箱 前机盖 */
    public volatile int[] doorStates = {-1, -1, -1, -1, -1, -1};

    /** 四车窗开度%（0=全关,100=全开）：左前 右前 左后 右后，-1 无值（eventId 21181/21180/21183/21182） */
    public volatile int[] windowPct = {-1, -1, -1, -1};

    /** 上一轮 settings/logcat 是否成功（用于显示数据有效性） */
    public volatile boolean settingsOk = false;
    public volatile boolean logcatOk = false;

    /** 全车信号清单（分组、多渠道标注，每轮采集重新组装，供信号清单页渲染）。 */
    public final java.util.ArrayList<SignalRow> rows =
            new java.util.ArrayList<SignalRow>();

    public boolean hasTs() { return ts > 0; }
}
