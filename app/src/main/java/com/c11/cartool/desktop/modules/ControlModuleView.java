package com.c11.cartool.desktop.modules;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.c11.cartool.R;
import com.c11.cartool.desktop.ModuleView;
import com.c11.cartool.vehicle.VehicleCommandDefs;
import com.c11.cartool.vehicle.VehicleDataModel;

import org.json.JSONObject;

/**
 * 车控按钮模块（1x1）
 *
 * 最基础的模块类型，用于单个车辆控制功能。
 * 显示：图标（空白占位）+ 功能名称
 * 点击：执行对应的车控命令（广播/settings/property）
 * 状态：开关型按钮根据当前状态高亮显示
 *
 * 设计原则：纯展示+点击，不包含数据轮询逻辑
 *           状态由 refreshData() 从 VehicleDataModel 读取
 */
public class ControlModuleView extends ModuleView {

    // ═══ 模块类型标识 ═══
    public static final String MODULE_TYPE = "control";

    // ═══ 视图组件 ═══
    private ImageView iconView;
    private TextView labelView;

    // ═══ 命令定义 ═══
    private VehicleCommandDefs.CommandDef commandDef;
    private String commandName; // 用于序列化恢复

    // ═══ 当前状态 ═══
    private boolean currentState = false;

    // ═══ 构造函数 ═══

    public ControlModuleView(Context context, VehicleCommandDefs.CommandDef def) {
        super(context);
        this.commandDef = def;
        this.commandName = def != null ? def.name : "未知";
        initView();
    }

    public ControlModuleView(Context context) {
        super(context);
        initView();
    }

    /**
     * 初始化视图
     */
    private void initView() {
        // 垂直布局：图标在上，文字在下
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(8));

        // 图标
        iconView = new ImageView(getContext());
        iconView.setImageResource(R.drawable.ic_module_placeholder);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(36), dpToPx(36)
        );
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconView.setLayoutParams(iconParams);
        layout.addView(iconView);

        // 文字标签
        labelView = new TextView(getContext());
        labelView.setText(commandName);
        labelView.setTextColor(0xFFE0E6F0);
        labelView.setTextSize(11);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        labelView.setPadding(0, dpToPx(6), 0, 0);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelView.setLayoutParams(labelParams);
        layout.addView(labelView);

        getContentContainer().addView(layout);
    }

    // ═══ ModuleView 抽象方法实现 ═══

    @Override
    public int[] getDefaultSpan() {
        return new int[]{1, 1}; // 车控按钮固定 1x1
    }

    @Override
    public String getModuleType() {
        return MODULE_TYPE;
    }

    @Override
    public void refreshData(VehicleDataModel data) {
        if (data == null || commandDef == null) return;

        // 根据命令类型从数据模型读取状态
        boolean state = readStateFromData(data);
        if (state != currentState) {
            currentState = state;
            updateVisualState();
        }
    }

    @Override
    protected void onModuleClicked() {
        if (commandDef == null) return;

        // 开关型：切换状态；非开关型：执行 onState
        boolean newState = commandDef.isToggle ? !currentState : true;
        executeCommand(newState);
        currentState = newState;
        updateVisualState();
    }

    // ═══ 命令执行 ═══

    /**
     * 执行车控命令
     * 注意：实际执行需要 shell 权限，这里先记录日志
     *       真实执行由 VehicleController 处理（后续集成）
     */
    private void executeCommand(boolean isOn) {
        if (commandDef == null) return;
        String cmd = commandDef.buildCommand(isOn);
        // TODO: 集成 VehicleController 执行命令
        // VehicleController.getInstance().execute(commandDef, isOn);
        android.util.Log.d("ControlModule", "执行命令: " + commandDef.name +
            " (" + (isOn ? "ON" : "OFF") + ") -> " + cmd);
    }

    // ═══ 状态读取 ═══

    /**
     * 从 VehicleDataModel 读取当前状态
     * 根据命令定义的 readProperty 或 readSettings 匹配
     */
    private boolean readStateFromData(VehicleDataModel data) {
        if (commandDef == null) return false;

        // 根据命令名称匹配数据模型字段
        String name = commandDef.name;
        switch (name) {
            case "最大制冷": return data.acMax;
            case "后除霜": return data.rearDefrost;
            case "空调界面": return data.acOn;
            case "近光灯": return data.headlightOn;
            case "示廓灯": return data.positionLampOn;
            case "后雾灯": return data.rearFogOn;
            case "锁车": return data.vehicleLocked;
            case "儿童锁": return data.childLockOn;
            case "后视镜折叠": return data.mirrorFolded;
            case "后视镜加热": return data.mirrorHeatOn;
            case "尾门": return data.trunkOpen;
            case "车窗锁": return data.windowLockOn;
            case "行人警示音": return data.pedestrianAlertOn;
            case "守护模式": return data.guardModeOn;
            case "小憩模式": return data.restModeOn;
            case "露营模式": return data.campingModeOn;
            case "省电模式": return data.powerSaveModeOn;
            case "哨兵模式": return data.sentinelModeOn;
            case "WiFi": return data.wifiOn;
            case "蓝牙": return data.bluetoothOn;
            case "强制充电": return data.forceChargeOn;
            default: return false;
        }
    }

    // ═══ 视觉状态更新 ═══

    /**
     * 根据当前状态更新视觉效果
     */
    private void updateVisualState() {
        if (currentState && commandDef != null && commandDef.isToggle) {
            // 激活态：蓝色背景
            setBackgroundColor(0xFF1E3A5F);
            labelView.setTextColor(0xFF4FC3F7);
        } else {
            // 未激活：默认深色背景（由基类绘制）
            setBackgroundColor(Color.TRANSPARENT);
            labelView.setTextColor(0xFFE0E6F0);
        }
    }

    // ═══ 序列化 ═══

    @Override
    protected JSONObject toJsonExtra() {
        JSONObject extra = new JSONObject();
        try {
            extra.put("commandName", commandName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return extra;
    }

    @Override
    protected void fromJsonExtra(JSONObject extra) {
        try {
            if (extra.has("commandName")) {
                commandName = extra.getString("commandName");
                commandDef = VehicleCommandDefs.findByName(commandName);
                if (labelView != null) labelView.setText(commandName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══ Getter ═══

    public VehicleCommandDefs.CommandDef getCommandDef() {
        return commandDef;
    }

    public String getCommandName() {
        return commandName;
    }
}
