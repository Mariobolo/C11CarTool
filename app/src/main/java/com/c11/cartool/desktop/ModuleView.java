package com.c11.cartool.desktop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.c11.cartool.R;
import com.c11.cartool.vehicle.VehicleDataModel;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 模块基类
 *
 * 所有桌面模块（车控按钮、信息模块、应用图标等）的父类。
 * 负责：
 * - 模块位置和尺寸管理（格子坐标）
 * - 编辑模式（长按进入、拖动、删除）
 * - 数据刷新接口
 * - 序列化/反序列化（用于布局持久化）
 * - 统一的视觉样式（圆角卡片、背景色、编辑态抖动）
 *
 * 子类必须实现：
 * - {@link #getDefaultSpan()} 返回默认尺寸 [spanX, spanY]
 * - {@link #refreshData(VehicleDataModel)} 刷新数据显示
 * - {@link #getModuleType()} 返回模块类型字符串
 *
 * 设计原则：基类只处理通用逻辑，子类专注内容渲染
 */
public abstract class ModuleView extends FrameLayout implements GridLayoutManager.GridPosition {

    // ═══ 模块状态 ═══

    /** 模块唯一ID（用于持久化和查找） */
    protected String moduleId;

    /** 模块类型（如 "control_lock", "info_battery", "app_icon"） */
    protected String moduleType;

    /** 格子位置 */
    protected int gridX;
    protected int gridY;

    /** 格子尺寸 */
    protected int spanX;
    protected int spanY;

    /** 是否在编辑模式 */
    protected boolean inEditMode = false;

    /** 是否被选中（编辑模式下） */
    protected boolean isSelected = false;

    // ═══ 视觉相关 ═══

    /** 卡片背景 */
    protected Paint bgPaint;
    /** 卡片圆角 */
    protected float cornerRadius;
    /** 编辑态抖动角度 */
    private float shakeAngle = 0;
    private long shakeStartTime = 0;

    /** 内容容器（子类添加内容到此容器） */
    protected FrameLayout contentContainer;

    /** 删除按钮（编辑模式下显示） */
    protected ImageView deleteButton;

    // ═══ 回调接口 ═══

    /** 模块事件监听器 */
    public interface ModuleListener {
        /** 模块被点击 */
        void onModuleClick(ModuleView module);
        /** 模块进入编辑模式（长按触发） */
        void onModuleEditStart(ModuleView module);
        /** 模块被删除 */
        void onModuleDelete(ModuleView module);
        /** 模块位置变化（拖动结束） */
        void onModulePositionChanged(ModuleView module, int newX, int newY);
    }

    protected ModuleListener listener;

    // ═══ 构造函数 ═══

    public ModuleView(Context context) {
        super(context);
        initBase();
    }

    /**
     * 初始化基础设置
     */
    private void initBase() {
        // 生成默认ID（子类可覆盖）
        this.moduleId = "module_" + System.currentTimeMillis() + "_" + hashCode();

        // 初始化画笔
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF1A1E2E);
        bgPaint.setStyle(Paint.Style.FILL);

        cornerRadius = dpToPx(16);

        // 设置布局参数
        setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 创建内容容器
        contentContainer = new FrameLayout(getContext());
        contentContainer.setLayoutParams(new LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ));
        addView(contentContainer);

        // 创建删除按钮（默认隐藏）
        deleteButton = new ImageView(getContext());
        deleteButton.setImageResource(R.drawable.ic_module_placeholder);
        deleteButton.setVisibility(GONE);
        LayoutParams delParams = new LayoutParams(dpToPx(24), dpToPx(24));
        delParams.gravity = Gravity.TOP | Gravity.END;
        delParams.topMargin = dpToPx(4);
        delParams.rightMargin = dpToPx(4);
        deleteButton.setLayoutParams(delParams);
        deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onModuleDelete(ModuleView.this);
        });
        addView(deleteButton);

        // 设置点击和长按
        setOnClickListener(v -> {
            if (inEditMode) {
                // 编辑模式下点击 = 选中/取消选中
                setSelected(!isSelected);
            } else {
                if (listener != null) listener.onModuleClick(ModuleView.this);
                onModuleClicked();
            }
        });

        setOnLongClickListener(v -> {
            if (!inEditMode) {
                if (listener != null) listener.onModuleEditStart(ModuleView.this);
                return true;
            }
            return false;
        });

        // 默认尺寸
        int[] span = getDefaultSpan();
        this.spanX = span[0];
        this.spanY = span[1];
    }

    // ═══ 抽象方法（子类必须实现） ═══

    /**
     * 获取模块默认尺寸
     * @return [spanX, spanY] 格子数
     */
    public abstract int[] getDefaultSpan();

    /**
     * 刷新模块数据显示
     * @param data 车辆数据模型（可能为 null，表示无数据）
     */
    public abstract void refreshData(VehicleDataModel data);

    /**
     * 获取模块类型字符串（用于序列化和模块工厂）
     */
    public abstract String getModuleType();

    /**
     * 模块被点击（非编辑模式下）
     * 子类可覆盖此方法处理点击逻辑
     */
    protected void onModuleClicked() {
        // 默认空实现，子类覆盖
    }

    // ═══ 布局与尺寸 ═══

    /**
     * 根据格子位置和尺寸更新模块布局
     */
    public void updateLayout() {
        int left = GridLayoutManager.gridToPxX(gridX);
        int top = GridLayoutManager.gridToPxY(gridY);
        int width = GridLayoutManager.spanToPxWidth(spanX);
        int height = GridLayoutManager.spanToPxHeight(spanY);

        LayoutParams params = (LayoutParams) getLayoutParams();
        params.width = width;
        params.height = height;
        setLayoutParams(params);

        setX(left);
        setY(top);
    }

    /**
     * 设置模块位置（格子坐标）
     */
    public void setGridPosition(int x, int y) {
        this.gridX = x;
        this.gridY = y;
        updateLayout();
    }

    /**
     * 设置模块尺寸（格子数）
     */
    public void setSpan(int spanX, int spanY) {
        this.spanX = spanX;
        this.spanY = spanY;
        updateLayout();
    }

    // ═══ GridPosition 接口实现 ═══

    @Override public int getGridX() { return gridX; }
    @Override public int getGridY() { return gridY; }
    @Override public int getSpanX() { return spanX; }
    @Override public int getSpanY() { return spanY; }

    // ═══ 编辑模式 ═══

    /**
     * 设置编辑模式
     */
    public void setEditMode(boolean edit) {
        this.inEditMode = edit;
        deleteButton.setVisibility(edit ? VISIBLE : GONE);
        if (edit) {
            shakeStartTime = System.currentTimeMillis();
            invalidate();
        } else {
            setSelected(false);
            setRotation(0);
            invalidate();
        }
    }

    /**
     * 设置选中状态（编辑模式下）
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        if (selected) {
            bgPaint.setColor(0xFF2A3A5E);
        } else {
            bgPaint.setColor(0xFF1A1E2E);
        }
        invalidate();
    }

    public boolean isSelected() { return isSelected; }
    public boolean isInEditMode() { return inEditMode; }

    // ═══ 绘制 ═══

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 编辑模式下应用抖动效果
        if (inEditMode) {
            long elapsed = System.currentTimeMillis() - shakeStartTime;
            shakeAngle = (float) Math.sin(elapsed / 100.0) * 1.5f;
            canvas.save();
            canvas.rotate(shakeAngle, getWidth() / 2f, getHeight() / 2f);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制卡片背景
        RectF rect = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
    }

    // ═══ 序列化 ═══

    /**
     * 序列化为 JSON（用于布局保存）
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", moduleId);
            json.put("type", getModuleType());
            json.put("x", gridX);
            json.put("y", gridY);
            json.put("spanX", spanX);
            json.put("spanY", spanY);
            // 子类可覆盖 toJsonExtra() 添加额外数据
            JSONObject extra = toJsonExtra();
            if (extra != null) {
                json.put("extra", extra);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * 子类可覆盖此方法添加额外的序列化数据
     */
    protected JSONObject toJsonExtra() {
        return null;
    }

    /**
     * 从 JSON 反序列化（恢复位置和尺寸）
     * 注意：模块实例由 ModuleFactory 创建，此方法只恢复状态
     */
    public void fromJson(JSONObject json) {
        try {
            if (json.has("id")) moduleId = json.getString("id");
            if (json.has("x")) gridX = json.getInt("x");
            if (json.has("y")) gridY = json.getInt("y");
            if (json.has("spanX")) spanX = json.getInt("spanX");
            if (json.has("spanY")) spanY = json.getInt("spanY");
            if (json.has("extra")) {
                fromJsonExtra(json.getJSONObject("extra"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 子类可覆盖此方法恢复额外数据
     */
    protected void fromJsonExtra(JSONObject extra) {
        // 默认空实现
    }

    // ═══ Getter / Setter ═══

    public String getModuleId() { return moduleId; }
    public void setModuleId(String id) { this.moduleId = id; }
    public void setModuleListener(ModuleListener l) { this.listener = l; }
    public FrameLayout getContentContainer() { return contentContainer; }

    // ═══ 工具方法 ═══

    protected int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        ));
    }

    /**
     * 创建一个简单的文字标签（子类常用）
     */
    protected TextView createLabel(String text, int textSizeSp, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
