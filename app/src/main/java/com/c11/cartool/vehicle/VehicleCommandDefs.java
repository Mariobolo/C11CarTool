package com.c11.cartool.vehicle;

/**
 * 车控命令定义
 *
 * 集中定义所有车控按钮的命令参数，避免魔法字符串。
 * 每个命令包含：显示名称、广播 Action、type、state、读取属性（用于状态显示）
 *
 * 控制方式：
 * - BROADCAST：通过 am broadcast 发送（需要 shell 权限）
 * - SETTINGS：通过 settings put global 写入（需要 shell 或 WRITE_SECURE_SETTINGS）
 * - PROPERTY：通过 setprop 写入（需要 root 或 shell 权限）
 *
 * 注意：所有 type 值来自 old/chekong/app_analysis/vehicle_control_interfaces.md
 *       实际可用性需上机验证
 */
public final class VehicleCommandDefs {

    private VehicleCommandDefs() {
        // 工具类，禁止实例化
    }

    // ═══ 广播 Action 常量 ═══

    public static final String ACTION_AIRCONDITIONER = "com.leapmotor.speech.toairconditioner";
    public static final String ACTION_CAR_CONTROL = "com.leapmotor.speech.tocarcontrol";
    public static final String ACTION_SETTINGS = "com.leapmotor.speech.tosettings";

    // ═══ 控制方式枚举 ═══

    public enum ControlMethod {
        BROADCAST,   // am broadcast
        SETTINGS,    // settings put global
        PROPERTY     // setprop
    }

    // ═══ 命令定义类 ═══

    /**
     * 单个车控命令定义
     */
    public static class CommandDef {
        public final String name;           // 显示名称
        public final ControlMethod method;  // 控制方式
        public final String action;         // 广播 Action（BROADCAST 方式）
        public final String type;           // 广播 type 参数
        public final int onState;           // 开启时的 state 值
        public final int offState;          // 关闭时的 state 值
        public final String settingsKey;    // settings 键名（SETTINGS 方式）
        public final String propertyKey;    // 属性键名（PROPERTY 方式）
        public final String readProperty;   // 用于读取状态的属性名（getprop）
        public final String readSettings;   // 用于读取状态的 settings 键名
        public final boolean isToggle;      // 是否为开关型（点击切换 on/off）

        public CommandDef(String name, ControlMethod method, String action,
                          String type, int onState, int offState,
                          String settingsKey, String propertyKey,
                          String readProperty, String readSettings, boolean isToggle) {
            this.name = name;
            this.method = method;
            this.action = action;
            this.type = type;
            this.onState = onState;
            this.offState = offState;
            this.settingsKey = settingsKey;
            this.propertyKey = propertyKey;
            this.readProperty = readProperty;
            this.readSettings = readSettings;
            this.isToggle = isToggle;
        }

        /**
         * 构建执行命令字符串（shell 命令）
         * @param isOn true=执行开启命令，false=执行关闭命令
         */
        public String buildCommand(boolean isOn) {
            int state = isOn ? onState : offState;
            switch (method) {
                case BROADCAST:
                    return "am broadcast -a " + action +
                           " --es type \"" + type + "\"" +
                           " --ei state " + state;
                case SETTINGS:
                    return "settings put global " + settingsKey + " " + state;
                case PROPERTY:
                    return "setprop " + propertyKey + " " + state;
                default:
                    return "";
            }
        }
    }

    // ═══ 空调座舱类命令 ═══

    public static final CommandDef AC_MAX = new CommandDef(
        "最大制冷", ControlMethod.BROADCAST,
        ACTION_AIRCONDITIONER, "HVACACMAXREQ", 1, 0,
        null, null, "leap.hvac.ac_max", null, true
    );

    public static final CommandDef REAR_DEFROST = new CommandDef(
        "后除霜", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCarRearDefrost", null, "leap.hvac.rear_defrost", "strCarRearDefrost", true
    );

    public static final CommandDef AC_UI = new CommandDef(
        "空调界面", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCar100006", null, "leap.cabin.ac_ui", "strCar100006", true
    );

    // ═══ 灯光类命令 ═══

    public static final CommandDef HEADLIGHT = new CommandDef(
        "近光灯", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "CARLIGHT_JINGUANG", 1, 0,
        null, null, "leap.light.headlight", null, true
    );

    public static final CommandDef POSITION_LAMP = new CommandDef(
        "示廓灯", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "CARLIGHT_SHEKUODENG", 1, 0,
        null, null, "leap.light.position", null, true
    );

    public static final CommandDef REAR_FOG = new CommandDef(
        "后雾灯", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "CARLIGHT_REARFOGCTL", 1, 0,
        null, null, "leap.light.rear_fog", null, true
    );

    // ═══ 车辆控制类命令 ═══

    public static final CommandDef VEHICLE_LOCK = new CommandDef(
        "锁车", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCarVehicleLock", null, null, "strCarVehicleLock", true
    );

    public static final CommandDef CHILD_LOCK = new CommandDef(
        "儿童锁", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCarChildLock", null, "leap.vehicle.child_lock", "strCarChildLock", true
    );

    public static final CommandDef MIRROR_FOLD = new CommandDef(
        "后视镜折叠", ControlMethod.PROPERTY,
        null, null, 1, 0,
        null, "leap.vehicle.mirror_fold",
        "leap.vehicle.mirror_fold", null, true
    );

    public static final CommandDef MIRROR_HEAT = new CommandDef(
        "后视镜加热", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCarMirrorHeart", null, "leap.vehicle.mirror_heat", "strCarMirrorHeart", true
    );

    public static final CommandDef TRUNK = new CommandDef(
        "尾门", ControlMethod.PROPERTY,
        null, null, 1, 0,
        null, "leap.vehicle.trunk",
        "leap.vehicle.trunk", null, true
    );

    public static final CommandDef WINDOW_LOCK = new CommandDef(
        "车窗锁", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCarWindowForbit", null, "leap.vehicle.window_lock", "strCarWindowForbit", true
    );

    public static final CommandDef PEDESTRIAN_ALERT = new CommandDef(
        "行人警示音", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "PEDESTRIANS_ALERT", 1, 0,
        null, null, "leap.system.pedestrians_alert", null, true
    );

    // ═══ 场景模式类命令 ═══

    public static final CommandDef GUARD_MODE = new CommandDef(
        "守护模式", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "GUARD_MODE", 1, 0,
        null, null, "leap.scene.guard", null, true
    );

    public static final CommandDef REST_MODE = new CommandDef(
        "小憩模式", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "REST_MODE", 1, 0,
        null, null, "leap.scene.rest", null, true
    );

    public static final CommandDef CAMPING_MODE = new CommandDef(
        "露营模式", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "CAMPING_MODE", 1, 0,
        null, null, "leap.scene.camping", null, true
    );

    public static final CommandDef POWER_SAVE_MODE = new CommandDef(
        "省电模式", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "POWER_SAVE_MODE", 1, 0,
        null, null, "leap.scene.power_save", null, true
    );

    public static final CommandDef SENTINEL_MODE = new CommandDef(
        "哨兵模式", ControlMethod.BROADCAST,
        ACTION_CAR_CONTROL, "SENTINEL_MODE", 1, 0,
        null, null, "leap.scene.sentinel", null, true
    );

    // ═══ 系统设置类命令 ═══

    public static final CommandDef WIFI = new CommandDef(
        "WiFi", ControlMethod.BROADCAST,
        ACTION_SETTINGS, "wifi", 1, 0,
        null, null, "leap.system.wifi", null, true
    );

    public static final CommandDef BLUETOOTH = new CommandDef(
        "蓝牙", ControlMethod.BROADCAST,
        ACTION_SETTINGS, "bluetooth", 1, 0,
        null, null, "leap.system.bluetooth", null, true
    );

    public static final CommandDef FORCE_CHARGE = new CommandDef(
        "强制充电", ControlMethod.PROPERTY,
        null, null, 1, 0,
        null, "leap.energy.force_charge",
        "leap.energy.force_charge", null, true
    );

    // ═══ 温度调节（非开关型，每次点击+1/-1） ═══

    public static final CommandDef DRIVER_TEMP_UP = new CommandDef(
        "主驾温度+", ControlMethod.SETTINGS,
        null, null, 1, 0,
        "strCar1409", null, null, "strCar1409", false
    );

    public static final CommandDef DRIVER_TEMP_DOWN = new CommandDef(
        "主驾温度-", ControlMethod.SETTINGS,
        null, null, 0, 0,
        "strCar1409", null, null, "strCar1409", false
    );

    // ═══ 所有命令列表（用于遍历和测试） ═══

    public static final CommandDef[] ALL_COMMANDS = {
        AC_MAX, REAR_DEFROST, AC_UI,
        HEADLIGHT, POSITION_LAMP, REAR_FOG,
        VEHICLE_LOCK, CHILD_LOCK, MIRROR_FOLD, MIRROR_HEAT,
        TRUNK, WINDOW_LOCK, PEDESTRIAN_ALERT,
        GUARD_MODE, REST_MODE, CAMPING_MODE, POWER_SAVE_MODE, SENTINEL_MODE,
        WIFI, BLUETOOTH, FORCE_CHARGE,
        DRIVER_TEMP_UP, DRIVER_TEMP_DOWN
    };

    /**
     * 根据名称查找命令定义
     */
    public static CommandDef findByName(String name) {
        for (CommandDef def : ALL_COMMANDS) {
            if (def.name.equals(name)) return def;
        }
        return null;
    }
}
