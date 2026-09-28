# 毛玻璃 / 仿 iOS 主题 — 审查报告（2026-09-28）

> 对照《毛玻璃/仿 iOS 主题审查提示词》逐项核对 v0.3.10 代码，结论：**部件已建，接线缺失**——
> 之前的实现只完成了三个独立部件（Glass / BingWallpaper / ThemeManager），但从未接入主界面，
> 也没有主题切换入口。本轮已补齐接线（标记 `[FIX-20260928]`）。

## 一、完成度评估

| 需求 | 审查前 | 审查后 |
|---|---|---|
| 主题 A：纯色背景 + 毛玻璃模块 | ✅ 已实现（Glass.wallpaper 光晕底 + Glass 卡片） | ✅ |
| 主题 B：必应壁纸（模糊+缓存）+ 毛玻璃模块 | ⚠️ 部件完成未接线（BingWallpaper 全仓零引用；壁纸曾被无条件加载，不分主题） | ✅ 按主题加载，失败自动降级 A |
| Android 9 降级（禁 AGSL/RenderEffect、一次性模糊、无实时模糊） | ✅ 合规（仅注释提及 AGSL/RenderEffect，实际用 RenderScript 一次性模糊+缓存） | ✅ |
| 主题切换入口 | ❌ **缺失**（ThemeManager 有类无引用，无任何 UI 入口） | ✅ 状态条「🎨 主题」A⇄B |
| 切换即时生效 | ❌（无切换） | ✅ applyThemeBackground() 立即重绘 |
| 设置持久化 | ⚠️ ThemeManager 有 SharedPreferences 实现但无人调用 | ✅ `c11_prefs.theme` |
| 断网降级不崩溃 | ⚠️ BingWallpaper 内部有缓存兜底，但未接线无法验证 | ✅ 先铺 A 底，壁纸失败保持 A |

## 二、发现的问题（本轮已修）

| 位置 | 问题 | 严重度 | 修复 |
|---|---|---|---|
| ThemeManager.java（全文件） | 主题管理类写好但全仓零引用——切换、持久化、两主题区分全部悬空 | 高（需求未完成） | DashboardActivity 接线：`themeManager = new ThemeManager(this)` + `applyThemeBackground()` |
| DashboardActivity:114-123（原） | 必应壁纸被**无条件**作为背景加载，违背"主题 B 可开关"设计 | 高 | 改为仅 `isBing()` 时加载；A 纯时光晕底 |
| DashboardActivity 状态条 | 无主题切换入口 | 高 | 新增「🎨 主题」chip，点击 A⇄B 即时生效+Toast 提示 |
| 无 | 局部动态模糊开关 | 低 | 未实现动态模糊（性能优先），无需开关；如需后续再加 |

## 三、代码结构与性能结论

- **职责清晰**：`Glass`（静态玻璃拟态绘制）/ `BingWallpaper`（下载→缩放→RenderScript 模糊→文件缓存）/
  `ThemeManager`（选择+持久化）三者职责单一，符合提示词要求。
- **无高版本 API**：全仓 grep `RenderEffect|AGSL|RuntimeShader|setRenderEffect` 仅命中 2 处注释（明确声明不用），
  实际模糊为 `ScriptIntrinsicBlur`（API 17+），Android 9 安全。
- **无实时模糊**：背景模糊只在壁纸加载时执行一次并缓存，模块用半透明绘制模拟，车机 GPU 压力极小。
- **内存**：壁纸位图按 1920×1080 降采样后模糊，单张约 8MB（ARGB_8888），缓存走文件不驻留双份，无泄漏风险
  （回调判 isFinishing/isDestroyed）。

## 四、同批修复：百分比取值开关 → 滑块

- `MainActivity` 参数页：`0-100`、`16-30`、`0-7` 等**数值区间**参数原先与 bool 一样给 ON/OFF 开关
  （对百分比无意义），改为 SeekBar 滑块（拖动即显示、松手写值、失败标红）；枚举串（含 `=`）与 bool 保持原开关。
- 标记：`[FIX-20260928]`（`parseRange()` + makeParamRow 滑块分支），撤回即删该分支恢复 ON/OFF。

## 五、遗留/建议

1. 参数页枚举型（如空调模式、档位）目前是 SET 文本框，可进一步做下拉/分段选择器。
2. 主题 B 的"局部动态模糊"（如 Dock 玻璃）按提示词为可选项，当前不实现（性能优先）。
3. 必应壁纸为每日一张；如需每小时换可加 `idx` 轮换（现按提示词"当日"实现）。
