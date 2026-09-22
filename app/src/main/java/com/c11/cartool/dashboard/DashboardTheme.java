package com.c11.cartool.dashboard;

/**
 * 仪表盘统一主题（深色、低干扰、车机友好）。
 * 独立于 MainActivity，避免跨包引用包级私有常量。
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

    /** 数据无值统一显示 */
    public static final String NA = "--";
}
