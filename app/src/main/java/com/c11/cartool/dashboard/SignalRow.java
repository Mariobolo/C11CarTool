package com.c11.cartool.dashboard;

/**
 * 全车信号清单中的一行。
 *
 * <p>同一逻辑数据若存在多个来源（settings / eventId / XML 节点 / TPMS ...），
 * 每个来源各生成一行，{@link #channel} 标注渠道，互不覆盖、全部保留。
 */
public final class SignalRow {

    /** 分组标题（动力/底盘、车门/车锁/车窗、灯光、空调、系统/能耗、充电、行程、定位）。 */
    public final String group;
    /** 中文名称。 */
    public final String name;
    /** 显示值（已含单位；无值为 "--"）。 */
    public final String value;
    /** 渠道标注（如 "settings/strCarAirWind"、"event/28104"、"node/AirValue"）。 */
    public final String channel;

    public SignalRow(String group, String name, String value, String channel) {
        this.group = group;
        this.name = name;
        this.value = value;
        this.channel = channel;
    }
}
