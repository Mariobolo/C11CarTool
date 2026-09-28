package com.c11.cartool.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.c11.cartool.AppInfo;
import com.c11.cartool.CrashHandler;
import com.c11.cartool.DiagnosticRunner;
import com.c11.cartool.LogExport;
import com.c11.cartool.LogStore;
import com.c11.cartool.Logger;
import com.c11.cartool.Sh;
import com.c11.cartool.WebPages;
import com.c11.cartool.WebServer;
import com.c11.cartool.vehicle.VehicleController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 车机主界面（横屏全屏，基于 {@link Activity}，不依赖 AndroidX）。
 *
 * <p>外层 {@link ViewFlipper} 三页：
 * <ul>
 *   <li>页0 网格化桌面：纵向 {@link ScrollView} + {@link FlowGridLayout}，
 *       全部小模块（1×1 / 2×1）按分组流式排布、宽度自适应、自动换行；</li>
 *   <li>页1 全车信号清单（状态条「📊 信号」，可搜索 / 折叠）；</li>
 *   <li>页2 全部车控（状态条「🎛 车控」）。</li>
 * </ul>
 * 数据由 {@link DashboardRepository} 后台 5s 一轮驱动；一键诊断、日志导出、
 * 手机扫码测控均前端直达，无工程模式。
 */
public class DashboardActivity extends Activity implements DashboardRepository.Callback {

    private VehicleController vc;
    private WebServer webServer;
    private DashboardRepository repository;

    private ViewFlipper pageSwitcher;
    private SignalListPage signalPage;
    private ControlListPage controlPage;

    private FlowGridLayout flow;
    private final Map<String, TileView> tiles = new LinkedHashMap<>();

    // v0.3.10 仪表 / 波形图表
    private GaugeView speedGauge, socGauge, powerGauge;
    private LineChartView voltageChart, currentChart, powerChart, speedChart;

    private TextView adbChip, gearChip, speedChip, powerChip, timeChip, webChip;

    private static final String[] WIN_TITLES = {"主驾车窗", "副驾车窗", "左后车窗", "右后车窗"};

    private volatile DashboardSnapshot latest;
    private Sh.StateListener stateListener;
    private final Handler ui = new Handler();

    // [FIX-20260928] 开关合并状态点（绿=已开 红=已关 白=未知）+ 门盖最近有效值缓存 + 循环三态
    private final java.util.Map<String, Boolean> pairState = new java.util.HashMap<>();
    private final int[] lastDoor = {-1, -1, -1, -1, -1, -1};
    private int innerLoopMode = 1;

    // [v0.3.11] 低频数据「最后已知值」缓存：读到一次持续显示，新值有效才更新，从未读到才 --（功能6）
    private final java.util.Map<String, Integer> cacheInt = new java.util.HashMap<>();
    private final java.util.Map<String, Float> cacheFloat = new java.util.HashMap<>();
    private final java.util.Map<String, int[]> cacheSlider = new java.util.HashMap<>(); // {min,max,value}
    private String cacheGear = null;

    // [FIX-20260928] 主题（A=纯色光晕 / B=必应壁纸），ThemeManager 持久化，切换入口见状态条 🎨
    private ThemeManager themeManager;
    private LinearLayout rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);
        // 日志始终落盘（下载目录/软件同名目录，logcat + 软件日志双体系）
        LogStore.init(this);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        vc = new VehicleController(this);
        webServer = new WebServer();
        webServer.setVehicleController(vc);

        Logger.clearCmdLog();
        Logger.title(AppInfo.TITLE + " 启动");
        Logger.info("设备: " + android.os.Build.MODEL + "，Android " + android.os.Build.VERSION.RELEASE);

        themeManager = new ThemeManager(this);   // [FIX-20260928] 主题选择+持久化（原先有类无接线）
        buildUi();
        startWebAuto();

        Sh.setContext(this);
        stateListener = new Sh.StateListener() {
            @Override public void onAdbStateChanged(boolean connected, int uid) {
                ui.post(DashboardActivity.this::updateStatusBar);
                // [v0.3.11] ADB 一连上立即补采一轮（含 -b all 启动历史），避免错过初始化信号（功能7）
                if (connected && repository != null) repository.requestRefresh();
            }
        };
        Sh.addStateListener(stateListener);
        Sh.startKeepAlive();
        Sh.autoConnectLocal();

        repository = new DashboardRepository(ui, this);
        repository.start();
    }

    // ═══════════════════════════════════════════════
    //  UI 构建
    // ═══════════════════════════════════════════════

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        rootView = root;
        applyThemeBackground();   // [FIX-20260928] 按主题 A/B 应用背景（壁纸失败自动降级主题 A）

        root.addView(buildStatusBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        pageSwitcher = new ViewFlipper(this);
        pageSwitcher.setAutoStart(false);
        pageSwitcher.setInAnimation(this, android.R.anim.fade_in);
        pageSwitcher.setOutAnimation(this, android.R.anim.fade_out);
        pageSwitcher.addView(buildGridPage());

        signalPage = new SignalListPage(this);
        pageSwitcher.addView(signalPage);

        controlPage = new ControlListPage(this, vc, new ControlListPage.CommandRunner() {
            @Override public void run(String label, Callable<Boolean> task) {
                runCommand(label, task);
            }
        });
        pageSwitcher.addView(controlPage);

        root.addView(pageSwitcher, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildDock(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
    }

    /** 网格化桌面：纵向滚动 + 自适应流式网格 */
    private View buildGridPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        flow = new FlowGridLayout(this);
        buildTiles();
        scroll.addView(flow, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    // ── 分组与小模块装配 ──

    private void buildTiles() {
        // ── 仪表 · 波形图表 ──
        group("📊 仪表 · 波形");
        speedGauge = gaugeTile("车速", 0, 200, "km/h", DashboardTheme.BLUE);
        socGauge = gaugeTile("电量", 0, 100, "%", DashboardTheme.GREEN);
        powerGauge = gaugeTile("功率", -60, 60, "kW", DashboardTheme.ORANGE);
        voltageChart = chartTile("电压波形", "V", DashboardTheme.CYAN);
        currentChart = chartTile("电流波形", "A", DashboardTheme.PURPLE);
        powerChart = chartTile("功率波形", "kW", DashboardTheme.ORANGE);
        speedChart = chartTile("车速波形", "km/h", DashboardTheme.BLUE);

        // 行车信息
        group("🚗 行车信息");
        tile("soc", TileView.Type.VALUE, "电量", 1, 1);
        tile("range", TileView.Type.VALUE, "续航", 1, 1);
        tile("volt", TileView.Type.VALUE, "电压", 1, 1);
        tile("current", TileView.Type.VALUE, "电流", 1, 1);
        tile("power", TileView.Type.VALUE, "功率", 1, 1);
        tile("speed", TileView.Type.VALUE, "车速", 1, 1);
        tile("gear", TileView.Type.VALUE, "档位", 1, 1);
        tile("outtemp", TileView.Type.VALUE, "车外温度", 1, 1);
        tile("pm25", TileView.Type.VALUE, "PM2.5", 1, 1);

        // 音量（滑块 2×1）
        group("🔊 音量");
        slider("vol_music", "媒体音量", 0, 100, "",
                v -> vc.setGlobalKey("C11_MUSIC", String.valueOf(v)));
        slider("vol_navi", "导航音量", 0, 100, "",
                v -> vc.setGlobalKey("C11_NAVI", String.valueOf(v)));
        slider("vol_speech", "语音音量", 0, 100, "",
                v -> vc.setGlobalKey("C11_SPEECH", String.valueOf(v)));
        slider("vol_call", "通话音量", 0, 100, "",
                v -> vc.setGlobalKey("C11_CALL", String.valueOf(v)));

        // 胎压胎温
        group("🛞 胎压胎温");
        tile("tire_fl", TileView.Type.VALUE, "左前轮", 1, 1);
        tile("tire_fr", TileView.Type.VALUE, "右前轮", 1, 1);
        tile("tire_rl", TileView.Type.VALUE, "左后轮", 1, 1);
        tile("tire_rr", TileView.Type.VALUE, "右后轮", 1, 1);

        // 车门 / 舱盖
        group("🚪 车门 / 舱盖");
        tile("door_fl", TileView.Type.VALUE, "左前车门", 1, 1);
        tile("door_fr", TileView.Type.VALUE, "右前车门", 1, 1);
        tile("door_rl", TileView.Type.VALUE, "左后车门", 1, 1);
        tile("door_rr", TileView.Type.VALUE, "右后车门", 1, 1);
        tile("trunk_state", TileView.Type.VALUE, "后备箱", 1, 1);
        tile("hood", TileView.Type.VALUE, "前机盖", 1, 1);

        // 空调座舱
        group("❄ 空调座舱");
        slider("temp_driver", "主驾温度", 16, 32, "℃", vc::setAcTemperatureDriver);
        slider("temp_pass", "副驾温度", 16, 32, "℃", vc::setAcTemperaturePassenger);
        slider("fan", "风量", 1, 7, "档", vc::setAcFanSpeed);
        toggle("ac", "空调", "ac");
        toggle("acmax", "最大制冷", "max");
        innerLoopTile();   // [FIX-20260928] 三态轮转（外→内→自动），补齐自动模式
        toggle("frontdef", "前除霜", "front");
        toggle("reardef", "后除霜", "rear");
        toggle("mirrorheat", "后视镜加热", "mirror");
        toggle("winlock", "车窗锁", "winlock");
        // [FIX-20260928] 空调模式按用户实测映射标注：0=自动 1=制冷 2=制热 3=制冷(待确认)
        action("airraw0", "模式·自动", () -> vc.setAirMode(0));
        action("airraw1", "模式·制冷", () -> vc.setAirMode(1));
        action("airraw2", "模式·制热", () -> vc.setAirMode(2));
        action("airraw3", "模式·制冷3", () -> vc.setAirMode(3));
        actionRun("acpage", "空调界面", vc::openAcPage);

        // [FIX-20260928] 阅读灯：开/关合并为单按钮 + 状态点；通道改 handMessage（见 VehicleController.readingLight）
        group("📖 阅读灯");
        pairTile("read_fl", "前左阅读灯", () -> vc.readingLight("前左阅读灯", true), () -> vc.readingLight("前左阅读灯", false));
        pairTile("read_fr", "前右阅读灯", () -> vc.readingLight("前右阅读灯", true), () -> vc.readingLight("前右阅读灯", false));
        pairTile("read_rl", "后左阅读灯", () -> vc.readingLight("后左阅读灯", true), () -> vc.readingLight("后左阅读灯", false));
        pairTile("read_rr", "后右阅读灯", () -> vc.readingLight("后右阅读灯", true), () -> vc.readingLight("后右阅读灯", false));

        // 车窗（点击开滑杆）
        group("🪟 车窗");
        for (int i = 0; i < 4; i++) {
            tile("win_" + i, TileView.Type.ACTION, WIN_TITLES[i], 1, 1)
                    .setListener(press(this::openWindowSlider));
        }

        // [FIX-20260928] 儿童锁：开/关合并 + 状态点
        group("👶 儿童锁");
        pairTile("child_l", "左童锁", vc::leftChildLockOn, vc::leftChildLockOff);
        pairTile("child_r", "右童锁", vc::rightChildLockOn, vc::rightChildLockOff);

        // [FIX-20260928] 灯光：开/关合并 + 状态点（绿=已开 红=已关）
        group("💡 灯光");
        pairTile("low", "近光", vc::lowBeamOn, vc::lowBeamOff);
        pairTile("high", "远光", vc::highBeamOn, vc::highBeamOff);
        pairTile("pos", "示廓灯", vc::positionLightOn, vc::positionLightOff);
        pairTile("ffog", "前雾灯", vc::frontFogOn, vc::frontFogOff);
        pairTile("fog", "后雾灯", vc::fogLightOn, vc::fogLightOff);
        actionRun("auto", "自动灯光", vc::autoLights);
        actionRun("closeall", "关闭全部", vc::closeAllLights);

        // 连接 · 环境 · 场景
        group("📶 连接 · 环境 · 场景");
        tile("bt", TileView.Type.VALUE, "蓝牙", 1, 1);
        tile("wifi", TileView.Type.VALUE, "WiFi", 1, 1);
        tile("ble", TileView.Type.VALUE, "蓝牙BLE", 1, 1);
        tile("ptc", TileView.Type.VALUE, "PTC出风温度", 1, 1);
        tile("ambient", TileView.Type.VALUE, "氛围灯", 1, 1);
        tile("airstatus", TileView.Type.VALUE, "空调模式", 1, 1);
        toggle("sentinel", "哨兵模式", "sentinel");
        toggle("speechspeak", "语音播报", "speechspeak");
        actionRun("scene_rest", "⚠小憩模式", () -> vc.scene("REST_MODE"));
        actionRun("scene_camp", "⚠露营模式", () -> vc.scene("CAMPING_MODE"));
        actionRun("scene_save", "⚠省电模式", () -> vc.scene("POWER_SAVE_MODE"));
        actionRun("scene_guard", "⚠守护模式", () -> vc.scene("GUARD_MODE"));
        actionRun("scene_senti", "⚠哨兵(广播)", () -> vc.scene("SENTINEL_MODE"));
        // [FIX-20260928] 行人警示音 / 后视镜折叠：合并开/关 + 状态点；通道改 handMessage 语音家族
        pairTile("ped_pair", "行人警示音", () -> vc.pedestrianAlert(true), () -> vc.pedestrianAlert(false));
        pairTile("mirror_fold", "后视镜折叠", () -> vc.mirrorFold(true), () -> vc.mirrorFold(false));
        actionRun("force_charge", "⚠强制充电",
                () -> vc.shellSetProp("leap.energy.force_charge", "1"));

        // [FIX-20260928] 后备箱/车门锁：开/关合并 + 状态点（门盖快照回写）
        group("🧰 车身 / 工具");
        pairTile("trunk_pair", "后备箱", vc::openTrunk, vc::closeTrunk);
        pairTile("lock_pair", "车门锁", vc::lockCar, vc::unlockCar);
        actionRun("scan", "📱 扫码测控", this::showWebInfoDialog);
        actionRun("diag", "🩺 一键诊断", this::runDiagnostic);
        actionRun("log", "📋 导出日志", this::exportLogs);
    }

    // ── 装配 helper ──

    private void group(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(GridDimens.SP_GROUP);
        tv.setTextColor(DashboardTheme.TEXT);
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(4), dp(8), dp(4), dp(2));
        flow.addGroupHeader(tv);
    }

    // ── v0.3.10 图表 / 滑块 / 阅读灯 helper ──

    private GaugeView gaugeTile(String title, float min, float max, String unit, int color) {
        GaugeView g = new GaugeView(this, title);
        g.setRange(min, max);
        g.setUnit(unit);
        g.setColor(color);
        flow.addCell(g, 2, 2);
        return g;
    }

    private LineChartView chartTile(String title, String unit, int color) {
        LineChartView c = new LineChartView(this, title);
        c.setUnit(unit);
        c.setColor(color);
        flow.addCell(c, 3, 2);
        return c;
    }

    private void slider(String id, String label, int min, int max, String unit,
                        java.util.function.IntConsumer handler) {
        TileView t = tile(id, TileView.Type.SLIDER, label, 2, 1);
        t.setSliderUnknown();
        t.setListener(new TileView.Listener() {
            @Override public void onSlider(int v) {
                // P0 修复：SeekBar 回调在主线程，写入必须放子线程（直接调 Sh.run 会持锁卡死）
                Sh.submitAsync(() -> {
                    boolean ok = false;
                    try { handler.accept(v); ok = true; }
                    catch (Exception e) { Logger.error("滑块", label + " 写入异常", e); }
                    final boolean fOk = ok;
                    runOnUiThread(() -> Toast.makeText(DashboardActivity.this,
                            label + " " + v + (fOk ? " ✅" : " ❌"), Toast.LENGTH_SHORT).show());
                    if (repository != null) ui.postDelayed(() -> repository.requestRefresh(), 1200);
                });
            }
        });
    }

    // [FIX-20260928] 开关合并：单按钮 + 红/绿/白小点（绿=已开 红=已关 白=未知）；点击翻转
    private void pairTile(String id, String label, Callable<Boolean> onTask, Callable<Boolean> offTask) {
        tile(id, TileView.Type.ACTION, pairLabel(id, label), 1, 1)
                .setListener(press(() -> {
                    boolean next = !Boolean.TRUE.equals(pairState.get(id));
                    pairState.put(id, next);
                    t(id).setLabel(pairLabel(id, label));
                    runCommand(label + (next ? "开" : "关"), () -> next ? onTask.call() : offTask.call());
                }));
    }

    private String pairLabel(String id, String label) {
        Boolean st = pairState.get(id);
        String dot = st == null ? "⚪" : (st ? "🟢" : "🔴");
        return dot + " " + label;
    }

    /** 有实证状态键时用快照回写小点（覆盖本地乐观态） */
    private void overlayPair(String id, String label, int v) {
        if (v < 0) return;   // 无值时保留本地/未知态
        pairState.put(id, v == 1);
        t(id).setLabel(pairLabel(id, label));
    }

    // [FIX-20260928] 内外循环三态轮转（外→内→自动），补齐缺失的自动模式
    private void innerLoopTile() {
        tile("innerloop", TileView.Type.ACTION, loopWord(innerLoopMode), 1, 1)
                .setListener(press(this::innerLoopNext));
    }

    private void innerLoopNext() {
        int next = (innerLoopMode + 1) % 3;
        runCommand(loopWord(next), () -> vc.setAirInnerLoopMode(next));
        innerLoopMode = next;
        t("innerloop").setLabel(loopWord(innerLoopMode));
    }

    private static String loopWord(int m) {
        return "循环·" + (m == 0 ? "外" : m == 1 ? "内" : "自动");
    }

    private TileView tile(String id, TileView.Type type, String label, int sx, int sy) {
        TileView t = new TileView(this, type, label);
        flow.addCell(t, sx, sy);
        tiles.put(id, t);
        return t;
    }

    private TileView t(String id) {
        return tiles.get(id);
    }

    private void toggle(String id, String label, String key) {
        tile(id, TileView.Type.TOGGLE, label, 1, 1)
                .setListener(new TileView.Listener() {
                    @Override public void onToggle(boolean c) { onToggleKey(key, c); }
                });
    }

    private void action(String id, String label, Callable<Boolean> task) {
        tile(id, TileView.Type.ACTION, label, 1, 1)
                .setListener(press(() -> runCommand(label, task)));
    }

    private void actionRun(String id, String label, Runnable r) {
        tile(id, TileView.Type.ACTION, label, 1, 1).setListener(press(r));
    }

    private TileView.Listener press(Runnable r) {
        return new TileView.Listener() {
            @Override public void onPress() { r.run(); }
        };
    }

    // ═══════════════════════════════════════════════
    //  状态条
    // ═══════════════════════════════════════════════

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0x990A0F1C);
        bar.setPadding(dp(10), 0, dp(10), 0);

        adbChip = chip("🔌 连接中…", true, v -> { showPage(0); Sh.autoConnectLocal(); });
        gearChip = chip("档位 --", false, v -> showPage(0));
        speedChip = chip("车速 --", false, v -> showPage(0));
        powerChip = chip("电量 --", false, v -> showPage(0));
        timeChip = chip("时间 --", false, v -> showPage(0));
        TextView voiceChip = chip("🎤 释放", true, v -> doReleaseVoice());
        TextView signalChip = chip("📊 信号", true, v -> showPage(1));
        TextView controlChip = chip("🎛 车控", true, v -> showPage(2));
        webChip = chip("🌐 Web: 启动中…", true, v -> showWebInfoDialog());
        TextView themeChip = chip("🎨 主题", true, v -> toggleTheme());   // [FIX-20260928] 主题切换入口

        bar.addView(adbChip);
        bar.addView(gearChip);
        bar.addView(speedChip);
        bar.addView(powerChip);
        bar.addView(timeChip);
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(voiceChip);
        bar.addView(signalChip);
        bar.addView(controlChip);
        bar.addView(webChip);
        bar.addView(themeChip);
        return bar;
    }

    private interface ChipClick { void onClick(View v); }

    private TextView chip(String text, boolean strong, final ChipClick click) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(strong ? DashboardTheme.CYAN : DashboardTheme.TEXT);
        tv.setTextSize(GridDimens.SP_CHIP);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), 0, dp(10), 0);
        if (click != null) tv.setOnClickListener(click::onClick);
        return tv;
    }

    private void showPage(int idx) {
        if (pageSwitcher.getDisplayedChild() != idx) pageSwitcher.setDisplayedChild(idx);
    }

    // ═══════════════════════════════════════════════
    //  Dock（底部快捷，固定不随滚动）
    // ═══════════════════════════════════════════════

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackgroundColor(0x990A0F1C);

        String[] items = {"❄ 空调", "💧 除雾", "↻ 循环", "🎥 360", "📂 后备箱", "🔒 锁车", "🔓 解锁", "📱 扫码"};
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(items[i]);
            b.setTextColor(DashboardTheme.TEXT);
            b.setTextSize(GridDimens.SP_CHIP);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(8), dp(8), dp(8), dp(8));
            b.setOnClickListener(v -> onDock(idx));
            dock.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        return dock;
    }

    private void onDock(int i) {
        switch (i) {
            case 0: hvacToggle("ac", true); break;
            case 1: hvacToggle("front", true); break;
            case 2: innerLoopNext(); break;
            case 3: runCommand("360全景", () -> vc.open360View()); break;
            case 4: runCommand("后备箱开", () -> vc.openTrunk()); break;
            case 5: runCommand("车门闭锁", () -> vc.lockCar()); break;
            case 6: runCommand("车门解锁", () -> vc.unlockCar()); break;
            case 7: showWebInfoDialog(); break;
            default: break;
        }
    }

    // ═══════════════════════════════════════════════
    //  开关控制
    // ═══════════════════════════════════════════════

    private void onToggleKey(String key, boolean c) {
        switch (key) {
            case "ac":     hvacToggle("ac", c); break;
            case "max":    hvacToggle("max", c); break;
            case "inner":  innerLoopNext(); break;
            case "front":  hvacToggle("front", c); break;
            case "rear":   hvacToggle("rear", c); break;
            case "mirror": runCommand(c ? "后视镜加热开" : "后视镜加热关",
                    () -> c ? vc.mirrorHeatOn() : vc.mirrorHeatOff()); break;
            case "winlock": runCommand(c ? "车窗锁开" : "车窗锁关",
                    () -> c ? vc.windowForbitOn() : vc.windowForbitOff()); break;
            case "sentinel": runCommand(c ? "哨兵开" : "哨兵关",
                    () -> vc.setGlobalKey("strCarSentinelMode", c ? "1" : "0")); break;
            case "speechspeak": runCommand(c ? "语音播报开" : "语音播报关",
                    () -> vc.setGlobalKey("SPEECH_SPEAK", c ? "1" : "0")); break;
            default: break;
        }
    }

    private void hvacToggle(String action, boolean checked) {
        switch (action) {
            case "ac":
                runCommand(checked ? "空调开" : "空调关",
                        () -> checked ? vc.acSwitchOn() : vc.acSwitchOff());
                break;
            case "max":
                runCommand(checked ? "最大制冷开" : "最大制冷关",
                        () -> checked ? vc.acMaxOn() : vc.acMaxOff());
                break;
            case "inner":
                runCommand(checked ? "内循环" : "外循环", () -> vc.setAirInnerLoop(checked));
                break;
            case "front":
                runCommand(checked ? "前除霜开" : "前除霜关",
                        () -> checked ? vc.frontDefrostOn() : vc.frontDefrostOff());
                break;
            case "rear":
                runCommand(checked ? "后除霜开" : "后除霜关",
                        () -> checked ? vc.rearDefrostOn() : vc.rearDefrostOff());
                break;
            default: break;
        }
    }

    private void openWindowSlider() {
        WindowCardView.Listener wl = (voiceName, percent) ->
                runCommand(voiceName + "→" + percent + "%",
                        () -> vc.setWindowByName(voiceName, percent));
        int[] pct = latest != null ? latest.windowPct : null;
        WindowSliderDialog.show(this, WindowCardView.voiceNames(), pct, wl);
    }

    // ═══════════════════════════════════════════════
    //  命令执行（后台 + Toast）
    // ═══════════════════════════════════════════════

    private void runCommand(final String label, final Callable<Boolean> task) {
        Sh.submitAsync(() -> {
            boolean ok = false;
            try {
                ok = Boolean.TRUE.equals(task.call());
            } catch (Exception e) {
                Logger.error("车控", label + " 执行异常", e);
            }
            final boolean fOk = ok;
            runOnUiThread(() -> Toast.makeText(DashboardActivity.this,
                    label + (fOk ? " ✅" : " ❌"), Toast.LENGTH_SHORT).show());
            if (repository != null) ui.postDelayed(() -> repository.requestRefresh(), 1500);
        });
    }

    // ═══════════════════════════════════════════════
    //  一键诊断 / 日志导出
    // ═══════════════════════════════════════════════

    private void runDiagnostic() {
        Toast.makeText(this, "正在采集一键诊断…", Toast.LENGTH_SHORT).show();
        DiagnosticRunner.run(this, webServer, this::showReportDialog);
    }

    private void showReportDialog(String report) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(0xFFE5E7EB);
        sv.addView(tv);
        int pad = dp(12);
        sv.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("🩺 一键诊断结果")
                .setView(sv)
                .setPositiveButton("导出报告", (d, w) ->
                        LogExport.saveText(this, "diagnostic", report, (path, ok) ->
                                Toast.makeText(this, ok ? "诊断已导出:\n" + path : "导出失败",
                                        Toast.LENGTH_LONG).show()))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void exportLogs() {
        LogExport.exportLogs(this, (path, ok) ->
                Toast.makeText(this, ok ? "日志已导出:\n" + path : "导出失败",
                        Toast.LENGTH_LONG).show());
    }

    private void doReleaseVoice() {
        boolean ok = vc.releaseVoice();
        Toast.makeText(this, ok ? "已发送语音释放" : "语音释放失败（见日志）",
                Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════
    //  Web 服务
    // ═══════════════════════════════════════════════

    private void startWebAuto() {
        int preferred = getSharedPreferences("c11cartool", MODE_PRIVATE).getInt("web_port", 8080);
        Sh.submitAsync(() -> {
            boolean ok = webServer.start(preferred);
            getSharedPreferences("c11cartool", MODE_PRIVATE)
                    .edit().putInt("web_port", webServer.getPort()).apply();
            Logger.ok("Web 服务" + (ok ? "已启动，端口 " + webServer.getPort() : "启动失败"));
            runOnUiThread(this::updateStatusBar);
        });
    }

    private void showWebInfoDialog() {
        WebPages.WebInfo info = new WebPages.WebInfo(
                webServer.isRunning(), webServer.getPort(),
                webServer.getAuthToken(), WebServer.getDeviceIp());
        new AlertDialog.Builder(this)
                .setTitle("📱 手机扫码测控")
                .setView(WebPages.buildInfoView(this, info))
                .setPositiveButton("知道了", null)
                .show();
    }

    // ═══════════════════════════════════════════════
    //  数据回调（Repository → UI）
    // ═══════════════════════════════════════════════

    @Override public void onSnapshot(DashboardSnapshot s) {
        latest = s;
        applySnapshot(s);
    }

    private void applySnapshot(DashboardSnapshot s) {
        if (s == null) {
            updateStatusBar();
            return;
        }

        float powerKw = (s.voltage >= 0 && s.current >= 0)
                ? s.voltage * s.current / 1000f : Float.NaN;

        // 仪表 / 波形
        if (s.speedKmh >= 0) speedGauge.setValue(s.speedKmh);
        if (s.batterySoc >= 0) socGauge.setValue(s.batterySoc);
        if (!Float.isNaN(powerKw)) powerGauge.setValue(powerKw);
        if (s.voltage >= 0) voltageChart.addPoint(s.voltage);
        if (s.current >= 0) currentChart.addPoint(s.current);
        if (!Float.isNaN(powerKw)) powerChart.addPoint(powerKw);
        if (s.speedKmh >= 0) speedChart.addPoint(s.speedKmh);

        // 行车数值（全部走最后已知值缓存）
        setIntCached("soc", s.batterySoc, "%");
        setIntCached("range", s.rangeDyn >= 0 ? s.rangeDyn : s.rangeStd, "km");
        setFloatCached("volt", s.voltage, "V");
        setFloatCached("current", s.current, "A");
        if (!Float.isNaN(powerKw)) { cacheFloat.put("power", powerKw); t("power").setValue(fmt1(powerKw), "kW"); }
        else { Float pc = cacheFloat.get("power"); if (pc != null) t("power").setValue(fmt1(pc), "缓"); else t("power").setFailed(); }
        setIntCached("speed", s.speedKmh, "km/h");
        if (s.gear != null && !s.gear.isEmpty()) { cacheGear = s.gear; t("gear").setValue(s.gear, ""); }
        else if (cacheGear != null) t("gear").setValue(cacheGear, "缓");
        else t("gear").setValue("--", "");
        setIntCached("outtemp", s.outsideTemp, "℃");
        setIntCached("pm25", s.pm25, "");

        // 音量滑块（缓存）
        setSliderCached("vol_music", 0, 100, s.musicVol, "");
        setSliderCached("vol_navi", 0, 100, s.naviVol, "");
        setSliderCached("vol_speech", 0, 100, s.speechVol, "");
        setSliderCached("vol_call", 0, 100, s.callVol, "");

        // 胎压
        setTire("tire_fl", s, 0);
        setTire("tire_fr", s, 1);
        setTire("tire_rl", s, 2);
        setTire("tire_rr", s, 3);

        // 车门 / 舱盖（[FIX-20260928] 记住最近有效值，不再回退“未读取到”）
        setDoorCached("door_fl", s.doorStates, 0);
        setDoorCached("door_fr", s.doorStates, 1);
        setDoorCached("door_rl", s.doorStates, 2);
        setDoorCached("door_rr", s.doorStates, 3);
        setDoorCached("trunk_state", s.doorStates, 4);
        setDoorCached("hood", s.doorStates, 5);

        // 空调滑块 + 开关
        setSliderCached("temp_driver", 16, 32,
                s.driverTempHalf >= 0 ? Math.round(s.driverTempHalf / 2f) : -1, "℃");
        setSliderCached("temp_pass", 16, 32,
                s.passengerTempHalf >= 0 ? Math.round(s.passengerTempHalf / 2f) : -1, "℃");
        setSliderCached("fan", 1, 7, s.fanSpeed, "档");
        setToggle("ac", s.acSwitch);
        // [FIX-20260928] 循环三态：有值时同步轮转起点与标签
        if (s.innerCycle >= 0) {
            innerLoopMode = Math.min(2, s.innerCycle);
            t("innerloop").setLabel(loopWord(innerLoopMode));
        }
        setToggle("frontdef", s.frontDefrost);
        setToggle("reardef", s.rearDefrost);
        setToggle("mirrorheat", s.mirrorHeat);
        setToggle("winlock", s.windowForbit);
        setToggle("sentinel", extraInt(s, "strCarSentinelMode"));
        setToggle("speechspeak", extraInt(s, "SPEECH_SPEAK"));
        // [FIX-20260928] 合并开关的小点以实证状态为准（有值才覆盖）
        overlayPair("lock_pair", "车门锁", s.vehicleLock);
        overlayPair("trunk_pair", "后备箱", s.doorStates != null ? s.doorStates[4] : -1);
        overlayPair("child_l", "左童锁", s.childLock);
        overlayPair("child_r", "右童锁", s.childLock);

        // 额外 settings 原始状态
        setRawTile("bt", s.extra.get("strCarBluetoothStatus"));
        setRawTile("wifi", s.extra.get("strCarWifiStatus"));
        setRawTile("ble", s.extra.get("strCarBleState"));
        setRawTile("ptc", s.extra.get("strCarPTCOutTemp"));
        setRawTile("ambient", s.extra.get("strCar1800"));
        setRawTile("airstatus", s.extra.get("strCarAirStatus"));

        // 车窗标签
        for (int i = 0; i < 4; i++) {
            if (s.windowPct != null && s.windowPct[i] >= 0) {
                t("win_" + i).setLabel(WIN_TITLES[i] + " " + s.windowPct[i] + "%");
            }
        }

        if (signalPage != null) signalPage.setRows(s.rows);
        updateStatusBar();
    }

    // ── 数据更新 helper ──

    // [v0.3.11] 数据 helper 走「最后已知值」缓存：新值有效更新缓存，无效沿用缓存（标"缓"），从未读到才失败
    private void setIntCached(String id, int value, String unit) {
        if (value >= 0) {
            cacheInt.put(id, value);
            t(id).setValue(String.valueOf(value), unit);
        } else {
            Integer c = cacheInt.get(id);
            if (c != null) t(id).setValue(String.valueOf(c), "缓");
            else t(id).setFailed();
        }
    }

    private void setSliderCached(String id, int min, int max, int value, String unit) {
        if (value >= 0) {
            cacheSlider.put(id, new int[]{min, max, value});
            t(id).setSlider(min, max, value, unit);
        } else {
            int[] c = cacheSlider.get(id);
            if (c != null) t(id).setSlider(c[0], c[1], c[2], unit);
            else t(id).setSliderUnknown();
        }
    }

    private void setRawTile(String id, String raw) {
        TileView t = t(id);
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw.trim())) t.setFailed();
        else t.setValue(raw.trim(), "");
    }

    private static int extraInt(DashboardSnapshot s, String key) {
        String v = s.extra.get(key);
        if (v == null) return -1;
        try { return (int) Math.floor(Double.parseDouble(v.trim())); }
        catch (Exception e) { return -1; }
    }

    private void setFloatCached(String id, float value, String unit) {
        if (value >= 0) {
            cacheFloat.put(id, value);
            t(id).setValue(fmt1(value), unit);
        } else {
            Float c = cacheFloat.get(id);
            if (c != null) t(id).setValue(fmt1(c), "缓");
            else t(id).setFailed();
        }
    }

    private void setToggle(String id, int value) {
        if (value >= 0) t(id).setChecked(value == 1);
    }

    private void setTire(String id, DashboardSnapshot s, int i) {
        if (s.tirePressKpa != null && i < s.tirePressKpa.length && s.tirePressKpa[i] > 0) {
            t(id).setValue(fmt2(s.tirePressKpa[i] / 100f), "bar");
            String temp = (s.tireTempC != null && i < s.tireTempC.length)
                    ? Math.round(s.tireTempC[i]) + "℃" : "";
            t(id).setSub(temp);
        } else {
            t(id).setFailed();
        }
    }

    private void setDoorCached(String id, int[] states, int i) {
        // [FIX-20260928] 门/舱盖状态记忆：logcat 无新事件时保留最近有效值（标“缓”）
        int v = (states != null && i < states.length) ? states[i] : -1;
        if (v >= 0) lastDoor[i] = v;
        int show = v >= 0 ? v : lastDoor[i];
        if (show < 0) t(id).setFailed();
        else t(id).setValue(doorWord(show), v >= 0 ? "" : "缓");
    }

    private static String doorWord(int st) {
        return st == 0 ? "关" : st == 1 ? "开" : String.valueOf(st);
    }

    private static String fmt1(float v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String fmt2(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private void updateStatusBar() {
        DashboardSnapshot s = latest;
        boolean adb = Sh.isAdbConnected();
        adbChip.setText(adb ? "🔌 ADB 已连" : "🔌 未连接");
        adbChip.setTextColor(adb ? DashboardTheme.GREEN : DashboardTheme.ORANGE);
        gearChip.setText("档位 " + (s != null && !s.gear.isEmpty() ? s.gear : "--"));
        speedChip.setText("车速 " + (s != null && s.speedKmh >= 0 ? s.speedKmh : "--"));
        powerChip.setText("电量 " + (s != null && s.batterySoc >= 0 ? s.batterySoc + "%" : "--"));
        timeChip.setText("时间 " + new java.text.SimpleDateFormat(
                "HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
        webChip.setText(webServer != null && webServer.isRunning()
                ? "🌐 Web:" + webServer.getPort() : "🌐 Web:关");
    }

    // ═══════════════════════════════════════════════
    //  沉浸模式 / 工具
    // ═══════════════════════════════════════════════

    // ═══════════════════════════════════════
    //  主题（毛玻璃 / 仿 iOS 玻璃拟态）
    // ═══════════════════════════════════════

    /**
     * [FIX-20260928] 主题背景应用（即时生效，无需重启）：
     * 主题 A = 纯色深色渐变 + 彩色光晕（Glass.wallpaper，本身即玻璃拟态底）；
     * 主题 B = 必应每日壁纸（后台一次性高斯模糊 + 本地缓存，Android 9 用 RenderScript，
     * 不用 AGSL/RenderEffect、不做实时模糊）；加载失败自动降级主题 A 底。
     * 两套主题的模块均为半透明毛玻璃（Glass 卡片），不随主题变化。
     */
    private void applyThemeBackground() {
        if (rootView == null) return;
        rootView.setBackground(Glass.wallpaper(this));   // 先给 A 兜底
        if (themeManager == null || !themeManager.isBing()) return;
        BingWallpaper.loadAsync(this, bmp -> {
            if (bmp != null && !isFinishing() && !isDestroyed() && themeManager.isBing()) {
                rootView.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bmp));
            }
        });
    }

    /** 主题切换（状态条 🎨）：A ⇄ B，持久化 + 即时生效 */
    private void toggleTheme() {
        themeManager.set(themeManager.isBing() ? ThemeManager.THEME_SOLID : ThemeManager.THEME_BING);
        applyThemeBackground();
        Toast.makeText(this,
                themeManager.isBing() ? "主题B：必应壁纸 · 毛玻璃" : "主题A：纯色光晕 · 毛玻璃",
                Toast.LENGTH_SHORT).show();
    }

    private void enterImmersive() {
        // [FIX-20260928] 全屏：补 FLAG_FULLSCREEN 窗口标志（仅系统 UI 标志时部分车机仍留状态栏）
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersive();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (stateListener != null) Sh.removeStateListener(stateListener);
        if (repository != null) repository.stop();
        if (webServer != null) webServer.stop();
    }
}
