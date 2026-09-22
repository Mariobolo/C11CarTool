# C11CarTool（C11 车控）

零跑 C11（2023 款，Android 9 车机）第三方车机工具：可视化仪表盘 + 车辆控制 + 工程诊断。
个人学习与车友交流项目，非零跑官方应用。

**当前版本：v0.3.5（公开测试版）**

## 功能

- **可视化仪表盘**：深色车机风格、固定网格、横屏全屏，打开即用
  - 数据方块：电量 / 续航 / 电压 / 车外温度 / 四轮胎压胎温 / 车门·后备箱·前机盖 / PM2.5 / 四路音量（媒体、语音、导航、通话）
  - 空调大卡：主副驾温度、风量、AC、最大制冷、内外循环、前后除霜
  - 车身控制：近光 / 示廓 / 后雾灯、360 全景、左右儿童锁、后备箱、车窗、后视镜加热、整车锁（实验性）
  - 底部 Dock：空调、前/后除雾、循环、360、后备箱、锁车 / 解锁、Web 遥控
- **数据自动刷新**：每 5 秒聚合采集，无数据一律显示 `--`，不放假值
- **Web 遥控**：App 内置 HTTP 服务，同一局域网下手机浏览器访问即可车控与查看日志（默认 8080，端口冲突自动切换）
- **工程模式**：一键诊断、一键全测、车控实验矩阵、日志导出（仪表盘右上角齿轮进入）
- **全局崩溃捕获**：崩溃日志自动保存到 `/sdcard/c11_crash_logs/`

## 车控通道（均来自实车日志标定）

| 通道 | 能力 | 说明 |
|---|---|---|
| `settings global` 真机键 | 空调 / 风量 / 温度 / 循环 / 除霜 / 儿童锁 / 四路音量等 | 经 ADB shell 写入，系统 observe 后真实控车，回读一致才算成功 |
| 讯飞语音 `handMessage` 广播 | 360 全景、左右儿童锁、后备箱、四车窗 | 复用系统语音服务控制语义 |
| 旧版语音广播 | 最大制冷、近光 / 示廓 / 后雾灯 | `com.leapmotor.speech.*` |
| logcat 被动解析 | 车速 / 档位 / 电压电流 / 电量 / 车外温度 / TPMS / 车门 | 只读，用于仪表盘数据展示 |

> 标注「实验性」的功能（整车锁等）通道尚未在实车完全标定，可能无效，使用风险自负。

### 已确认的 Settings.Global 键（部分）

| 键 | 范围 | 功能 |
|---|---|---|
| `strCarAirSwitch` | 0/1 | 空调开关 |
| `strCarAirWind` | 0-7 | 风量 |
| `strCar1409` / `strCar1410` | 温度×2（如 52 = 26℃） | 主驾 / 副驾温度 |
| `strCarAirInner` | 0/1 | 内外循环 |
| `strCarFrontDefrost` / `strCarRearDefrost` | 0/1 | 前 / 后除霜 |
| `strCarChildLock` | 0/1 | 儿童锁 |
| `C11_MUSIC` / `C11_NAVI` / `C11_SPEECH` / `C11_CALL` | 0-100 | 四路音量 |
| `strCarVehicleLock` | 0/1 | 车锁状态（实验性） |

## 屏幕适配

- 中控屏 1920×1080：12 列 × 7 行完整布局
- 仪表屏 / 副驾屏 1920×720：12 列 × 4 行精简布局（按分辨率自动切换）

## 构建

- JDK 17、Gradle 7.5.1、Android SDK Platform 28（Android 9）
- 无 AndroidX、无第三方依赖，纯 Java 8

```bash
gradle assembleDebug --offline
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 部署与授权

```bash
adb install -r -t app-debug.apk
# 车机需先通过系统自带 Demo 开启 Wi-Fi ADB（persist.sys.leap.wifiadb=1）
# App 会自动连接本机 ADB（shell 权限）并保活
```

## 免责声明

本项目仅用于个人学习与车机功能研究，不修改车机系统分区。
车辆控制涉及行车安全，请勿在行驶中操作；实验性功能可能不生效。
因使用本软件造成的一切后果由使用者自行承担。

## 参考项目

- [c11assistant](https://github.com/Mariobolo/c11assistant) - C11 车控接口文档与 Java 实现
- [C11Partner](https://github.com/Mariobolo/C11Partner) - C11 车载桌面系统
- [leapconnect](https://github.com/markoceri/leapconnect) - 云端 API 控制
