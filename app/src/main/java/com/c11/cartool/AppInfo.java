package com.c11.cartool;

/**
 * 应用版本与功能清单常量（唯一版本来源，所有 UI/日志/诊断统一引用）。
 *
 * 命名规范：0.x，测试阶段使用。
 */
public final class AppInfo {

    private AppInfo() {}

    /** 版本号（与 build.gradle versionName 保持一致） */
    public static final String VERSION = "0.3.8";

    /** 内部版本号（与 build.gradle versionCode 保持一致） */
    public static final int VERSION_CODE = 14;

    /** 应用显示名 */
    public static final String APP_NAME = "C11 车控";

    /** 标题栏完整标识 */
    public static final String TITLE = "🚗 " + APP_NAME + " v" + VERSION;

    /**
     * 当前版本功能清单（首页 / 说明展示）。每行一条。
     */
    public static final String FEATURE_LIST =
            "- 三页结构：仪表盘 / 📊 全车信号清单 / 🎛 全部车控，状态条点击切换\n"
            + "- 取消工程模式：一键诊断、日志导出、手机扫码测控全部前端直达\n"
            + "- 全部车控页：车控命令全量平铺、分组展示；同一功能多通道并列标注，上机分别试即可标定\n"
            + "- 信号清单：支持按名称 / 渠道 / 分组搜索，分组可折叠；多渠道同名各占一行\n"
            + "- 开关成对：灯光 / 童锁 / 后视镜加热 / 车窗锁等给出明确「开 / 关」按钮，避免只能开不能关\n"
            + "- 车窗：每窗 全关 / 微开 / 半开 / 全开 四档，真实开度回写高亮\n"
            + "- 空调大卡：主副驾温度 / 风量步进 + AC / 最大制冷 / 内外循环 / 前后除霜\n"
            + "- 数据方块：电量 / 续航 / 电压 / 车外温度 / 胎压胎温 / 车门 / PM2.5 / 四路音量，5s 自动刷新\n"
            + "- 三态显示：加载中 / 已获取 / 获取失败，读不到明确标注，不放假数据\n"
            + "- 手机扫码测控：Web 服务随 App 自启，同 WiFi 网段手机扫码即可遥控与看日志\n"
            + "- ADB：打开即自动连接（127.0.0.1 + wlan0 多地址候选，最多 6 次），保活重连\n"
            + "- 三屏适配：中控 1920×1080（12×7）、仪表 / 副驾 1920×720（12×4 精简）";
}
