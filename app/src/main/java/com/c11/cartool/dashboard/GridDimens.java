package com.c11.cartool.dashboard;

/**
 * 自适应网格与车机字号常量（集中管理，唯一来源）。
 *
 * <p>字号对齐 Google 车机设计指南：主要内容 ≥32sp、触摸目标 ≥76dp。
 * 1×1 紧凑格内辅助标签受空间限制适当缩小，主数值仍达标。
 */
public final class GridDimens {

    private GridDimens() {}

    // ── 网格（dp）──
    public static final int MIN_CELL_W_DP = 140;  // 基准最小格宽（决定列数）
    public static final int CELL_H_DP     = 120;  // 固定行高（>76dp 触摸高）
    public static final int GAP_DP        = 12;   // 模块间距
    public static final int MARGIN_DP     = 16;   // 外边距
    public static final int GROUP_H_DP    = 40;   // 分组标题行高

    // ── 字号（sp）──
    public static final int SP_VALUE      = 38;   // 1×1 主数值（主要，≥32）
    public static final int SP_LABEL      = 17;   // 1×1 标签/单位（辅助）
    public static final int SP_STEP_VAL   = 26;   // 步进当前值
    public static final int SP_STEP_BTN   = 24;   // 步进 −/+ 按钮（≥24）
    public static final int SP_ACTION     = 18;   // 动作/开关文字
    public static final int SP_GROUP      = 20;   // 分组标题
    public static final int SP_BADGE      = 13;   // 渠道角标
    public static final int SP_CHIP       = 18;   // 状态栏 / Dock
}
