package com.c11.cartool;

import java.util.ArrayList;
import java.util.List;

/**
 * 零跑 C11 全量参数字典（结构化，v0.3.11 由弱类型 {@code String[][]} 重构为 {@link ParamDef}）。
 *
 * <p>每条参数含：键 / 中文名 / 数据类型({@link Kind}) / 命名空间({@link Namespace}) /
 * 读写性 / 是否真机实证 / 取值约束 / 备注。
 *
 * <p><b>收录原则（不臆造）</b>：
 * <ul>
 *   <li>真机 settings global 实证键（见 {@link com.c11.cartool.vehicle.RealVehicleKeys}）；</li>
 *   <li>logcat 只读信号（见 {@link com.c11.cartool.vehicle.LogcatVehicleSource}），命名空间 LOGCAT；</li>
 *   <li>早期推测、无硬件消费者的 leap.* 虚拟键已剔除（真机读写均空，历史见 reference/，不在字典伪装可用）。</li>
 * </ul>
 *
 * <p><b>兼容</b>：尚未迁移的工程模式 UI（MainActivity / WorkbenchTest）仍按 {@code String[]}
 * 访问，故由 {@link ParamDef#toRow()} 生成旧格式视图 {@link #PARAMS}（仅含 settings 条目，
 * logcat 只读信号不混入该视图）；新代码应直接遍历结构化的 {@link #ALL}。
 */
public final class VehicleParams {

    private VehicleParams() {}

    /** 数据类型。 */
    public enum Kind { BOOL, INT, FLOAT, STRING }

    /** 命名空间。LOGCAT = 仅能从日志只读；SETTING_GLOBAL = settings global 真机键。 */
    public enum Namespace { SETTING_GLOBAL, LOGCAT }

    /** 单条参数定义（不可变）。 */
    public static final class ParamDef {
        public final String key;
        public final String label;
        public final Kind kind;
        public final Namespace namespace;
        public final boolean writable;
        public final boolean verified;   // 真机实证=true；仅键存在/语义待标定=false
        public final String constraint;
        public final String note;

        public ParamDef(String key, String label, Kind kind, Namespace namespace,
                        boolean writable, boolean verified, String constraint, String note) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.namespace = namespace;
            this.writable = writable;
            this.verified = verified;
            this.constraint = constraint == null ? "" : constraint;
            this.note = note == null ? "" : note;
        }

        /** 旧格式 6 列：{key, 中文名, 类型, 命名空间(setting/prop), 默认值(空), 约束+备注}。 */
        public String[] toRow() {
            String type = kind.name().toLowerCase();
            String ns = namespace == Namespace.SETTING_GLOBAL ? "setting" : "prop";
            String range = (verified ? "" : "[待标定] ") + constraint
                    + (note.isEmpty() ? "" : "（" + note + "）");
            return new String[]{key, label, type, ns, "", range};
        }
    }

    // ── 紧凑构造 helper ──
    private static ParamDef sg(String key, String label, Kind kind, boolean writable,
                               boolean verified, String constraint, String note) {
        return new ParamDef(key, label, kind, Namespace.SETTING_GLOBAL, writable, verified, constraint, note);
    }
    private static ParamDef lc(String key, String label, Kind kind,
                               boolean verified, String constraint, String note) {
        return new ParamDef(key, label, kind, Namespace.LOGCAT, false, verified, constraint, note);
    }

    /** 全量结构化字典（settings 可读写 + logcat 只读，多渠道同名以 (渠道) 区分）。 */
    public static final ParamDef[] ALL = {
        // ═══ settings global · 空调座舱（可写，真机实证）═══
        sg("strCarAirSwitch",   "空调开关",   Kind.INT,  true, true, "0关 1开", ""),
        sg("strCarAirWind",     "空调风量",   Kind.INT,  true, true, "0-7档", ""),
        sg("strCarAirStatus",   "空调运行模式", Kind.INT, true, true, "0自动 1制冷 2制热", "3语义待标定(疑除湿/强冷)"),
        sg("strCarAirInner",    "内外循环",   Kind.INT,  true, true, "0外 1内 2自动", "2=自动待真机确认；替代旧中文键 leap.hvac循环模式"),
        sg("strCarFrontDefrost","前除霜(除雾)", Kind.BOOL, true, true, "0关 1开", ""),
        sg("strCarRearDefrost", "后除霜",     Kind.BOOL, true, true, "0关 1开", ""),
        sg("strCar1409",        "主驾温度(settings)", Kind.INT, true, true, "16-32℃", "℃×2 半度编码：值=温度×2，52→26℃"),
        sg("strCar1410",        "副驾温度(settings)", Kind.INT, true, true, "16-32℃", "℃×2 半度编码：值=温度×2，52→26℃"),
        sg("strCarAntiColdWindMode", "防冷风模式", Kind.BOOL, true, true, "0关 1开", ""),
        sg("strCarMirrorHeart", "后视镜加热", Kind.BOOL, true, true, "0关 1开", ""),
        sg("strCar100006",     "空调界面",   Kind.BOOL, true, true, "0关 1开", "边缘触发：需 0→1 跳变才开页"),

        // ═══ settings global · 锁/门/窗/模式（可写）═══
        sg("strCarVehicleLock", "整车锁(settings)", Kind.INT, true, false, "0解锁 1闭锁", "备用通道，主锁动作用 Rightware；方向待核对"),
        sg("strCarChildLock",   "儿童锁",     Kind.INT, true, true, "0关 1开", ""),
        sg("strCarWindowForbit","车窗锁(禁降)", Kind.BOOL, true, true, "0关 1开", ""),
        sg("strCarSentinelMode","哨兵模式",   Kind.BOOL, true, true, "0关 1开", ""),

        // ═══ settings global · 音量（可写）═══
        sg("C11_MUSIC", "媒体音量", Kind.INT, true, true, "0-100", ""),
        sg("C11_NAVI",  "导航音量", Kind.INT, true, true, "0-100", ""),
        sg("C11_CALL",  "通话音量", Kind.INT, true, true, "0-100", ""),
        sg("C11_SPEECH","语音音量", Kind.INT, true, true, "0-100", ""),
        sg("SPEECH_SPEAK", "语音播报开关", Kind.BOOL, true, true, "0关 1开", ""),

        // ═══ settings global · 只读状态（真机存在，多为待标定）═══
        sg("strCarPTCOutTemp", "PTC出风温度", Kind.INT, false, true, "℃", ""),
        sg("strCarTrunkState", "后备箱状态", Kind.INT, false, false, "待标定", ""),
        sg("strCar1800",       "氛围灯",     Kind.INT, false, false, "待标定", ""),
        sg("strCarBluetoothStatus", "蓝牙状态", Kind.STRING, false, true, "", ""),
        sg("strCarWifiStatus", "WiFi状态",   Kind.STRING, false, true, "", ""),
        sg("strCarBleState",   "蓝牙BLE状态", Kind.STRING, false, true, "", ""),
        sg("strCarPm25",       "车内PM2.5",  Kind.INT, false, true, "µg/m³", ""),
        sg("strCar4gLevel",    "4G等级",     Kind.INT, false, true, "", ""),
        sg("strCarEntertainmentDisplay", "娱乐屏状态", Kind.INT, false, true, "", ""),
        sg("strCarSlowChargeLockSts", "慢充锁状态", Kind.BOOL, false, true, "0解锁 1锁定", ""),
        sg("strCarCCConectSts","CarPlay连接", Kind.STRING, false, true, "", ""),
        sg("strCarWirelessCharge", "无线充电", Kind.BOOL, false, false, "0关 1开", "待标定"),
        sg("strCarBackMute",   "后排静音",   Kind.BOOL, false, false, "0关 1开", "待标定"),
        sg("strCarSeat1216",   "座椅状态1216", Kind.STRING, false, false, "待标定", ""),
        sg("strCarSeat1520",   "座椅状态1520", Kind.STRING, false, false, "待标定", ""),
        sg("strCarSeat1521",   "座椅状态1521", Kind.STRING, false, false, "待标定", ""),

        // ═══ logcat 只读 · 三电 / 动力 ═══
        lc("3130", "电压(logcat)", Kind.FLOAT, true, "V", "动力电池电压"),
        lc("3131", "电流(logcat)", Kind.FLOAT, true, "A", "放电为负/充电为正(以实测为准)"),
        lc("3162", "电量(logcat)", Kind.INT, true, "%", ""),
        lc("301",  "续航(logcat)", Kind.INT, true, "km", ""),
        lc("1108", "车速(logcat)", Kind.INT, true, "km/h", ""),
        lc("1110", "档位(logcat)", Kind.STRING, true, "R/N/D", "另有 GearMonitor 渠道"),
        lc("33110","车外温度(logcat)", Kind.INT, true, "℃", ""),
        lc("1208", "充电状态(logcat)", Kind.INT, true, "", ""),
        lc("17176","屏幕亮度(logcat)", Kind.INT, true, "%", ""),
        lc("1200", "整车锁(logcat)", Kind.INT, true, "0解锁 1闭锁", ""),
        lc("28110","主驾温度(logcat)", Kind.FLOAT, true, "16-32℃", "logcat 直接为℃，勿与 settings 的℃×2 混淆"),
        lc("28111","副驾温度(logcat)", Kind.FLOAT, true, "16-32℃", "logcat 直接为℃，勿与 settings 的℃×2 混淆"),

        // ═══ logcat 只读 · 车门 / 舱盖（0关 1开）═══
        lc("9123", "左前车门(logcat)", Kind.INT, true, "0关 1开", ""),
        lc("9124", "右前车门(logcat)", Kind.INT, true, "0关 1开", ""),
        lc("9125", "左后车门(logcat)", Kind.INT, true, "0关 1开", ""),
        lc("9126", "右后车门(logcat)", Kind.INT, true, "0关 1开", ""),
        lc("9127", "后备箱(logcat)", Kind.INT, true, "0关 1开", ""),
        lc("9128", "前机盖(logcat)", Kind.INT, true, "0关 1开", ""),

        // ═══ logcat 只读 · 四车窗开度%（21180 已验证，其余待逐窗标定）═══
        lc("21181", "主驾车窗(logcat)", Kind.INT, false, "0-100%", "待逐窗标定"),
        lc("21180", "副驾车窗(logcat)", Kind.INT, true,  "0-100%", ""),
        lc("21183", "左后车窗(logcat)", Kind.INT, false, "0-100%", "待逐窗标定"),
        lc("21182", "右后车窗(logcat)", Kind.INT, false, "0-100%", "待逐窗标定"),
    };

    /**
     * 旧格式兼容视图（6 列），仅含 settings 条目；供尚未迁移的工程模式 UI 使用。
     * logcat 只读信号不混入（避免被 getprop/settings 误读）。
     */
    public static final String[][] PARAMS;
    static {
        List<String[]> rows = new ArrayList<>();
        for (ParamDef d : ALL) {
            if (d.namespace == Namespace.SETTING_GLOBAL) rows.add(d.toRow());
        }
        PARAMS = rows.toArray(new String[0][]);
    }

    /** 全量字典条目数（含 logcat 只读信号）。 */
    public static int getCount() {
        return ALL.length;
    }

    /** 在 settings 兼容视图中按分类/关键字过滤（旧签名，返回 6 列行）。 */
    public static List<String[]> getByCategory(String keyword) {
        List<String[]> result = new ArrayList<>();
        for (String[] p : PARAMS) {
            if (p[0].contains(keyword) || p[1].contains(keyword)) result.add(p);
        }
        return result;
    }

    /** 在 settings 兼容视图中搜索（旧签名，返回 6 列行）。 */
    public static List<String[]> search(String query) {
        List<String[]> result = new ArrayList<>();
        String q = query.toLowerCase();
        for (String[] p : PARAMS) {
            if (p[0].toLowerCase().contains(q) || p[1].toLowerCase().contains(q)) result.add(p);
        }
        return result;
    }
}
