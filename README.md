# C11 车控测试工具 (Android)

零跑 C11 车辆控制功能测试工具，直接安装在车机或手机上使用。

## 版本

**当前版本: v0.2.20260916 (测试版)**

### v0.2.20260916 更新内容

- **重构 Sh.java**: 所有命令返回完整 exit code/stdout/stderr，默认 10 秒超时，独立线程读取输出防死锁
- **重构 Logger.java**: 8 级日志（DEBUG/INFO/OK/WARN/ERROR/STEP/TITLE/CMD），时间戳+标签，1000 条上限防溢出
- **移除假 ADB 连接检测**: 之前"连接对号"只是检查本地 `id` 命令，现已改为显示真实权限等级（APP/SHELL/ROOT）和设备信息
- **新增诊断模式**: 一键运行 8 项诊断（基本信息/零跑属性/设置读取/车辆设置/广播测试/logcat测试/网络状态/权限总结），自动保存报告
- **修复车辆状态读取**: 读取失败显示红色"读取失败"而非模糊的"--"，超时显示橙色"超时"
- **新增 CrashHandler**: 全局异常捕获，崩溃时自动保存完整日志到 `/sdcard/c11_crash_logs/`
- **版本号改为 0.2.日期戳格式**

### 已知限制（普通应用权限）

| 操作 | 普通应用 | 说明 |
|------|---------|------|
| getprop | ✅ 大部分可读 | |
| settings get | ✅ 大部分可读 | |
| settings put | ❌ 需 WRITE_SECURE_SETTINGS | |
| am broadcast | ⚠️ 可执行，接收方可能拒绝 | 需实车诊断确认 |
| logcat | ❌ 只能看自己的日志 | Android 4.1+ 限制 |

**首次使用请运行"诊断模式"确认实际权限能力。**

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
- **诊断模式**: 一键检查权限/命令/广播/logcat 能力
- **全局异常捕获**: 崩溃日志自动保存

## 构建

### 方式 1: Android Studio (推荐)

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成
3. Build → Build APK

### 方式 2: 命令行

```bash
# 需要: JDK 17+, Android SDK (API 28, Build Tools 28.0.3)
# 设置 local.properties:
#   sdk.dir=/path/to/Android/Sdk

./gradlew assembleDebug
```

### 方式 3: build.sh

```bash
bash build.sh
```

## 部署

```bash
# 安装
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# 授权 (获取系统设置写入权限 - 需要 shell 权限)
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
2. 部分功能需要 `WRITE_SECURE_SETTINGS` 权限（普通应用无此权限）
3. 广播控制依赖车机 ROM，不同版本可能有差异
4. 建议加入电池优化白名单保持后台运行
5. 普通应用无法读取系统 logcat，只能读取 APP 自己的日志
6. 首次使用请运行"诊断模式"确认实际权限能力
