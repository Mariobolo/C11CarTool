package com.c11.cartool.desktop;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.List;

/**
 * 网格布局管理器
 *
 * 负责桌面网格的所有计算工作：
 * - 格子坐标 ↔ 像素坐标 转换
 * - 模块尺寸 ↔ 像素尺寸 转换
 * - 重叠检测、空位查找、边界检测
 *
 * 设计原则：纯工具类，无状态，所有方法静态，线程安全
 *
 * 网格规范（中控屏 1920x1080 横屏）：
 * - 16 列 x 7 行
 * - 1x1 格子 = 120dp x 120dp
 * - 格子间距 = 6dp
 * - 外边距 = 16dp
 */
public final class GridLayoutManager {

    // ═══ 网格常量 ═══

    /** 主界面网格列数 */
    public static final int GRID_COLUMNS = 16;

    /** 主界面网格行数 */
    public static final int GRID_ROWS = 7;

    /** 底部 Dock 栏列数 */
    public static final int DOCK_COLUMNS = 24;

    /** 底部 Dock 栏行数 */
    public static final int DOCK_ROWS = 1;

    /** 1x1 格子尺寸（dp） */
    public static final int CELL_SIZE_DP = 120;

    /** 格子间距（dp） */
    public static final int CELL_GAP_DP = 6;

    /** 屏幕外边距（dp） */
    public static final int MARGIN_DP = 16;

    /** Dock 栏 1x1 格子尺寸（dp） */
    public static final int DOCK_CELL_SIZE_DP = 80;

    /** Dock 栏格子间距（dp） */
    public static final int DOCK_CELL_GAP_DP = 4;

    /** Dock 栏高度（dp） */
    public static final int DOCK_HEIGHT_DP = 96;

    // ═══ 运行时像素值（由 init() 计算） ═══

    private static int cellSizePx = 0;
    private static int cellGapPx = 0;
    private static int marginPx = 0;
    private static int dockCellSizePx = 0;
    private static int dockCellGapPx = 0;
    private static boolean initialized = false;

    private GridLayoutManager() {
        // 工具类，禁止实例化
    }

    /**
     * 初始化网格参数（根据屏幕真实分辨率计算像素值）
     * 必须在使用其他方法前调用一次
     *
     * @param context 上下文
     */
    public static synchronized void init(Context context) {
        if (initialized) return;

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);

        float density = dm.density;
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        // 计算格子尺寸：根据屏幕宽度动态调整，保持 16 列
        int availableWidth = screenWidth - 2 * dpToPx(MARGIN_DP, density);
        int totalGap = (GRID_COLUMNS - 1) * dpToPx(CELL_GAP_DP, density);
        cellSizePx = (availableWidth - totalGap) / GRID_COLUMNS;
        cellGapPx = dpToPx(CELL_GAP_DP, density);
        marginPx = dpToPx(MARGIN_DP, density);

        // Dock 栏格子尺寸
        int dockAvailableWidth = screenWidth - 2 * dpToPx(MARGIN_DP, density);
        int dockTotalGap = (DOCK_COLUMNS - 1) * dpToPx(DOCK_CELL_GAP_DP, density);
        dockCellSizePx = (dockAvailableWidth - dockTotalGap) / DOCK_COLUMNS;
        dockCellGapPx = dpToPx(DOCK_CELL_GAP_DP, density);

        initialized = true;
    }

    /**
     * 重新初始化（屏幕旋转或分辨率变化时调用）
     */
    public static synchronized void reinit(Context context) {
        initialized = false;
        init(context);
    }

    // ═══ 坐标转换（主界面网格） ═══

    /**
     * 格子 X 坐标 → 像素 X 坐标（模块左上角）
     */
    public static int gridToPxX(int gridX) {
        checkInitialized();
        return marginPx + gridX * (cellSizePx + cellGapPx);
    }

    /**
     * 格子 Y 坐标 → 像素 Y 坐标（模块左上角）
     */
    public static int gridToPxY(int gridY) {
        checkInitialized();
        return marginPx + gridY * (cellSizePx + cellGapPx);
    }

    /**
     * 像素 X 坐标 → 格子 X 坐标（四舍五入到最近格子）
     */
    public static int pxToGridX(int pxX) {
        checkInitialized();
        if (cellSizePx + cellGapPx == 0) return 0;
        int val = Math.round((float)(pxX - marginPx) / (cellSizePx + cellGapPx));
        return Math.max(0, Math.min(GRID_COLUMNS - 1, val));
    }

    /**
     * 像素 Y 坐标 → 格子 Y 坐标
     */
    public static int pxToGridY(int pxY) {
        checkInitialized();
        if (cellSizePx + cellGapPx == 0) return 0;
        int val = Math.round((float)(pxY - marginPx) / (cellSizePx + cellGapPx));
        return Math.max(0, Math.min(GRID_ROWS - 1, val));
    }

    // ═══ 尺寸转换（主界面网格） ═══

    /**
     * 横向格子数 → 像素宽度
     */
    public static int spanToPxWidth(int spanX) {
        checkInitialized();
        if (spanX <= 0) return 0;
        return spanX * cellSizePx + (spanX - 1) * cellGapPx;
    }

    /**
     * 纵向格子数 → 像素高度
     */
    public static int spanToPxHeight(int spanY) {
        checkInitialized();
        if (spanY <= 0) return 0;
        return spanY * cellSizePx + (spanY - 1) * cellGapPx;
    }

    // ═══ Dock 栏坐标转换 ═══

    /** Dock 栏格子 X → 像素 X */
    public static int dockGridToPxX(int gridX) {
        checkInitialized();
        return marginPx + gridX * (dockCellSizePx + dockCellGapPx);
    }

    /** Dock 栏像素 X → 格子 X */
    public static int dockPxToGridX(int pxX) {
        checkInitialized();
        if (dockCellSizePx + dockCellGapPx == 0) return 0;
        int val = Math.round((float)(pxX - marginPx) / (dockCellSizePx + dockCellGapPx));
        return Math.max(0, Math.min(DOCK_COLUMNS - 1, val));
    }

    /** Dock 栏横向格子数 → 像素宽度 */
    public static int dockSpanToPxWidth(int spanX) {
        checkInitialized();
        if (spanX <= 0) return 0;
        return spanX * dockCellSizePx + (spanX - 1) * dockCellGapPx;
    }

    // ═══ 碰撞检测与空位查找 ═══

    /**
     * 检测两个模块是否重叠
     *
     * @param x1,y1,sx1,sy1 模块A的位置和尺寸
     * @param x2,y2,sx2,sy2 模块B的位置和尺寸
     * @return true 表示重叠
     */
    public static boolean isOverlap(int x1, int y1, int sx1, int sy1,
                                     int x2, int y2, int sx2, int sy2) {
        return x1 < x2 + sx2 && x1 + sx1 > x2
            && y1 < y2 + sy2 && y1 + sy1 > y2;
    }

    /**
     * 检测模块是否越界
     *
     * @return true 表示越界
     */
    public static boolean isOutOfBounds(int x, int y, int spanX, int spanY) {
        return x < 0 || y < 0
            || x + spanX > GRID_COLUMNS
            || y + spanY > GRID_ROWS;
    }

    /**
     * 检测指定位置是否可以放置模块（不越界、不与其他模块重叠）
     *
     * @param module 要放置的模块（用于排除自身）
     * @param gridX,gridY 目标位置
     * @param spanX,spanY 模块尺寸
     * @param modules 已存在的模块列表
     * @return true 表示可以放置
     */
    public static boolean canPlace(Object module, int gridX, int gridY,
                                    int spanX, int spanY, List<?> modules) {
        if (isOutOfBounds(gridX, gridY, spanX, spanY)) {
            return false;
        }
        if (modules == null) return true;

        for (Object m : modules) {
            if (m == module) continue;
            if (!(m instanceof GridPosition)) continue;
            GridPosition gp = (GridPosition) m;
            if (isOverlap(gridX, gridY, spanX, spanY,
                          gp.getGridX(), gp.getGridY(), gp.getSpanX(), gp.getSpanY())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查找离目标位置最近的可放置空位
     *
     * @param module 要放置的模块
     * @param targetX,targetY 目标位置
     * @param spanX,spanY 模块尺寸
     * @param modules 已存在模块
     * @return 可放置位置 [x, y]，如果找不到返回 null
     */
    public static int[] findNearestEmpty(Object module, int targetX, int targetY,
                                           int spanX, int spanY, List<?> modules) {
        // 先检查目标位置
        if (canPlace(module, targetX, targetY, spanX, spanY, modules)) {
            return new int[]{targetX, targetY};
        }

        // 螺旋搜索：从近到远遍历所有位置
        int maxDist = Math.max(GRID_COLUMNS, GRID_ROWS);
        for (int dist = 1; dist <= maxDist; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dy = -dist; dy <= dist; dy++) {
                    // 只搜索当前环（距离等于 dist 的位置）
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != dist) continue;
                    int x = targetX + dx;
                    int y = targetY + dy;
                    if (canPlace(module, x, y, spanX, spanY, modules)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    // ═══ Getter ═══

    public static int getCellSizePx() { checkInitialized(); return cellSizePx; }
    public static int getCellGapPx() { checkInitialized(); return cellGapPx; }
    public static int getMarginPx() { checkInitialized(); return marginPx; }
    public static int getDockCellSizePx() { checkInitialized(); return dockCellSizePx; }
    public static int getDockCellGapPx() { checkInitialized(); return dockCellGapPx; }

    // ═══ 内部工具 ═══

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GridLayoutManager 未初始化，请先调用 init(context)");
        }
    }

    private static int dpToPx(int dp, float density) {
        return Math.round(dp * density);
    }

    /**
     * 网格位置接口（模块需要实现此接口以支持碰撞检测）
     */
    public interface GridPosition {
        int getGridX();
        int getGridY();
        int getSpanX();
        int getSpanY();
    }
}
