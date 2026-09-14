package com.c11.cartool;

/**
 * 零跑 C11 全量参数定义 (200+)
 *
 * 来源: C11hmi.xml + C11_carConfig.json + 系统分析
 * 分类: prop (getprop) / setting (settings global) / system (settings system)
 */
public final class VehicleParams {

    /**
     * 参数定义: {key, 显示名, 类型, 命名空间, 默认值, 范围/选项}
     * 类型: bool / int / float / string
     * 命名空间: prop / setting / system
     */
    public static final String[][] PARAMS = {
        // ═══ 空调 (17) ═══
        {"leap.cabin.driver_temp",      "主驾温度",         "float",  "prop",    "",  "16-30"},
        {"leap.cabin.passenger_temp",   "副驾温度",         "float",  "prop",    "",  "16-30"},
        {"leap.cabin.ac_ui",            "空调界面",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.hvac.ac_max",            "最大制冷",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.hvac.rear_defrost",      "后除霜",           "bool",   "prop",    "",  "0=关/1=开"},
        {"strCar1409",                  "主驾温度(写)",     "float",  "setting", "25.0", "16-30"},
        {"strCar1410",                  "副驾温度(写)",     "float",  "setting", "25.0", "16-30"},
        {"strCar100006",                "空调界面(写)",     "bool",   "setting", "",  "0=关/1=开"},
        {"leap.hvac.fan_speed",         "风扇速度",         "int",    "prop",    "",  "0-7"},
        {"leap.hvac.mode",              "空调模式",         "int",    "prop",    "",  "0=制冷/1=制热/2=自动"},
        {"leap.hvac循环模式",           "循环模式",         "int",    "prop",    "",  "0=外循环/1=内循环"},
        {"leap.hvac.auto",              "自动空调",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.hvac.aqs",               "空气质量传感器",   "bool",   "prop",    "",  "0=关/1=开"},
        {"strCarRearDefrost",           "后除霜(写)",       "bool",   "setting", "",  "0=关/1=开"},
        {"leap.cabin.driver_temp_unit", "温度单位",         "int",    "prop",    "",  "0=摄氏/1=华氏"},
        {"leap.hvac.ac_ui_state",       "空调界面状态",     "int",    "prop",    "",  "状态值"},
        {"leap.hvac.defrost_state",     "除霜状态",         "int",    "prop",    "",  "状态值"},

        // ═══ 灯光 (15) ═══
        {"leap.light.headlight",        "近光灯",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.position",         "示廓灯",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.rear_fog",         "后雾灯",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.ambient.switch",         "氛围灯开关",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.ambient.color",          "氛围灯颜色",       "int",    "prop",    "",  "0=红/1=橙/3=黄/7=绿/10=青/14=蓝/16=紫"},
        {"strCar1800",                  "氛围灯开关(写)",   "bool",   "setting", "",  "0=关/1=开"},
        {"strCar8867",                  "氛围灯颜色(写)",   "int",    "setting", "14", "0=红/1=橙/3=黄/7=绿/10=青/14=蓝/16=紫"},
        {"leap.light.auto",             "自动灯光",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.high_beam",        "远光灯",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.auto_open",        "灯光自动开启",     "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.delay_close",      "灯光延时关闭",     "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.drl",              "日间行车灯",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.welcome",          "迎宾灯光",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.light.follow_me_home",   "伴我回家",         "int",    "prop",    "",  "延时秒数"},
        {"leap.light.language",         "灯光语言",         "int",    "prop",    "",  "0=关/1=开"},

        // ═══ 座椅 (20+) ═══
        {"leap.seat.main.heat",         "主驾加热",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.main.vent",         "主驾通风",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.main.memory",       "主驾记忆",         "int",    "system",  "",  "1-3"},
        {"leap.seat.main.massage",      "主驾按摩",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.main.massage_type", "主驾按摩类型",     "int",    "system",  "",  "0=波浪/1=单排/2=蝶形/3=脉冲/4=毛布"},
        {"leap.seat.main.massage_level","主驾按摩力度",     "int",    "system",  "",  "1-3"},
        {"leap.seat.main.height",       "主驾高度",         "int",    "system",  "",  "0-100"},
        {"leap.seat.main.lumbar",       "主驾腰托",         "int",    "system",  "",  "0-100"},
        {"leap.seat.sub.heat",          "副驾加热",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.sub.vent",          "副驾通风",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.sub.massage",       "副驾按摩",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.sub.memory",        "副驾记忆",         "int",    "system",  "",  "1-3"},
        {"leap.seat.back.heat",         "后排加热",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.back.left_heat",    "左后加热",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.back.right_heat",   "右后加热",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.main.heat_level",   "主驾加热档位",     "int",    "system",  "",  "0-3"},
        {"leap.seat.sub.heat_level",    "副驾加热档位",     "int",    "system",  "",  "0-3"},
        {"leap.seat.main.vent_level",   "主驾通风档位",     "int",    "system",  "",  "0-3"},
        {"leap.seat.sub.vent_level",    "副驾通风档位",     "int",    "system",  "",  "0-3"},
        {"leap.seat.steering_heat",     "方向盘加热",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.seat.steering_heat_level","方向盘加热档位",  "int",    "system",  "",  "0-3"},

        // ═══ 车身 (15) ═══
        {"leap.vehicle.child_lock",     "儿童锁",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.vehicle.mirror_fold",    "后视镜折叠",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.vehicle.mirror_heat",    "后视镜加热",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.vehicle.trunk",          "后备箱",           "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.vehicle.window_lock",    "车窗锁",           "bool",   "prop",    "",  "0=关/1=开"},
        {"strCarVehicleLock",           "车辆锁(写)",       "bool",   "setting", "",  "0=解锁/1=锁车"},
        {"strCarChildLock",             "儿童锁(写)",       "bool",   "setting", "",  "0=关/1=开"},
        {"strCarMirrorHeart",           "后视镜加热(写)",   "bool",   "setting", "",  "0=关/1=开"},
        {"strCarWindowForbit",          "车窗锁(写)",       "bool",   "setting", "",  "0=关/1=开"},
        {"strCarDoorFrontLeft",         "左前门状态",       "int",    "setting", "",  "状态值"},
        {"strCarDoorFrontRight",        "右前门状态",       "int",    "setting", "",  "状态值"},
        {"strCarDoorRearLeft",          "左后门状态",       "int",    "setting", "",  "状态值"},
        {"strCarDoorRearRight",         "右后门状态",       "int",    "setting", "",  "状态值"},
        {"leap.vehicle.mirror_left",    "左后视镜位置",     "int",    "system",  "",  "位置值"},
        {"leap.vehicle.mirror_right",   "右后视镜位置",     "int",    "system",  "",  "位置值"},

        // ═══ 车窗/天窗 (10) ═══
        {"leap.window.front_left",      "左前车窗",         "int",    "prop",    "",  "0-100"},
        {"leap.window.front_right",     "右前车窗",         "int",    "prop",    "",  "0-100"},
        {"leap.window.rear_left",       "左后车窗",         "int",    "prop",    "",  "0-100"},
        {"leap.window.rear_right",      "右后车窗",         "int",    "prop",    "",  "0-100"},
        {"leap.sunroof.position",       "天窗位置",         "int",    "prop",    "",  "0-100"},
        {"leap.sunroof.vent",           "天窗通风",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.sunshade.position",      "遮阳帘位置",       "int",    "prop",    "",  "0-100"},
        {"leap.window.global_open",     "全部车窗开",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.window.global_close",    "全部车窗关",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.sunroof.global_open",    "天窗全开",         "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 驾驶模式 (6) ═══
        {"leap.drive_mode.set",         "驾驶模式",         "int",    "prop",    "",  "0=舒适/1=运动/2=自定义/3=极致/4=经济/5=领跑"},
        {"leap.drive_mode.comfort",     "舒适模式",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.drive_mode.sport",       "运动模式",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.drive_mode.custom",      "自定义模式",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.drive_mode.extreme",     "极致模式",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.drive_mode.economy",     "经济模式",         "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 转向/能量回收 (6) ═══
        {"leap.steer.mode",             "转向模式",         "int",    "system",  "",  "0=舒适/1=运动/2=标准"},
        {"leap.steer.weight",           "转向力度",         "int",    "system",  "",  "1-3"},
        {"leap.energy.recovery",        "能量回收",         "int",    "system",  "",  "0-3"},
        {"leap.energy.recovery_high",   "高能量回收",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.energy.mode",            "能量模式",         "int",    "prop",    "",  "模式值"},
        {"leap.energy.force_charge",    "强制充电",         "bool",   "prop",    "",  "0=关/1=开"},

        // ═══ 场景模式 (9) ═══
        {"leap.scene.rest",             "小憩模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.camping",          "露营模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.guard",            "守护模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.sentinel",         "哨兵模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.power_save",       "省电模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.experience",       "体验模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.scene.rest_time",        "小憩时长(分)",     "int",    "system",  "30", "分钟"},
        {"leap.scene.guard_time",       "守护时长(分)",     "int",    "system",  "30", "分钟"},
        {"leap.scene.weather_mode",     "天气模式",         "int",    "prop",    "",  "模式值"},

        // ═══ 音量 (6) ═══
        {"leap.volume.call",            "电话音量",         "int",    "prop",    "",  "0-100"},
        {"leap.volume.navi",            "导航音量",         "int",    "prop",    "",  "0-100"},
        {"leap.volume.music",           "媒体音量",         "int",    "prop",    "",  "0-100"},
        {"C11_CALL",                    "电话音量(写)",     "int",    "setting", "",  "0-100"},
        {"C11_NAVI",                    "导航音量(写)",     "int",    "setting", "",  "0-100"},
        {"C11_MUSIC",                   "媒体音量(写)",     "int",    "setting", "",  "0-100"},
        {"C11_SPEECH",                  "语音音量(写)",     "int",    "setting", "10", "10-100"},
        {"SPEECH_SPEAK",                "语音开关",         "bool",   "setting", "",  "0=关/1=开"},
        {"HOME_XIAOLING_FLOAT",         "小灵动画",         "bool",   "setting", "",  "0=关/1=开"},
        {"leap.volume.media_auto",      "媒体音量自动",     "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 充电 (10) ═══
        {"leap.charging.state",         "充电状态",         "int",    "prop",    "",  "状态值"},
        {"leap.charging.current",       "充电电流",         "float",  "prop",    "",  "A"},
        {"leap.charging.voltage",       "充电电压",         "float",  "prop",    "",  "V"},
        {"leap.charging.power",         "充电功率",         "float",  "prop",    "",  "kW"},
        {"leap.charging.soc",           "电池电量",         "int",    "prop",    "",  "0-100%"},
        {"leap.charging.range",         "续航里程",         "int",    "prop",    "",  "km"},
        {"leap.charging.healthy",       "健康充电",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.charging.limit",         "充电限值(%)",      "int",    "system",  "80", "50-100"},
        {"leap.charging.timer",         "定时充电",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.charging.gun_lock",      "充电枪锁",         "bool",   "prop",    "",  "0=解锁/1=锁定"},

        // ═══ 电池/续航 (8) ═══
        {"leap.battery.soc",            "电池SOC",          "int",    "prop",    "",  "0-100"},
        {"leap.battery.voltage",        "电池电压",         "float",  "prop",    "",  "V"},
        {"leap.battery.current",        "电池电流",         "float",  "prop",    "",  "A"},
        {"leap.battery.temp",           "电池温度",         "float",  "prop",    "",  "°C"},
        {"leap.battery.soh",            "电池SOH",          "int",    "prop",    "",  "0-100"},
        {"leap.range.km",               "续航里程",         "int",    "prop",    "",  "km"},
        {"leap.range.standard",         "标准续航",         "int",    "prop",    "",  "km"},
        {"leap.range.dynamic",          "动态续航",         "int",    "prop",    "",  "km"},

        // ═══ ADAS (20+) ═══
        {"leap.adas.acc",               "自适应巡航ACC",    "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.aeb",               "自动紧急制动AEB",  "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.lka",               "车道保持LKA",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.ldw",               "车道偏离LDW",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.bsd",               "盲区检测BSD",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.fcw",               "前碰撞预警FCW",    "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.dow",               "开门预警DOW",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.rcta",              "后方交叉RCTA",     "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.rcw",               "后碰撞预警RCW",    "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.alc",               "自动变道ALC",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.tsr",               "交通标志TSR",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.hwa",               "高速辅助HWA",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.lcc",               "车道居中LCC",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.tja",               "拥堵辅助TJA",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.ica",               "集成巡航ICA",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.isa",               "智能限速ISA",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.slif",              "限速信息SLIF",     "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.bsi",               "盲区影像BSI",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.ir",                "红外影像IR",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.sdis",              "安全距离SDIS",     "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.sai",               "安全影像SAI",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.auto_parking",      "自动泊车",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.pdc",               "泊车雷达PDC",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.hdc",               "陡坡缓降HDC",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.ccs",               "定速巡航CCS",      "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.speed_limit",       "限速值",           "int",    "system",  "",  "km/h"},

        // ═══ 行车辅助 (8) ═══
        {"leap.adas.fcw_distance",      "FCW距离",          "int",    "system",  "",  "0=近/1=中/2=远"},
        {"leap.adas.ldw_mode",          "LDW模式",          "int",    "system",  "",  "0=关/1=保持/2=预警"},
        {"leap.adas.tired_check",       "疲劳检测",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.face_recognition",  "人脸识别",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.dms",               "驾驶员监测DMS",    "int",    "system",  "",  "0=关/1=低/2=中/3=高"},
        {"leap.adas.low_speed",         "低速提示",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.adas.creep",             "蠕行模式",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.adas.one_pedal",         "单踏板模式",       "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 系统 (10) ═══
        {"leap.system.wifi",            "WiFi",             "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.bluetooth",       "蓝牙",             "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.hotspot",         "热点",             "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.dark_mode",       "暗色模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.day_mode",        "亮色模式",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.pedestrians_alert","行人警示音",       "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.lower_media_during_navi","导航降低媒体","bool",  "prop",    "",  "0=关/1=开"},
        {"leap.system.update",          "系统更新",         "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.usb",             "USB状态",          "bool",   "prop",    "",  "0=关/1=开"},
        {"leap.system.network",         "网络状态",         "int",    "prop",    "",  "状态值"},

        // ═══ 安全 (6) ═══
        {"leap.safety.lock_sound",      "锁车声音",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.safety.find_car",        "寻车声音",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.safety.flash_whistle",   "闪灯鸣笛",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.safety.belt_mute",       "安全带静音",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.safety.start_password",  "启动密码",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.safety.guest_mode",      "访客模式",         "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 里程/时间 (6) ═══
        {"leap.odo.total",              "总里程",           "int",    "prop",    "",  "km"},
        {"leap.odo.current",            "当前里程",         "int",    "prop",    "",  "km"},
        {"leap.odo.trip_a",             "行程A",            "int",    "prop",    "",  "km"},
        {"leap.odo.trip_b",             "行程B",            "int",    "prop",    "",  "km"},
        {"leap.time.current",           "当前时间",         "string", "prop",    "",  "HH:MM"},
        {"leap.time.running",           "行驶时间",         "int",    "prop",    "",  "分钟"},

        // ═══ 灯光高级 (8) ═══
        {"leap.light.star_ring",        "星环灯",           "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.interior",         "车内灯",           "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.reading_left",     "左阅读灯",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.reading_right",    "右阅读灯",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.reading_auto",     "阅读灯自动",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.trunk",            "后备箱灯",         "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.ambient_auto",     "氛围灯自动",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.light.welcome_delay",    "迎宾灯延时(秒)",   "int",    "system",  "15", "15/30/60"},

        // ═══ 车辆信息 (8) ═══
        {"leap.car.vin",                "VIN码",            "string", "prop",    "",  ""},
        {"leap.car.model",              "车型",             "string", "prop",    "",  ""},
        {"leap.car.year",               "年款",             "string", "prop",    "",  ""},
        {"leap.car.color",              "车身颜色",         "int",    "prop",    "",  "颜色代码"},
        {"leap.car.config",             "车辆配置",         "int",    "prop",    "",  "配置代码"},
        {"leap.car.gear",               "档位",             "int",    "prop",    "",  "0=P/1=R/2=N/3=D"},
        {"leap.car.speed",              "车速",             "float",  "prop",    "",  "km/h"},
        {"leap.car.rpm",                "转速",             "int",    "prop",    "",  "rpm"},

        // ═══ 多媒体 (8) ═══
        {"leap.media.source",           "媒体源",           "int",    "system",  "",  "0=FM/1=蓝牙/2=USB/3=在线"},
        {"leap.media.playing",          "播放状态",         "bool",   "system",  "",  "0=暂停/1=播放"},
        {"leap.media.track",            "当前曲目",         "string", "system",  "",  ""},
        {"leap.media.artist",           "艺术家",           "string", "system",  "",  ""},
        {"leap.media.album",            "专辑",             "string", "system",  "",  ""},
        {"leap.media.fm_freq",          "FM频率",           "float",  "system",  "",  "MHz"},
        {"leap.media.fm_preset",        "FM预设",           "int",    "system",  "",  "预设号"},
        {"leap.media.eq_preset",        "均衡器预设",       "int",    "system",  "",  "0=流行/1=摇滚/2=古典/3=爵士"},

        // ═══ 导航 (4) ═══
        {"leap.navi.destination",       "导航目的地",       "string", "system",  "",  ""},
        {"leap.navi.distance",          "剩余距离",         "int",    "system",  "",  "km"},
        {"leap.navi.eta",               "预计到达",         "string", "system",  "",  "HH:MM"},
        {"leap.navi.traffic",           "路况",             "int",    "system",  "",  "0=畅通/1=缓行/2=拥堵"},

        // ═══ 蓝牙/电话 (6) ═══
        {"leap.bt.connected",           "蓝牙连接",         "bool",   "prop",    "",  "0=断开/1=连接"},
        {"leap.bt.device_name",         "蓝牙设备名",       "string", "prop",    "",  ""},
        {"leap.bt.phone_state",         "电话状态",         "int",    "system",  "",  "0=空闲/1=响铃/2=通话"},
        {"leap.bt.phone_number",        "来电号码",         "string", "system",  "",  ""},
        {"leap.bt.phone_name",          "来电姓名",         "string", "system",  "",  ""},
        {"leap.bt.signal",              "蓝牙信号",         "int",    "prop",    "",  "0-100"},

        // ═══ 雨刷 (6) ═══
        {"leap.wiper.front.speed",      "前雨刷速度",       "int",    "system",  "",  "0=关/1=间歇/2=低/3=高"},
        {"leap.wiper.front.auto",       "前雨刷自动",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.wiper.front.sensitivity","前雨刷灵敏度",     "int",    "system",  "",  "1-5"},
        {"leap.wiper.rear.speed",       "后雨刷速度",       "int",    "system",  "",  "0=关/1=间歇/2=低"},
        {"leap.wiper.rear.auto",        "后雨刷自动",       "bool",   "system",  "",  "0=关/1=开"},
        {"leap.wiper.spray",            "喷水",             "bool",   "system",  "",  "0=关/1=开"},

        // ═══ 空气/环境 (4) ═══
        {"leap.env.pm25_inside",        "车内PM2.5",        "int",    "prop",    "",  "μg/m³"},
        {"leap.env.pm25_outside",       "车外PM2.5",        "int",    "prop",    "",  "μg/m³"},
        {"leap.env.temp_inside",        "车内温度",         "float",  "prop",    "",  "°C"},
        {"leap.env.temp_outside",       "车外温度",         "float",  "prop",    "",  "°C"},
    };

    /**
     * 获取参数总数
     */
    public static int getCount() {
        return PARAMS.length;
    }

    /**
     * 按分类获取参数
     */
    public static java.util.List<String[]> getByCategory(String keyword) {
        java.util.List<String[]> result = new java.util.ArrayList<>();
        for (String[] p : PARAMS) {
            if (p[0].contains(keyword) || p[1].contains(keyword)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 搜索参数
     */
    public static java.util.List<String[]> search(String query) {
        java.util.List<String[]> result = new java.util.ArrayList<>();
        String q = query.toLowerCase();
        for (String[] p : PARAMS) {
            if (p[0].toLowerCase().contains(q) || p[1].toLowerCase().contains(q)) {
                result.add(p);
            }
        }
        return result;
    }
}
