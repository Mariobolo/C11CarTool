package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 二维流式网格（纵向，置于 ScrollView 中）。
 *
 * <ul>
 *   <li>列数随可用宽度自适应：columns = (内宽 + gap) / (基准格宽 + gap)；</li>
 *   <li>格宽自适应、行高固定（{@link GridDimens#CELL_H_DP}）；</li>
 *   <li>{@link #addCell}：模块声明 spanX×spanY，按"分组内装箱"从左上扫描
 *       找第一个能完整容纳的空位，自动换行 / 扩行；</li>
 *   <li>{@link #addGroupHeader}：占满整行、强制分组从新行开始（不与上一组混排）；</li>
 *   <li>模块 spanX 超过当前列数时自动按整行处理（窄屏宽卡自然堆叠）。</li>
 * </ul>
 */
public class FlowGridLayout extends FrameLayout {

    private static final class Cell {
        final View view;
        final int sx, sy;
        final boolean header;

        Cell(View view, int sx, int sy, boolean header) {
            this.view = view;
            this.sx = sx;
            this.sy = sy;
            this.header = header;
        }
    }

    private final int marginPx;
    private final int gapPx;
    private final int cellHPx;
    private final int groupHPx;

    private final List<Cell> cells = new ArrayList<>();
    /** 每 cell 的像素盒 {left, top, width, height}（onMeasure 填，onLayout 用） */
    private int[][] boxes;
    private int columns = 1;
    private int totalHeight;

    public FlowGridLayout(Context context) {
        super(context);
        this.marginPx = dp(GridDimens.MARGIN_DP);
        this.gapPx = dp(GridDimens.GAP_DP);
        this.cellHPx = dp(GridDimens.CELL_H_DP);
        this.groupHPx = dp(GridDimens.GROUP_H_DP);
    }

    public <V extends View> V addCell(V view, int spanX, int spanY) {
        cells.add(new Cell(view, Math.max(1, spanX), Math.max(1, spanY), false));
        addView(view);
        requestLayout();
        return view;
    }

    public <V extends View> V addGroupHeader(V view) {
        cells.add(new Cell(view, -1, 1, true));
        addView(view);
        requestLayout();
        return view;
    }

    // ── 测量：自适应列数 → 装箱 → 定位 / 测量子 View ──

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int innerW = Math.max(0, width - marginPx * 2);
        int minCellPx = dp(GridDimens.MIN_CELL_W_DP);
        columns = Math.max(1, (innerW + gapPx) / (minCellPx + gapPx));
        int cellW = Math.max(1, (innerW - (columns - 1) * gapPx) / columns);

        // 占用矩阵（动态增行）；rowHeader 标记该行是否为分组标题行
        List<boolean[]> rows = new ArrayList<>();
        List<Boolean> rowHeader = new ArrayList<>();
        int[] gx = new int[cells.size()];
        int[] gy = new int[cells.size()];

        int floorY = 0;   // 当前分组起始行（不回填上一分组）
        int maxRow = 0;   // 已占用到的最大行
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            if (c.header) {
                int hy = maxRow;
                ensureRows(rows, rowHeader, hy);
                boolean[] r = rows.get(hy);
                for (int k = 0; k < columns; k++) r[k] = true;
                rowHeader.set(hy, Boolean.TRUE);
                gx[i] = 0;
                gy[i] = hy;
                floorY = hy + 1;
                maxRow = hy + 1;
            } else {
                int sx = Math.min(c.sx, columns);
                int sy = c.sy;
                int[] pos = findSlot(rows, floorY, sx, sy);
                int px = pos[0], py = pos[1];
                ensureRows(rows, rowHeader, py + sy - 1);
                for (int b = 0; b < sy; b++) {
                    boolean[] r = rows.get(py + b);
                    for (int a = 0; a < sx; a++) r[px + a] = true;
                }
                gx[i] = px;
                gy[i] = py;
                maxRow = Math.max(maxRow, py + sy);
            }
        }

        // 每行顶部像素
        int rowCount = rows.size();
        int[] rowTop = new int[Math.max(1, rowCount)];
        int acc = marginPx;
        for (int y = 0; y < rowCount; y++) {
            rowTop[y] = acc;
            int rh = Boolean.TRUE.equals(rowHeader.get(y)) ? groupHPx : cellHPx;
            acc += rh + gapPx;
        }
        totalHeight = rowCount == 0 ? marginPx * 2 : acc - gapPx + marginPx;

        // 计算每 cell 像素盒并测量
        boxes = new int[cells.size()][4];
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int left = marginPx + gx[i] * (cellW + gapPx);
            int top = rowTop[gy[i]];
            int w, h;
            if (c.header) {
                w = innerW;
                h = groupHPx;
            } else {
                int sx = Math.min(c.sx, columns);
                w = sx * cellW + (sx - 1) * gapPx;
                h = c.sy * cellHPx + (c.sy - 1) * gapPx;
            }
            boxes[i] = new int[]{left, top, w, h};
            c.view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, View.resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (boxes == null) return;
        for (int i = 0; i < cells.size(); i++) {
            int[] q = boxes[i];
            cells.get(i).view.layout(q[0], q[1], q[0] + q[2], q[1] + q[3]);
        }
    }

    // ── 装箱辅助 ──

    /** 从 startY 行起、从左到右找第一个能容纳 sx×sy 的空位 */
    private int[] findSlot(List<boolean[]> rows, int startY, int sx, int sy) {
        for (int y = startY; ; y++) {
            for (int x = 0; x + sx <= columns; x++) {
                if (fits(rows, x, y, sx, sy)) return new int[]{x, y};
            }
            if (y >= rows.size()) return new int[]{0, Math.max(startY, rows.size())};
        }
    }

    private boolean fits(List<boolean[]> rows, int x, int y, int sx, int sy) {
        for (int b = 0; b < sy; b++) {
            int ry = y + b;
            if (ry >= rows.size()) continue; // 尚未开辟的行视为空
            boolean[] r = rows.get(ry);
            for (int a = 0; a < sx; a++) {
                if (r[x + a]) return false;
            }
        }
        return true;
    }

    private void ensureRows(List<boolean[]> rows, List<Boolean> header, int to) {
        while (rows.size() <= to) {
            rows.add(new boolean[columns]);
            header.add(Boolean.FALSE);
        }
    }

    public int getColumns() {
        return columns;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
