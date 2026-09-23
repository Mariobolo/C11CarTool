package com.c11.cartool.dashboard;

import com.c11.cartool.Logger;
import com.c11.cartool.Sh;
import com.c11.cartool.vehicle.LogcatVehicleSource;

/**
 * 仪表盘数据采集器（单一后台线程，5s 一轮）。
 *
 * <p>每个周期执行两条 ADB 命令并解析，聚合为 {@link DashboardSnapshot} 回调主线程：
 * <ul>
 *   <li>{@code settings list global}：一次拿全部真机键，Java 端按白名单解析（不在 ADB 流上跑复杂管道）；</li>
 *   <li>{@code logcat -d -v brief -s <核心TAG>}：交给 {@link LogcatVehicleSource#parse} 解析高频实时信号。</li>
 * </ul>
 *
 * <p>ADB 未连接时不采集（快照标记未连接，UI 引导连接），避免无意义命令。
 */
public class DashboardRepository {

    public interface Callback {
        void onSnapshot(DashboardSnapshot snap);
    }

    private static final String TAG = "DashRepo";
    private static final long INTERVAL_MS = 5000;

    /** 仪表盘每轮抓取的 TAG（与一键全测核心集对齐，去掉无车辆数据的 Wallpaper）。 */
    private static final String[] LOG_TAGS = {
            "C11CarSomeIp", "C11CarXml", "C11AirConditioner", "zza",
            "EnergyDataBinder", "CarControl", "C11CarConf",
            "LocationDataC23Handler", "SomeipSub", "GearMonitorService",
            "UnifiedVehicleDataSvc", "C11SmartContrl"
    };

    private static final java.util.regex.Pattern TEMP_RE =
            java.util.regex.Pattern.compile("([\\d.]+)°C");

    private final Callback callback;
    private final android.os.Handler mainHandler;
    private Thread thread;
    private volatile boolean running = false;

    public DashboardRepository(android.os.Handler main, Callback cb) {
        this.mainHandler = main;
        this.callback = cb;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "dashboard-refresh");
        thread.setDaemon(true);
        thread.start();
        Logger.info(TAG, "仪表盘数据采集启动（5s/轮）");
    }

    public synchronized boolean isRunning() { return running; }

    public synchronized void stop() {
        running = false;
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    public void requestRefresh() {
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                long t0 = System.currentTimeMillis();
                collectOnce();
                long cost = System.currentTimeMillis() - t0;
                long sleep = INTERVAL_MS - cost;
                if (sleep > 0) Thread.sleep(sleep);
            } catch (InterruptedException e) {
                // 收到中断 = 触发一次立即采集（车控后主动刷新）
                if (running) {
                    try { collectOnce(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Logger.warn(TAG, "采集异常: " + e.getMessage());
                try { Thread.sleep(INTERVAL_MS); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void collectOnce() {
        DashboardSnapshot snap = new DashboardSnapshot();
        snap.ts = System.currentTimeMillis();

        if (!Sh.isAdbConnected()) {
            snap.adbConnected = false;
            snap.adbUid = Sh.getAdbUid();
            deliver(snap);
            return;
        }
        snap.adbConnected = true;
        snap.adbUid = Sh.getAdbUid();

        // ── settings list global：一次命令，Java 端解析 �─
        java.util.ArrayList<SignalRow> settingsRows = new java.util.ArrayList<SignalRow>();
        Sh.Result s = Sh.run("settings list global", 10000);
        if (s.ok() && s.out != null) {
            parseSettings(s.out, snap, settingsRows);
            snap.settingsOk = true;
        } else {
            Logger.warn(TAG, "settings list global 失败: " + (s.err == null ? "" : s.err));
            snap.settingsOk = false;
        }

        // ── logcat：核心 TAG 一次性拉取解析 ──
        String tags = join(" ", LOG_TAGS);
        Sh.Result l = Sh.run("logcat -d -v brief -s " + tags, 10000);
        LogcatVehicleSource.Result pr = null;
        if (l.ok() && l.out != null && !l.out.trim().isEmpty()) {
            pr = LogcatVehicleSource.parse(l.out);
            parseLogcat(pr, snap);
            snap.logcatOk = true;
        } else {
            Logger.debug(TAG, "logcat 无输出或失败（缓冲可能已刷掉）");
            snap.logcatOk = false;
        }

        // ── 组装全车信号清单（settings / event / node / TPMS / GPS / 行程，多渠道并列）──
        buildRows(snap, settingsRows, pr);

        deliver(snap);
    }

    /** settings list global 输出 "key=value" 逐行解析（白名单），同时收集 settings 渠道信号行。 */
    private void parseSettings(String text, DashboardSnapshot snap,
                               java.util.ArrayList<SignalRow> settingsRows) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || !t.contains("=")) continue;
            int i = t.indexOf('=');
            String key = t.substring(0, i).trim();
            String val = t.substring(i + 1).trim();
            if (val.isEmpty()) continue;

            // 数字值才驱动卡片；非数字值（字符串状态）不丢弃，仍在信号清单原样展示
            int iv = Integer.MIN_VALUE;
            try { iv = (int) Math.floor(Double.parseDouble(val)); }
            catch (Exception ignored) { }

            if (iv != Integer.MIN_VALUE) {
                switch (key) {
                    case "strCarAirSwitch":     snap.acSwitch = iv; break;
                    case "strCarAirWind":       snap.fanSpeed = iv; break;
                    case "strCarAirInner":      snap.innerCycle = iv; break;
                    case "strCarFrontDefrost":  snap.frontDefrost = iv; break;
                    case "strCarRearDefrost":   snap.rearDefrost = iv; break;
                    case "strCar1409":          snap.driverTempHalf = iv; break;
                    case "strCar1410":          snap.passengerTempHalf = iv; break;
                    case "strCarVehicleLock":   snap.vehicleLock = iv; break;
                    case "strCarChildLock":     snap.childLock = iv; break;
                    case "strCarWindowForbit":  snap.windowForbit = iv; break;
                    case "strCarMirrorHeart":   snap.mirrorHeat = iv; break;
                    case "strCarPm25":          snap.pm25 = iv; break;
                    case "C11_MUSIC":           snap.musicVol = iv; break;
                    case "C11_NAVI":            snap.naviVol = iv; break;
                    case "C11_SPEECH":          snap.speechVol = iv; break;
                    case "C11_CALL":            snap.callVol = iv; break;
                    default: break;
                }
            }

            String[] meta = SETTINGS_META.get(key);
            if (meta != null) {
                String shown = iv != Integer.MIN_VALUE
                        ? formatSettings(meta[2], key, iv) : val;
                settingsRows.add(new SignalRow(
                        meta[0], meta[1], shown, "settings/" + key));
            }
        }
    }

    // ─────────────── 信号清单组装（多渠道并列 + 分组排序）───────────────

    /** settings 键元数据：{分组, 中文名, 类型(switch/temp/fan/vol/pm)}。 */
    private static final java.util.LinkedHashMap<String, String[]> SETTINGS_META =
            new java.util.LinkedHashMap<String, String[]>();
    static {
        Object[][] m = {
            {"strCarAirSwitch",     "空调", "空调开关", "switch"},
            {"strCarAirWind",       "空调", "空调风量", "fan"},
            {"strCarAirInner",      "空调", "空调循环", "switch"},
            {"strCarFrontDefrost",  "空调", "前除霜",   "switch"},
            {"strCarRearDefrost",   "空调", "后除霜",   "switch"},
            {"strCar1409",          "空调", "左区温度", "temp"},
            {"strCar1410",          "空调", "右区温度", "temp"},
            {"strCarVehicleLock",   "车门/车锁/车窗", "整车锁", "switch"},
            {"strCarChildLock",     "车门/车锁/车窗", "儿童锁", "switch"},
            {"strCarWindowForbit",  "车门/车锁/车窗", "车窗锁", "switch"},
            {"strCarMirrorHeart",   "车门/车锁/车窗", "后视镜加热", "switch"},
            {"strCarPm25",          "系统/能耗", "车内PM2.5", "pm"},
            {"C11_MUSIC",           "系统/能耗", "媒体音量", "vol"},
            {"C11_NAVI",            "系统/能耗", "导航音量", "vol"},
            {"C11_SPEECH",          "系统/能耗", "语音音量", "vol"},
            {"C11_CALL",            "系统/能耗", "通话音量", "vol"},
            // ── v0.3.7 从 Rightware vdex 共享区补全的真机键（含义未标定者用 raw 原样展示，不臆测）──
            {"strCar100006",            "空调", "空调界面", "switch"},
            {"strCarAirStatus",         "空调", "空调状态", "raw"},
            {"strCarAntiColdWindMode",  "空调", "防冷风模式", "switch"},
            {"strCarPTCOutTemp",        "空调", "PTC输出温度", "raw"},
            {"strCarTrunkState",        "车门/车锁/车窗", "后备箱状态", "raw"},
            {"strCarSentinelMode",      "车门/车锁/车窗", "哨兵模式", "switch"},
            {"str_unLock",              "车门/车锁/车窗", "解锁指令", "raw"},
            {"str_unLocked",            "车门/车锁/车窗", "已解锁", "raw"},
            {"strCarSlowChargeLockSts", "充电", "慢充锁状态", "switch"},
            {"str_slowChargeUnLock",    "充电", "慢充解锁", "raw"},
            {"strCarWirelessCharge",    "系统/能耗", "无线充电", "switch"},
            {"strCarSeat",              "系统/能耗", "座椅状态", "raw"},
            {"strCarSeat1216",          "系统/能耗", "座椅状态1216", "raw"},
            {"strCarSeat1520",          "系统/能耗", "座椅状态1520", "raw"},
            {"strCarSeat1521",          "系统/能耗", "座椅状态1521", "raw"},
            {"strCarPageStatus",        "系统/能耗", "页面状态", "raw"},
            {"strCarFace",              "系统/能耗", "Face状态", "raw"},
            {"strCarCalibration",       "系统/能耗", "校准状态", "raw"},
            {"strCar1217",              "系统/能耗", "状态1217", "raw"},
            {"strCar1506",              "系统/能耗", "状态1506", "raw"},
            {"strCar1518",              "系统/能耗", "状态1518", "raw"},
            {"strCar9027",              "系统/能耗", "状态9027", "raw"},
            {"strCarBleState",          "系统/能耗", "蓝牙状态", "raw"},
            {"strCarBluetoothStatus",   "系统/能耗", "蓝牙连接", "raw"},
            {"strCarCCConectSts",       "系统/能耗", "CarPlay连接", "raw"},
            {"strCarWifiStatus",        "系统/能耗", "WiFi状态", "raw"},
            {"strCar4gLevel",           "系统/能耗", "4G等级", "raw"},
            {"strCarEntertainmentDisplay", "系统/能耗", "娱乐屏状态", "raw"},
            {"strShowCarMode",          "系统/能耗", "显示模式", "raw"},
            {"strCarVoiceCustom",       "系统/能耗", "语音自定义", "raw"},
            {"strCarVoiceCustom2115",   "系统/能耗", "语音自定义2115", "raw"},
            {"strCarBackMute",          "系统/能耗", "后排静音", "switch"},
        };
        for (Object[] row : m)
            SETTINGS_META.put((String) row[0],
                    new String[]{(String) row[1], (String) row[2], (String) row[3]});
    }

    /** XML/节点字段 → 分组（未列出的默认归系统/能耗）。 */
    private static String nodeGroup(String field) {
        switch (field) {
            case "speed": case "gear":
                return "动力/底盘";
            case "AirValue": case "AirState": case "Bottom_AC": case "SyncButton":
            case "LeftTempValue": case "RightTempValue":
            case "LeftSeatVentilation": case "RightSeatVentilation":
            case "seatVentAuto": case "optionSeatVentilation": case "optionSeatVentilation02":
                return "空调";
            case "SunRoofProgress": case "carLock": case "carLock_double":
            case "CMSlfDoor": case "CMSrfDoor":
                return "车门/车锁/车窗";
            case "CMSstopLight": case "CMSlTurnLight": case "CMSrTurnLight": case "Close":
                return "灯光";
            default:
                return "系统/能耗";
        }
    }

    /** 清单分组展示顺序（未识别固定在最后）。 */
    private static final String[] GROUP_ORDER = {
        "动力/底盘", "空调", "车门/车锁/车窗", "灯光",
        "系统/能耗", "充电", "胎压", "行程", "定位", "未识别"
    };

    private static int groupIndex(String group) {
        for (int i = 0; i < GROUP_ORDER.length; i++)
            if (GROUP_ORDER[i].equals(group)) return i;
        return GROUP_ORDER.length;
    }

    private static String formatSettings(String kind, String key, int iv) {
        if ("temp".equals(kind))
            return String.format(java.util.Locale.US, "%.1f°C", iv / 2.0);
        if ("fan".equals(kind))  return iv + " 级";
        if ("pm".equals(kind))   return iv + " µg/m³";
        if ("vol".equals(kind) || "raw".equals(kind))  return String.valueOf(iv);
        return iv == 1 ? "开" : "关"; // switch
    }

    /**
     * 把本轮全部渠道的信号汇总为清单行，按 {@link #GROUP_ORDER} 稳定排序。
     * 同组内 settings → event → node → TPMS/GPS/行程，多渠道同名数据全部保留。
     */
    private void buildRows(DashboardSnapshot snap,
                           java.util.ArrayList<SignalRow> settingsRows,
                           LogcatVehicleSource.Result pr) {
        java.util.ArrayList<SignalRow> all = new java.util.ArrayList<SignalRow>();
        all.addAll(settingsRows);

        if (pr != null) {
            for (java.util.Map.Entry<Integer, LogcatVehicleSource.State> e
                    : pr.eventStates.entrySet()) {
                int id = e.getKey();
                String g = LogcatVehicleSource.groupOfEvent(id);
                if (g.isEmpty()) g = "未识别";
                LogcatVehicleSource.State st = e.getValue();
                String v = st.raw;
                String unit = LogcatVehicleSource.unitOfEvent(id);
                if (!unit.isEmpty()) v += " " + unit;
                if (st.meaning != null) v += " (" + st.meaning + ")";
                all.add(new SignalRow(g, st.name, v, "event/" + id));
            }
            for (java.util.Map.Entry<String, LogcatVehicleSource.State> e
                    : pr.xmlStates.entrySet()) {
                String field = e.getKey();
                all.add(new SignalRow(nodeGroup(field), e.getValue().name,
                        e.getValue().raw, "node/" + field));
            }
            for (java.util.Map.Entry<Integer, LogcatVehicleSource.State> e
                    : pr.tireStates.entrySet()) {
                all.add(new SignalRow("胎压", e.getValue().name,
                        e.getValue().meaning, "TPMS/" + e.getKey()));
            }
            if (pr.gps.count > 0)
                all.add(new SignalRow("定位", "GPS", pr.gps.meaning, "LocationDataC23"));
            if (!pr.tripMile.isEmpty()) {
                all.add(new SignalRow("行程", "自启动里程", pr.tripMile + " km",
                        "EnergyDataBinder/EV_MILE"));
                all.add(new SignalRow("行程", "自启动时间", pr.tripTime + " min",
                        "EnergyDataBinder/EV_TIME"));
                all.add(new SignalRow("行程", "平均能耗", pr.tripConsume + " kWh/100km",
                        "EnergyDataBinder/CONSUME"));
            }
        }

        java.util.Collections.sort(all, new java.util.Comparator<SignalRow>() {
            @Override public int compare(SignalRow a, SignalRow b) {
                int ga = groupIndex(a.group), gb = groupIndex(b.group);
                return ga < gb ? -1 : (ga > gb ? 1 : 0);
            }
        });
        snap.rows.addAll(all);
        Logger.info(TAG, "信号清单组装 " + all.size() + " 行");
    }

    /** 把 LogcatVehicleSource 解析结果映射进快照 */
    private void parseLogcat(LogcatVehicleSource.Result r, DashboardSnapshot snap) {
        snap.batterySoc = intOf(xml(r, "figure"), intOf(event(r, 3162), -1), -1);
        snap.voltage = floatOf(event(r, 3130), -1);
        snap.current = floatOf(event(r, 3131), -1);
        snap.speedKmh = intOf(xml(r, "speed"), intOf(event(r, 1108), -1), -1);
        snap.outsideTemp = intOf(event(r, 33110), -1);

        String g = xml(r, "gear");
        if (g.isEmpty()) g = meaningOf(r, 1110);
        if (!g.isEmpty()) snap.gear = g;

        snap.rangeStd = intOf(xml(r, "rangeStandard"), -1);
        snap.rangeDyn = intOf(xml(r, "rangeDynamic"), -1);

        if (!r.tireStates.isEmpty()) {
            for (int pos = 0; pos < 4; pos++) {
                LogcatVehicleSource.State st = r.tireStates.get(pos);
                if (st == null) continue;
                try {
                    snap.tirePressKpa[pos] = (float) Math.floor(Double.parseDouble(st.raw));
                } catch (Exception ignored) {}
                // meaning 形如 "2.31bar 24°C"，提取温度
                if (st.meaning != null) {
                    java.util.regex.Matcher mm = TEMP_RE.matcher(st.meaning);
                    if (mm.find()) {
                        try { snap.tireTempC[pos] = (float) Math.floor(Double.parseDouble(mm.group(1))); }
                        catch (Exception ignored) {}
                    }
                }
            }
        }

        snap.doorStates[0] = intOf(event(r, 9123), -1); // 左前
        snap.doorStates[1] = intOf(event(r, 9124), -1); // 右前
        snap.doorStates[2] = intOf(event(r, 9125), -1); // 左后
        snap.doorStates[3] = intOf(event(r, 9126), -1); // 右后
        snap.doorStates[4] = intOf(event(r, 9127), -1); // 后备箱
        snap.doorStates[5] = intOf(event(r, 9128), -1); // 前机盖

        // 四车窗开度%（顺序 左前 右前 左后 右后）
        snap.windowPct[0] = intOf(event(r, 21181), -1);
        snap.windowPct[1] = intOf(event(r, 21180), -1);
        snap.windowPct[2] = intOf(event(r, 21183), -1);
        snap.windowPct[3] = intOf(event(r, 21182), -1);
    }

    private static String xml(LogcatVehicleSource.Result r, String field) {
        LogcatVehicleSource.State s = r.xmlStates.get(field);
        return s == null || s.raw == null ? "" : s.raw;
    }
    private static String event(LogcatVehicleSource.Result r, int id) {
        LogcatVehicleSource.State s = r.eventStates.get(id);
        return s == null || s.raw == null ? "" : s.raw;
    }
    private static String meaningOf(LogcatVehicleSource.Result r, int id) {
        LogcatVehicleSource.State s = r.eventStates.get(id);
        return s == null || s.meaning == null ? "" : s.meaning;
    }
    private static int intOf(String raw, int dft) {
        if (raw == null || raw.isEmpty()) return dft;
        try { return (int) Math.floor(Double.parseDouble(raw.trim())); } catch (Exception e) { return dft; }
    }
    private static int intOf(String raw, int secondary, int dft) {
        int a = intOf(raw, dft);
        if (a != dft) return a;
        return secondary;
    }
    private static float floatOf(String raw, float dft) {
        if (raw == null || raw.isEmpty()) return dft;
        try { return (float) Double.parseDouble(raw.trim()); } catch (Exception e) { return dft; }
    }

    private static String join(String sep, String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private void deliver(final DashboardSnapshot snap) {
        if (mainHandler != null && callback != null) {
            mainHandler.post(() -> callback.onSnapshot(snap));
        }
    }
}
