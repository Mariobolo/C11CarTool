package com.c11.cartool.dashboard;

/**
 * 仪表盘统一主题（深色、低干扰、车机友好）。
 * 全项目视觉常量的唯一来源（v0.3.20 设计整改后统一，工程模式 MainActivity 已删除）。
 */
public final class DashboardTheme {

    private DashboardTheme() {}

    // 背景与表面
    public static final int BG       = 0xFF0F1117;   // 页面底
    public static final int SURFACE  = 0xFF1A1D27;   // 状态条/Dock 底
    public static final int CARD     = 0xFF1E2330;   // 卡片底
    public static final int CARD_ACT = 0xFF1E3A5F;   // 激活态卡底
    public static final int CARD_ERR = 0xFF3A2222;   // 失败/无数据态

    // 文字
    public static final int TEXT   = 0xFFE4E4E7;
    public static final int DIM    = 0xFF8B8FA3;
    public static final int FAINT  = 0xFF5A5E6E;

    // 语义色
    public static final int BLUE   = 0xFF3B82F6;
    public static final int GREEN  = 0xFF22C55E;
    public static final int RED    = 0xFFEF4444;
    public static final int YELLOW = 0xFFEAB308;
    public static final int CYAN   = 0xFF06B6D4;
    public static final int ORANGE = 0xFFF97316;
    public static final int PURPLE = 0xFFA855F7;

    // ── 功能色组（v0.3.20 设计审查统一；原散落硬编码就近归组，唯一来源）──
    // 状态反馈（诊断/连接状态文字）
    public static final int STATUS_OK   = GREEN;               // 成功（原 0xFF4CAF50/0xFF81C784 归组）
    public static final int STATUS_WARN = 0xFFFFB74D;          // 警告（原 0xFFFFB74D/0xFFFF9800 归组）
    public static final int STATUS_ERR  = RED;                 // 失败
    // 工程按钮深色组（仅 AmapFixActivity 等保留页使用；诊断按钮，非主 UI）
    public static final int BTN_DEEP_GREEN  = 0xFF2E7D32;      // 一键诊断
    public static final int BTN_DEEP_BLUE   = 0xFF1565C0;      // Web 运行中
    public static final int BTN_DEEP_NAVY   = 0xFF0D47A1;      // Web 远程
    public static final int BTN_DEEP_ORANGE = 0xFFE65100;      // 连接 ADB
    public static final int BTN_DEEP_PURPLE = 0xFF4A148C;      // 查看日志
    public static final int BTN_DEEP_MAGENTA= 0xFF9C27B0;      // 实验入口
    public static final int BTN_DEEP_BROWN  = 0xFF6D4C41;      // 快速全测
    public static final int BTN_DEEP_SLATE  = 0xFF37474F;      // 返回

    // ── 浅色系（MainActivity 原 C_LIGHT_*；AmapFixActivity 浅色页使用）──
    public static final int LIGHT_BG      = 0xFFF5F5F5;
    public static final int LIGHT_SURFACE = 0xFFFFFFFF;
    public static final int LIGHT_CARD    = 0xFFF0F0F0;
    public static final int LIGHT_TEXT    = 0xFF1A1A1A;
    public static final int LIGHT_DIM     = 0xFF666666;

    // ── 组件级（v0.3.20 统一硬编码）──
    public static final int BAR_BG        = 0x990A0F1C;   // 状态条 / Dock 半透明底
    public static final int STROKE_IDLE   = 0x22FFFFFF;   // 内部小卡描边（WindowCardView）
    public static final int STROKE_ACT    = 0x663B82F6;   // 内部小卡激活描边
    public static final int PURE_WHITE    = 0xFFFFFFFF;   // 纯白装饰（滑块拇指/图表点/画布）
    public static final int PLACEHOLDER   = 0x22FFFFFF;   // 封面/占位底（MediaCardView）

    /** 数据无值统一显示 */
    public static final String NA = "--";
}
