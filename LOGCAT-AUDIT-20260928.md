# C11CarTool v0.3.10 — logcat / settings 对接核对报告

- 日期：2026-09-28
- 版本：v0.3.10（versionCode 16），包名 `com.c11.cartool`
- 核对目的：用足量实车 logcat 与权威解析规范，核查 logcat 解析有无对接错误、哪些信号频率更高更易获取、是否存在"本来对的被改成错的"，并同步对照 settings global 属性。

## 一、核对方法（不凭记忆，全部用真实文件逐条验证）

依次读取并用真实数据验证：

1. 解析器现状：`vehicle/LogcatVehicleSource.java`、采集器 `dashboard/DashboardRepository.java`
2. 权威规范：《零跑C11车辆日志解析规范》、`tag_classification_full.txt`
3. 设备端真实抓取（`logcat -d -v brief -s <TAG>`）：`c11_logcat_tags_20260921_201301.txt`（119 行，brief 格式，停车/怠速）
4. 一键全测报告：`c11_workbench_20260921_201301.txt`
5. settings 权威键：`vehicle/RealVehicleKeys.java`，对照真机原始 dump：`c11_env_20260921_201043.txt`
6. 在海量历史日志中 Grep 车窗（21180–21183）、制动踏板（11166）核对值域

## 二、核心结论

1. **当前 v0.3.10 的 logcat 解析器与真机 brief 格式完全兼容，六条正则全部正确，未发现"本来对的改成错的"。**
2. **settings global 渠道 26 个权威键全部在真机 dump 中真实存在、拼写完全一致，无写死、无拼错。**
3. 停车/怠速状态下 SomeIP 动力事件为 0、GPS/Energy 为 0 属正常（车辆静止、缓冲无记录），并非解析失败。
4. 唯一"已抓取未利用"的信号是 **GearMonitorService 档位**，本次已接入为档位第三渠道。

### 曾出现的一次误判（已排除）

核对初期曾依据压缩摘要中的旧描述，以为 `RE_XML` 锚定了 `C11CarXml:` 字面前缀，在 brief 格式（`D/C11CarXml( 1448): node_name : Time setTextContent: 20:13`，TAG 后带 `(PID)`）下会失效。读取当前工作区真实代码后确认：**当前 `RE_XML` 已是修正过的正确版本**——不锚 TAG 前缀，值用宽松捕获 `(.+?)\s*$`，TAG 过滤由外层 `line.contains("C11CarXml")` 负责。真实行可正确捕获 `Time=20:13`，与一键全测报告"XML 字段 1 条、Time 抓到但归未识别"完全吻合。

## 三、正则对 brief 格式的兼容性核对

| 正则 | 用途 | 是否锚 TAG | 结论 |
|---|---|---|---|
| `RE_EVENT` | `eventId: X value: Y`（onMessage） | 否 | ✓ 正确 |
| `RE_EVENTID_MSG` | `eventid: X msg: Y`，外层 contains C11CarSomeIp/Someip/onMessage | 外层 contains | ✓ 正确 |
| `RE_XML` | `node_name : 字段 setTextContent: 值`，宽松取值 | 否（外层 contains） | ✓ 正确，能取 `20:13`/小数/负数 |
| `RE_TPMS` | `TPMSBean{pos=.. singleTirePress=.. singleTireTemp=..}` | 否 | ✓ 实测 44 条 |
| `RE_GPS` | `D:(lat lon alt) course.. tickTime.. status..` | 否 | ✓ 正确 |
| `RE_ENERGY` | `updateEnergyData: EV_MILE.. EV_TIME.. EV_AVERAGE_CONSUME..` | 否 | ✓ 正确 |
| `RE_GEAR_MONITOR`（本次新增） | `检测到X档` / `档位更新: X` | 外层 contains GearMonitor | ✓ 新增 |

## 四、信号频率与易获取性（规范 + 实测）

实测样本：119 行（20:13，停车/怠速）。胎压 44 条、XML Time 1 条、SomeIP 动力 0、GPS 0、Energy 0。

| 信号 | 渠道（TAG / eventId） | 频率 | 停车是否有 | 易获取性 |
|---|---|---|---|---|
| 胎压 / 胎温 | zza · TPMSBean | 四轮循环，最高频（119 行中 44 条） | 有 | ★★★ 最易，最稳定 |
| 系统时间 | C11CarXml · Time | 约每分钟 1 条 | 有 | ★★★ |
| 车速 | SomeIP · 1108 | 约 100ms（行驶） | 无（静止） | ★★ 行驶中 |
| 制动踏板开度 | SomeIP · 11166 | 约 100ms，float 连续值（实测 0.0/1.0，注释称可达 34.0） | 0.0 | ★★ 行驶中 |
| 电池总电压 | SomeIP · 3130 | 约 1.5s | 本轮无 | ★★ |
| 电池总电流 | SomeIP · 3131 | 约 1.5s | 本轮无 | ★★ |
| 档位 | 1110 / XML gear / **GearMonitor（新）** | 换挡瞬间 | 本轮无 | ★★ |
| 标准/动态续航 | XML · rangeStandard / rangeDynamic | 启动 + 周期 | 本轮无 | ★★ |
| 电量 | SomeIP · 3162 | 低频 | 本轮无 | ★ |
| 行程三件套 | EnergyDataBinder | 行驶 2–60s，停车低频 | 无 | ★ |
| GPS 定位 | LocationDataC23Handler | 定位时 | 无 | ★ |

口径提醒（不可混用）：
- logcat 空调温度 28110/28111 直接为 ℃（16.0–32.0）；settings 渠道 strCar1409/1410 为 ℃×2 半度编码（52=26℃，待最终确认）。
- 功率 kW = 3130 × 3131 / 1000；胎压 kPa/100 = bar。

## 五、本次代码改进：GearMonitorService 档位第三渠道

原车 `GearMonitorService` 直接输出中文档位（`🚗 检测到N档 (通过AIDL)`、`档位更新: N`），此前已在抓取 TAG 列表但解析器未利用。本次接入，与 eventId 1110、XML gear 互为多渠道：

- `vehicle/LogcatVehicleSource.java`
  - 新增正则 `RE_GEAR_MONITOR = (?:检测到|档位更新\s*:?)\s*([PNDR])\s*档?`
  - `Result` 新增独立字段 `gearByMonitor`（沿用 `gps` 的独立 State 设计，count=0 即本轮未出现，不填默认值）
  - `parse` 新增 GearMonitor 分支；`summarize` 新增「档位（GearMonitorService·AIDL）」输出
- `dashboard/DashboardRepository.java`
  - 档位快照组装增加第三优先级：XML gear → eventId 1110 → gearByMonitor
  - `buildRows` 信号清单新增「档位(GearMonitor)」行（分组：动力/底盘，来源：GearMonitorService/AIDL）

## 六、settings 渠道核对（26 键全部一致）

`RealVehicleKeys.KEYS` 的 26 个键逐一在真机 env dump 中找到、拼写一致，例如：strCarAirSwitch=0、strCarAirWind=0、strCarAirStatus=3、strCarAirInner=0、strCarFrontDefrost=0、strCarRearDefrost=0、strCar1409=52、strCar1410=52、strCarVehicleLock=1、strCarChildLock=0、strCarTrunkState=1、strCarPm25=2、strCarSentinelMode=0、C11_MUSIC=6、C11_NAVI=12、C11_CALL=51、C11_SPEECH=24、SPEECH_SPEAK=0。

### 三套 settings 数据的职责区分（避免重复与误解）

- `RealVehicleKeys.KEYS`：一键全测（WorkbenchTest）遍历读取的**实证键白名单**（26 个）。
- `DashboardRepository.SETTINGS_META`：仪表盘主界面信号清单的元数据，**已收录 40+ 键**，包括 strCar4gLevel（4G 等级）、strCarEntertainmentDisplay（娱乐屏状态）、strCarSlowChargeLockSts（慢充锁）、strCarCCConectSts（CarPlay 连接）、座椅系列等；含义未标定者以 raw 原样展示，不臆测。
- 一键全测的 env dump：保存 `settings list global` **全量原始键值**，即使未列入白名单也不丢失，可离线标定。

故本次核对结论：**无需向 RealVehicleKeys 补键**——主界面由 SETTINGS_META 全量渲染，原始全量由 env dump 留存，三套职责清晰、低耦合。

## 七、仍需标定 / 待办

### 需一次上机标定（无实车做不了）

1. **车窗归属标定（重要）**：仅 21180=右前被管家配置验证；21181=左前、21182=右后、21183=左后为按值域推断。历史日志见 2023-12-27 一次 21180=10、2026-08-28 一次 21181=10，但日志不标注操作的是哪扇窗。建议上机时逐窗操作（左前/右前/左后/右后各开 10%），记录变化的 eventId，一次确认。
2. 空调运行模式 strCarAirStatus=3 的真实语义（口述"制冷"与 1 重复，疑除湿/强冷）。
3. 内外循环"自动"=2 是否真切到 AUTO。
4. 语音实体名试错：座椅/后视镜/行人警示/阅读灯，逐个点，无效的报按钮名以便换候选。

### 已排期未做（跨模块，建议单独一轮）

- MainActivity 生命周期：`Logger.setCallback` 静态回调泄漏（P0）。
- WebServer 安全：`/api/*` 除 control 外无鉴权、请求体无上限、连接无超时。
- VehicleParams 结构化（`String[][]` → ParamDef）+ 中文键 `leap.hvac循环模式` + 温度×2 单位注释统一。

### 待拍板

- 死代码处置二选一：`desktop/`（约 930 行）、`CarControlFragment`（约 358 行）、`media/` 转发链路 —— 删除，还是立项接线。

### 锦上添花（记录在案，不急）

- 参数页枚举型做下拉/分段选择器；Dock 局部动态模糊开关；P3 规范项（死代码内注释、未用常量等）。

## 八、构建与交付

- 构建：JDK17（dragonwell-17）+ Gradle 7.5.1，`clean assembleDebug --no-daemon`，**BUILD SUCCESSFUL in 24s**。
- 最新 APK：`release/apk/C11CarTool_v0.3.10_debug.apk`（623216 字节，v0.3.10 / code16），同签名可覆盖安装。
- 旁支 `C11DdsBridge-stage1-v0.1.8.20260922.apk` 保留，不与主程序混淆。
