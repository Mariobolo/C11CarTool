# C11 车控测试工具 (Android)

零跑 C11 车辆控制功能测试工具，直接安装在车机或手机上使用。

## 功能

- **227 个参数**, **23 个 Tab**
- **已确认接口**: 基于 [c11assistant](https://github.com/Mariobolo/c11assistant) 项目验证
- **广播控制**: 灯光/驾驶模式/场景/空调/系统设置
- **Settings.Global**: 温度/音量/氛围灯/车锁
- **Logcat 监控**: 实时车辆事件 (车门/档位/转向灯/锁车)
- **TTS 语音**: 调用车机讯飞 TTS 引擎
- **双日志**: 应用日志 + 车辆日志
- **搜索**: 按名称/key 模糊搜索参数
- **批量读取**: 一键读取当前 Tab 所有参数

## 构建

### 方式 1: Android Studio (推荐)

1. 用 Android Studio 打开 `android_app/` 目录
2. 等待 Gradle 同步完成
3. Build → Build APK

### 方式 2: 命令行

```bash
cd android_app

# 需要: JDK 17+, Android SDK (API 28, Build Tools 28.0.3)
# 设置 local.properties:
#   sdk.dir=/path/to/Android/Sdk

./gradlew assembleDebug
```

### 方式 3: build.sh

```bash
cd android_app
bash build.sh
```

## 部署

```bash
# 安装
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 授权 (获取系统设置写入权限)
adb shell pm grant com.c11.cartool android.permission.WRITE_SECURE_SETTINGS
```

## 已确认的控制接口

### Settings.Global (直接读写)

| 属性 | 范围 | 功能 |
|------|------|------|
| `C11_CALL` | 0-100 | 电话音量 |
| `C11_NAVI` | 0-100 | 导航音量 |
| `C11_MUSIC` | 0-100 | 媒体音量 |
| `strCar1409` | 16-30 | 主驾温度 |
| `strCar1410` | 16-30 | 副驾温度 |
| `strCar100006` | 0/1 | 空调界面 |
| `strCar1800` | 0/1 | 氛围灯开关 |
| `strCar8867` | 0-16 | 氛围灯颜色 |
| `SPEECH_SPEAK` | 0/1 | 语音开关 |
| `strCarVehicleLock` | 0/1 | 车锁状态 |

### 广播控制 (已确认可用)

| 广播 | Extra | 功能 |
|------|-------|------|
| `com.leapmotor.speech.tocarcontrol` | `CARLIGHT_JINGUANG` | 近光灯 |
| `com.leapmotor.speech.tocarcontrol` | `CARLIGHT_REARFOGCTL` | 后雾灯 |
| `com.leapmotor.speech.tocarcontrol` | `CARLIGHT_SHEKUODENG` | 示廓灯 |
| `com.leapmotor.speech.tocarcontrol` | `PEDESTRIANS_ALERT` | 行人警示音 |
| `com.leapmotor.speech.tocarcontrol` | `MMI_DRIVER_MODE_SET` | 驾驶模式 (0-4) |
| `com.leapmotor.speech.tocarcontrol` | `GUARD_MODE` | 守护模式 |
| `com.leapmotor.speech.tocarcontrol` | `REST_MODE` | 小憩模式 |
| `com.leapmotor.speech.tocarcontrol` | `CAMPING_MODE` | 露营模式 |
| `com.leapmotor.speech.tocarcontrol` | `POWER_SAVE_MODE` | 省电模式 |
| `com.leapmotor.speech.tocarcontrol` | `SENTINEL_MODE` | 哨兵模式 |
| `com.leapmotor.speech.toairconditioner` | `HVACACMAXREQ` | 最大制冷 |
| `com.leapmotor.speech.tosettings` | `wifi` | WiFi |
| `com.leapmotor.speech.tosettings` | `bluetooth` | 蓝牙 |

### Logcat 事件

| TAG | 匹配字符串 | 含义 |
|-----|-----------|------|
| `D/C11CarSomeIp` | `onMessage eventId: 9123 value:` | 左前门 |
| `D/C11CarSomeIp` | `onMessage eventId: 1110 value:` | 档位 (1=R/2=N/3=D) |
| `D/C11CarSomeIp` | `eventid: 1200 msg:` | 锁车 (0=解锁/1=上锁) |
| `D/C11CarSomeIp` | `onMessage eventId: 9106 value:` | 左转灯 |
| `D/C11CarSomeIp` | `onMessage eventId: 9107 value:` | 右转灯 |

## 参考项目

- [c11assistant](https://github.com/Mariobolo/c11assistant) - C11 车控接口文档和 Java 实现
- [C11Partner](https://github.com/Mariobolo/C11Partner) - C11 车载桌面系统
- [leapconnect](https://github.com/markoceri/leapconnect) - 云端 API 控制

## 注意事项

1. 车机 Android 9 (API 28)，不要使用 AndroidX
2. 部分功能需要 `WRITE_SECURE_SETTINGS` 权限
3. 广播控制依赖车机 ROM，不同版本可能有差异
4. 建议加入电池优化白名单保持后台运行
