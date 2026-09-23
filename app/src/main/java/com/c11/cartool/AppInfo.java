package com.c11.cartool;

/**
 * 应用版本与功能清单常量（唯一版本来源，所有 UI/日志/诊断统一引用）。
 *
 * 命名规范：0.x.yyyyMMdd，测试阶段使用。
 */
public final class AppInfo {

    private AppInfo() {}

    /** 版本号（与 build.gradle versionName 保持一致） */
    public static final String VERSION = "0.3.7";

    /** 内部版本号（与 build.gradle versionCode 保持一致） */
    public static final int VERSION_CODE = 13;

    /** 应用显示名 */
    public static final String APP_NAME = "C11 车控";

    /** 标题栏完整标识 */
    public static final String TITLE = "🚗 " + APP_NAME + " v" + VERSION;

    /**
     * 当前版本功能清单（首页"功能清单"卡片展示）。
     * 每行一条，格式：- 功能说明
     */
    public static final String FEATURE_LIST =
            "- v0.3.6 数据全量对齐：状态条「📊 信号」进入全车信号清单，解析器拿到的信号全部可见\n"
            + "- 多渠道并列：同一数据 settings/eventId/XML节点 各占一行并标注渠道，互不覆盖\n"
            + "- 新增：行程里程/时间/平均能耗、驾驶模式、制动踏板、天窗遮阳帘、车窗开度、灯光状态、亮度、充电状态、GPS、座椅通风等\n"
            + "- 抓取 TAG 由 5 个补齐到 12 个（含 EnergyDataBinder/C11AirConditioner 等）\n"
            + "- v0.3.5.1 修复版：状态条显示版本号、Web 弹窗带扫码二维码、诊断报告含 ADB 公钥指纹、车控实验清单按真机标定更新\n"
            + "- v0.3.5 公开版：打开即仪表盘（按钮+数据方块+图标，固定网格 12 列）\n"
            + "- 数据方块：电量/续航/电压/车外温度/胎压胎温/车门/PM2.5/四路音量，5s 自动刷新\n"
            + "- 空调大卡：主副驾温度/风量步进 + AC/最大制冷/内外循环/前后除霜（settings 真机键+回读）\n"
            + "- 灯光车身：近光/示廓/后雾/360/儿童锁/后备箱（旧语音广播与讯飞 handMessage）\n"
            + "- 锁车/车窗/后视镜加热/车窗锁：已放出，标注 ⚠ 实验性（通道标定中）\n"
            + "- Dock：空调/除雾/循环/360/后备箱/锁车/解锁/Web\n"
            + "- 顶部状态条：ADB/档位/车速/电量/时间/Web 状态，工程模式一键进入\n"
            + "- Web 远程遥控：开机自启、扫码进入、wlan0 地址直连（真机同 WiFi 网段）\n"
            + "- ADB 长连接：保活心跳、自动重连、指纹固定、状态实时刷新\n"
            + "- 工程模式保留：一键全测/诊断/200+ 参数/logcat 解析/环境采集\n"
            + "- 双分辨率：中控 1920×1080（12×6）、仪表/副驾 1920×720（12×4 精简）";
}
