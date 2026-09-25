package com.c11.cartool.vehicle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 零跑 C11 logcat 车辆信号解析器。
 *
 * 数据依据：《零跑C11车辆日志解析规范 v3.0》（车友管家配置已验证 + 日志实测）。
 * 本类只做纯文本解析，不依赖 Android API / 网络，便于离线与单元测试。
 *
 * 覆盖数据源：
 *   C11CarSomeIp / C11AirConditioner —— SomeIP 实时事件（"eventId: X value: Y"，
 *       无论前后是否带 serviceId / instanceId 均能识别）
 *   C11CarXml / C11AirConditioner —— 车辆状态 XML/节点字段（"node_name:field setTextContent:value"）
 *   zza/TPMSBean  —— 胎压胎温
 *   LocationDataC23Handler —— GPS 经纬度/航向
 *   EnergyDataBinder —— 行程里程 / 行驶时间 / 平均能耗
 *
 * 设计原则：只报告日志里真实出现过的值，绝不回填默认值；识别不了的 eventId/字段
 * 一律列入"未识别"清单带回，用于离线补全映射，不臆测含义。
 */
public final class LogcatVehicleSource {

    private LogcatVehicleSource() {}

    /** 一键全测时用 logcat -s 精准抓取的核心 TAG（设备端过滤，传输量小、覆盖该 TAG 全缓冲）。 */
    public static final String[] CORE_TAGS = {
            "C11CarSomeIp:D", "C11CarXml:D", "C11AirConditioner:D", "zza:D",
            "EnergyDataBinder:D", "Wallpaper:D", "CarControl:D", "C11CarConf:D",
            "LocationDataC23Handler:D", "SomeipSub:D", "GearMonitorService:D",
            "UnifiedVehicleDataSvc:D", "C11SmartContrl:D"
    };

    // ───────────────────────── 信号定义 ─────────────────────────

    /** 一个 SomeIP eventId 的定义：中文名、单位、可选枚举值映射、分组。 */
    public static final class Sig {
        public final int id;
        public final String name;
        public final String unit;
        public final String group;
        public final Map<Integer, String> enums;
        Sig(int id, String name, String unit, String group, Map<Integer, String> enums) {
            this.id = id; this.name = name; this.unit = unit; this.group = group; this.enums = enums;
        }
    }

    private static final Map<Integer, Sig> EVENTS = new LinkedHashMap<Integer, Sig>();

    private static Map<Integer, String> enums(Object... kv) {
        Map<Integer, String> m = new LinkedHashMap<Integer, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((Integer) kv[i], (String) kv[i + 1]);
        }
        return m;
    }

    private static void num(int id, String name, String unit, String group) {
        EVENTS.put(id, new Sig(id, name, unit, group, null));
    }

    private static void enu(int id, String name, String group, Map<Integer, String> e) {
        EVENTS.put(id, new Sig(id, name, "-", group, e));
    }

    static {
        final String G_DRIVE = "动力/底盘", G_BODY = "车门/车锁/车窗", G_LIGHT = "灯光",
                G_HVAC = "空调", G_SYS = "系统/能耗", G_CHARGE = "充电";

        // 档位（管家配置验证：1=R,2=N,3=D；P 可能为 0，存疑不臆造）
        enu(1110, "档位", G_DRIVE, enums(1, "R", 2, "N", 3, "D"));
        num(1108, "车速", "km/h", G_DRIVE);
        // 制动踏板：实测为连续开度值（如 34.0），非早期规范猜测的 0/1/2 枚举
        num(11166, "制动踏板开度", "%", G_DRIVE);
        enu(11201, "驾驶模式", G_DRIVE, enums(1, "舒适", 4, "运动", 6, "自定义"));

        // 车门（管家配置验证，已纠正早期错误映射）
        enu(9123, "左前门", G_BODY, enums(0, "关", 1, "开"));
        enu(9124, "右前门", G_BODY, enums(0, "关", 1, "开"));
        enu(9125, "左后门", G_BODY, enums(0, "关", 1, "开"));
        enu(9126, "右后门", G_BODY, enums(0, "关", 1, "开"));
        enu(9127, "后备箱", G_BODY, enums(0, "关", 1, "开"));
        enu(9128, "前机盖", G_BODY, enums(0, "关", 1, "开"));
        enu(1200, "整车锁", G_BODY, enums(0, "解锁", 1, "上锁"));
        enu(21104, "车门锁", G_BODY, enums(0, "解锁", 1, "上锁"));
        enu(9106, "左转灯", G_BODY, enums(0, "关", 1, "开"));
        enu(9107, "右转灯", G_BODY, enums(0, "关", 1, "开"));
        enu(21201, "天窗", G_BODY, enums(3, "开", 4, "关"));
        enu(21207, "遮阳帘", G_BODY, enums(3, "开", 4, "关"));
        num(21180, "右前车窗", "%", G_BODY);
        num(21181, "左前车窗", "%", G_BODY);
        num(21182, "右后车窗", "%", G_BODY);
        num(21183, "左后车窗", "%", G_BODY);

        // 灯光
        enu(3102, "转向灯状态", G_LIGHT, enums(0, "关", 5, "闪烁"));
        enu(9121, "制动灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(14130, "刹车灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1716, "大灯总状态", G_LIGHT, enums(0, "关", 1, "开"));
        // 远光（BCM_HIGHBEAMCTRL=9114）与自动远光（IVI_AUTOHIGHBEAMEN=17111）
        enu(9114, "远光灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(17111, "自动远光", G_LIGHT, enums(0, "关", 1, "开"));
        // 原车 CarHeadUtils 灯光 opcode（SomeIP event，4 位编号体系；与 5 位 eventId 互为多渠道）
        enu(1000, "前雾灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1001, "灯光总开关", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1002, "示廓灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1003, "近光灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1005, "远光灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1006, "后雾灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1007, "前阅读灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1008, "后阅读灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1009, "前左阅读灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1010, "前右阅读灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1011, "后左阅读灯", G_LIGHT, enums(0, "关", 1, "开"));
        enu(1012, "后右阅读灯", G_LIGHT, enums(0, "关", 1, "开"));

        // 动力电池 / 能耗
        num(3130, "电池总电压", "V", G_SYS);
        num(3131, "电池总电流", "A", G_SYS);
        num(3162, "电量", "%", G_SYS);
        num(301, "续航(SomeIP)", "km", G_SYS);

        // 空调
        enu(28101, "空调开关", G_HVAC, enums(0, "关", 1, "开"));
        enu(28103, "空调循环", G_HVAC, enums(0, "外循环", 1, "内循环"));
        num(28104, "空调风量", "级", G_HVAC);
        enu(28106, "前除霜", G_HVAC, enums(0, "关", 1, "开"));
        enu(28107, "后除霜", G_HVAC, enums(0, "关", 1, "开"));
        num(28110, "左区温度", "°C", G_HVAC);
        num(28111, "右区温度", "°C", G_HVAC);
        enu(28156, "座椅通风自动", G_HVAC, enums(0, "关", 1, "开"));
        enu(28157, "同步模式", G_HVAC, enums(0, "关", 1, "开"));

        // 系统 / 充电
        num(17176, "屏幕亮度", "%", G_SYS);
        num(33110, "车外温度", "°C", G_SYS);
        num(1208, "充电状态", "-", G_CHARGE);
        enu(18100, "驱动模式", G_DRIVE, enums(1, "标准", 4, "运动"));
    }

    /** C11CarXml / C11AirConditioner 节点字段名 → 中文名（规范 5.3 + 日志实测）。 */
    private static final Map<String, String> XML_FIELDS = new LinkedHashMap<String, String>();
    static {
        XML_FIELDS.put("speed", "车速(km/h)");
        XML_FIELDS.put("gear", "档位");
        XML_FIELDS.put("figure", "电量(%)");
        XML_FIELDS.put("figureMin", "最低电量(%)");
        XML_FIELDS.put("TCVolt", "电池电压(V)");
        XML_FIELDS.put("TCCurrent", "电池电流(A)");
        XML_FIELDS.put("AirValue", "空调风量(0-7)");
        XML_FIELDS.put("AirState", "空调状态");
        XML_FIELDS.put("Bottom_AC", "底部空调按钮");
        XML_FIELDS.put("SyncButton", "同步按钮");
        XML_FIELDS.put("LeftTempValue", "左区温度(°C)");
        XML_FIELDS.put("RightTempValue", "右区温度(°C)");
        XML_FIELDS.put("SunRoofProgress", "天窗开度(%)");
        XML_FIELDS.put("carLock", "车锁");
        XML_FIELDS.put("carLock_double", "二次锁");
        XML_FIELDS.put("rangeStandard", "标准续航(km)");
        XML_FIELDS.put("rangeDynamic", "动态续航(km)");
        XML_FIELDS.put("Vin", "VIN");
        XML_FIELDS.put("VersionText", "系统版本");
        XML_FIELDS.put("CMSstopLight", "刹车灯");
        XML_FIELDS.put("CMSlTurnLight", "左转向灯");
        XML_FIELDS.put("CMSrTurnLight", "右转向灯");
        XML_FIELDS.put("CMSlfDoor", "左前门");
        XML_FIELDS.put("CMSrfDoor", "右前门");
        XML_FIELDS.put("Volume", "音量");
        XML_FIELDS.put("Close", "近光灯(0=开,1=关)");
        // 座椅通风（C11AirConditioner 节点，日志实测）
        XML_FIELDS.put("LeftSeatVentilation", "左座椅通风");
        XML_FIELDS.put("RightSeatVentilation", "右座椅通风");
        XML_FIELDS.put("seatVentAuto", "座椅通风自动");
        XML_FIELDS.put("optionSeatVentilation", "座椅通风选项");
        XML_FIELDS.put("optionSeatVentilation02", "座椅通风选项2");
    }

    private static final String[] TIRE_POS = {"左前", "右前", "左后", "右后"};

    // ───────────────────────── 正则 ─────────────────────────

    /**
     * SomeIP 事件：eventId 与 value 相邻即可，兼容两种写法：
     *   "onMessage eventId: X value: Y"（C11CarSomeIp）
     *   "onMessage serviceId: .. instanceId: .. eventId: X value: Y"（C11AirConditioner）
     */
    private static final Pattern RE_EVENT = Pattern.compile(
            "eventId\\s*:?\\s*(\\d+)\\s+value\\s*:?\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_EVENTID_MSG = Pattern.compile(
            "eventid\\s*:?\\s*(\\d+)\\s+msg\\s*:?\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_XML = Pattern.compile(
            "(?:node_name\\s*:\\s*|--------node_name:)(\\w+)(?:\\s*setTextContent\\s*:|--------setTextContent:)\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TPMS = Pattern.compile(
            "TPMSBean\\{pos=(\\d+),.*?singleTirePress=(\\d+),.*?singleTireTemp=(\\d+)");
    private static final Pattern RE_GPS = Pattern.compile(
            "D:\\(([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\)\\s+course:([\\d.]+)\\s+tickTime:(\\d+)\\s+status:(\\w)");
    /** EnergyDataBinder：行程里程 / 时间 / 平均能耗（三段连写，无分隔符）。 */
    private static final Pattern RE_ENERGY = Pattern.compile(
            "updateEnergyData:\\s*EV_MILE:\\s*([\\d.]+)\\s*EV_TIME:\\s*(\\d+)\\s*EV_AVERAGE_CONSUME:\\s*([\\d.]+)");

    // ───────────────────────── 解析结果 ─────────────────────────

    public static final class State {
        public String name, raw, unit, meaning, source;
        public int count;
    }

    public static final class Result {
        public final Map<Integer, State> eventStates = new LinkedHashMap<Integer, State>();
        public final Map<String, State> xmlStates = new LinkedHashMap<String, State>();
        public final Map<Integer, State> tireStates = new TreeMap<Integer, State>();
        public final State gps = new State();
        /** EnergyDataBinder 行程三件套（空串=本轮未出现，不填默认值）。 */
        public String tripMile = "", tripTime = "", tripConsume = "";
        public final ArrayList<Integer> unknownEvents = new ArrayList<Integer>();
        public final ArrayList<String> unknownXml = new ArrayList<String>();
        public int someipLines, xmlLines, tpmsLines, gpsLines, energyLines;
        public int totalLines;
    }

    /** 解析整段 logcat，取每个信号最后一次出现的值（最新状态）并计数。 */
    public static Result parse(String logcat) {
        Result r = new Result();
        if (logcat == null) return r;
        for (String rawLine : logcat.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            r.totalLines++;

            Matcher m = RE_EVENT.matcher(line);
            if (m.find()) {
                r.someipLines++;
                putEvent(r, Integer.parseInt(m.group(1)), m.group(2), "eventId");
                continue;
            }
            m = RE_EVENTID_MSG.matcher(line);
            if (m.find() && (line.contains("C11CarSomeIp") || line.contains("Someip") || line.contains("onMessage"))) {
                r.someipLines++;
                putEvent(r, Integer.parseInt(m.group(1)), m.group(2), "eventid");
                continue;
            }
            m = RE_ENERGY.matcher(line);
            if (m.find()) {
                r.energyLines++;
                r.tripMile = m.group(1);
                r.tripTime = m.group(2);
                r.tripConsume = m.group(3);
                continue;
            }
            m = RE_TPMS.matcher(line);
            if (m.find()) {
                r.tpmsLines++;
                int pos = parseIntSafe(m.group(1), -1);
                if (pos >= 0 && pos < 4) {
                    State s = new State();
                    s.name = TIRE_POS[pos] + "轮";
                    int kpa = parseIntSafe(m.group(2), 0);
                    s.raw = String.valueOf(kpa);
                    s.unit = "kPa";
                    s.meaning = String.format(java.util.Locale.US, "%.2fbar %s°C",
                            kpa / 100.0, m.group(3));
                    s.source = "TPMS";
                    s.count = r.tireStates.containsKey(pos) ? r.tireStates.get(pos).count + 1 : 1;
                    r.tireStates.put(pos, s);
                }
                continue;
            }
            m = RE_GPS.matcher(line);
            if (m.find()) {
                r.gpsLines++;
                r.gps.name = "GPS";
                r.gps.raw = m.group(1) + "," + m.group(2);
                r.gps.meaning = "纬度" + m.group(1) + " 经度" + m.group(2)
                        + " 海拔" + m.group(3) + "m 航向" + m.group(4) + "° status=" + m.group(6);
                r.gps.source = "LocationDataC23";
                r.gps.count++;
                continue;
            }
            m = RE_XML.matcher(line);
            if (m.find() && (line.contains("C11CarXml") || line.contains("C11AirConditioner"))) {
                r.xmlLines++;
                String field = m.group(1);
                String val = m.group(2).trim();
                State s = r.xmlStates.get(field);
                if (s == null) {
                    s = new State();
                    String cn = XML_FIELDS.get(field);
                    s.name = cn != null ? cn : field;
                    s.source = "node";
                    r.xmlStates.put(field, s);
                    if (cn == null && !r.unknownXml.contains(field)) r.unknownXml.add(field);
                }
                s.raw = val;
                s.count++;
            }
        }
        return r;
    }

    private static void putEvent(Result r, int id, String val, String source) {
        State s = r.eventStates.get(id);
        Sig def = EVENTS.get(id);
        if (s == null) {
            s = new State();
            s.name = def != null ? def.name : ("eventId " + id);
            s.unit = def != null ? def.unit : "";
            s.source = "SomeIP/" + source;
            r.eventStates.put(id, s);
            if (def == null && !r.unknownEvents.contains(id)) r.unknownEvents.add(id);
        }
        s.raw = val;
        s.meaning = null;
        if (def != null && def.enums != null) {
            try {
                String mm = def.enums.get((int) Math.floor(Double.parseDouble(val)));
                if (mm != null) s.meaning = mm;
            } catch (Exception ignored) {}
        }
        s.count++;
    }

    private static int parseIntSafe(String s, int dft) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dft; }
    }

    /** eventId 的分组（未配置返回 ""，供外部组装信号清单）。 */
    public static String groupOfEvent(int id) {
        Sig d = EVENTS.get(id);
        return d == null ? "" : d.group;
    }

    /** eventId 的单位（未配置或无单位返回 ""）。 */
    public static String unitOfEvent(int id) {
        Sig d = EVENTS.get(id);
        return d == null || d.unit == null ? "" : d.unit;
    }

    // ───────────────────────── 报告 ─────────────────────────

    /** 生成可直接放进一键全测报告的中文状态文本。 */
    public static String summarize(String logcat) {
        Result r = parse(logcat);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("  采集: 共 ").append(r.totalLines).append(" 行；SomeIP 事件 ")
          .append(r.someipLines).append(" 条，XML/节点字段 ").append(r.xmlLines)
          .append(" 条，胎压 ").append(r.tpmsLines).append(" 条，GPS ").append(r.gpsLines)
          .append(" 条，行程 ").append(r.energyLines).append(" 条\n");

        // 按分组输出已识别事件
        String[] groups = {"动力/底盘", "车门/车锁/车窗", "灯光", "空调", "系统/能耗", "充电"};
        for (String g : groups) {
            StringBuilder seg = new StringBuilder();
            for (Map.Entry<Integer, State> e : r.eventStates.entrySet()) {
                Sig def = EVENTS.get(e.getKey());
                if (def == null || !g.equals(def.group)) continue;
                seg.append("    ").append(pad(def.name, 10)).append("= ")
                   .append(fmtState(e.getValue(), def.unit)).append('\n');
            }
            if (seg.length() > 0) sb.append("  ── ").append(g).append(" ──\n").append(seg);
        }

        if (!r.tireStates.isEmpty()) {
            sb.append("  ── 胎压/胎温 ──\n");
            for (State s : r.tireStates.values())
                sb.append("    ").append(pad(s.name, 10)).append("= ").append(s.raw)
                  .append("kPa (").append(s.meaning).append(")\n");
        }

        if (r.gps.count > 0) {
            sb.append("  ── 定位 ──\n    ").append(r.gps.meaning)
              .append("（").append(r.gps.count).append(" 次）\n");
        }

        if (!r.tripMile.isEmpty()) {
            sb.append("  ── 行程（EnergyDataBinder）──\n")
              .append("    自启动里程= ").append(r.tripMile).append(" km\n")
              .append("    自启动时间= ").append(r.tripTime).append(" min\n")
              .append("    平均能耗= ").append(r.tripConsume).append(" kWh/100km\n");
        }

        if (!r.xmlStates.isEmpty()) {
            sb.append("  ── XML/节点 状态快照 ──\n");
            for (State s : r.xmlStates.values())
                sb.append("    ").append(pad(s.name, 14)).append("= ").append(s.raw).append('\n');
        }

        if (!r.unknownEvents.isEmpty()) {
            sb.append("  ── 未识别 eventId（请随报告带回，用于补全映射）──\n    ");
            int n = 0;
            for (Integer id : r.unknownEvents) {
                sb.append(id).append(' ');
                if (++n % 20 == 0) sb.append("\n    ");
            }
            sb.append('\n');
        }
        if (!r.unknownXml.isEmpty()) {
            sb.append("  ── 未识别 XML/节点字段 ──\n    ").append(r.unknownXml.toString()).append('\n');
        }
        if (r.someipLines == 0 && r.xmlLines == 0) {
            sb.append("  ⚠️ 未解析到车辆信号：可能缓冲已被刷掉，请先做几个车控动作后再跑一次，")
              .append("或确认 shell 对 logcat 有读取权限。\n");
        }
        return sb.toString();
    }

    private static String fmtState(State s, String unit) {
        StringBuilder b = new StringBuilder();
        b.append(s.raw);
        if (unit != null && !unit.equals("-") && !unit.isEmpty()) b.append(' ').append(unit);
        if (s.meaning != null) b.append(" (").append(s.meaning).append(')');
        b.append("  [×").append(s.count).append(']');
        return b.toString();
    }

    private static String pad(String s, int w) {
        if (s.length() >= w) return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append('　'); // 全角空格对齐中文
        return b.toString();
    }
}
