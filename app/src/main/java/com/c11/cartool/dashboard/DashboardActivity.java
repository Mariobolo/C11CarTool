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

    private TextView adbChip, gearChip, speedChip, powerChip, timeChip, webChip;

    private static final String[] WIN_TITLES = {"主驾车窗", "副驾车窗", "左后车窗", "右后车窗"};

    private volatile DashboardSnapshot latest;
    private Sh.StateListener stateListener;
    private final Handler ui = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        vc = new VehicleController(this);
        webServer = new WebServer();
        webServer.setVehicleController(vc);

        Logger.clearCmdLog();
        Logger.title(AppInfo.TITLE + " 启动");
        Logger.info("设备: " + android.os.Build.MODEL + "，Android " + android.os.Build.VERSION.RELEASE);

        buildUi();
        startWebAuto();

        Sh.setContext(this);
        stateListener = new Sh.StateListener() {
            @Override public void onAdbStateChanged(boolean connected, int uid) {
                ui.post(DashboardActivity.this::updateStatusBar);
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
        root.setBackground(Glass.wallpaper(this));

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

        // 音量（步进 2×1）
        group("🔊 音量");
        tile("vol_music", TileView.Type.STEP, "媒体音量", 2, 1).setListener(volumeStep("C11_MUSIC"));
        tile("vol_navi", TileView.Type.STEP, "导航音量", 2, 1).setListener(volumeStep("C11_NAVI"));
        tile("vol_speech", TileView.Type.STEP, "语音音量", 2, 1).setListener(volumeStep("C11_SPEECH"));
        tile("vol_call", TileView.Type.STEP, "通话音量", 2, 1).setListener(volumeStep("C11_CALL"));

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
        tile("temp_driver", TileView.Type.STEP, "主驾温度", 2, 1).setListener(step(0));
        tile("temp_pass", TileView.Type.STEP, "副驾温度", 2, 1).setListener(step(1));
        tile("fan", TileView.Type.STEP, "风量", 2, 1).setListener(new TileView.Listener() {
            @Override public void onStep(int d) { adjustFan(d); }
        });
        toggle("ac", "空调", "ac");
        toggle("acmax", "最大制冷", "max");
        toggle("innerloop", "内外循环", "inner");
        toggle("frontdef", "前除霜", "front");
        toggle("reardef", "后除霜", "rear");
        toggle("mirrorheat", "后视镜加热", "mirror");
        toggle("winlock", "车窗锁", "winlock");
        action("airraw0", "模式0", () -> vc.setAirStatusRaw(0));
        action("airraw1", "模式1", () -> vc.setAirStatusRaw(1));
        action("airraw2", "模式2", () -> vc.setAirStatusRaw(2));
        action("airraw3", "模式3", () -> vc.setAirStatusRaw(3));
        actionRun("acpage", "空调界面", vc::openAcPage);

        // 车窗（点击开滑杆）
        group("🪟 车窗");
        for (int i = 0; i < 4; i++) {
            tile("win_" + i, TileView.Type.ACTION, WIN_TITLES[i], 1, 1)
                    .setListener(press(this::openWindowSlider));
        }

        // 儿童锁
        group("👶 儿童锁");
        action("child_l_on", "左锁开", () -> vc.leftChildLockOn());
        action("child_l_off", "左锁关", () -> vc.leftChildLockOff());
        action("child_r_on", "右锁开", () -> vc.rightChildLockOn());
        action("child_r_off", "右锁关", () -> vc.rightChildLockOff());

        // 灯光
        group("💡 灯光");
        action("low_on", "近光开", () -> vc.lowBeamOn());
        action("low_off", "近光关", () -> vc.lowBeamOff());
        action("high_on", "远光开", () -> vc.highBeamOn());
        action("high_off", "远光关", () -> vc.highBeamOff());
        action("pos_on", "示廓开", () -> vc.positionLightOn());
        action("pos_off", "示廓关", () -> vc.positionLightOff());
        action("ffog_on", "前雾开", () -> vc.frontFogOn());
        action("ffog_off", "前雾关", () -> vc.frontFogOff());
        action("fog_on", "后雾开", () -> vc.fogLightOn());
        action("fog_off", "后雾关", () -> vc.fogLightOff());
        actionRun("auto", "自动灯光", vc::autoLights);
        actionRun("closeall", "关闭全部", vc::closeAllLights);

        // 车身 / 工具
        group("🧰 车身 / 工具");
        action("trunk_open", "后备箱开", () -> vc.openTrunk());
        action("trunk_close", "后备箱关", () -> vc.closeTrunk());
        action("lock", "🔒 锁车", () -> vc.lockCar());
        action("unlock", "🔓 解锁", () -> vc.unlockCar());
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

    private TileView.Listener volumeStep(String key) {
        return new TileView.Listener() {
            @Override public void onStep(int d) { adjustVolume(key, d); }
        };
    }

    private TileView.Listener step(final int which) {
        return new TileView.Listener() {
            @Override public void onStep(int d) { adjustTemp(which == 0, d); }
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
        TextView signalChip = chip("📊 信号", true, v -> showPage(1));
        TextView controlChip = chip("🎛 车控", true, v -> showPage(2));
        webChip = chip("🌐 Web: 启动中…", true, v -> showWebInfoDialog());

        bar.addView(adbChip);
        bar.addView(gearChip);
        bar.addView(speedChip);
        bar.addView(powerChip);
        bar.addView(timeChip);
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(signalChip);
        bar.addView(controlChip);
        bar.addView(webChip);
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
            case 2: hvacToggle("inner", true); break;
            case 3: runCommand("360全景", () -> vc.open360View()); break;
            case 4: runCommand("后备箱开", () -> vc.openTrunk()); break;
            case 5: runCommand("车门闭锁", () -> vc.lockCar()); break;
            case 6: runCommand("车门解锁", () -> vc.unlockCar()); break;
            case 7: showWebInfoDialog(); break;
            default: break;
        }
    }

    // ═══════════════════════════════════════════════
    //  空调 / 音量 / 车窗 调节
    // ═══════════════════════════════════════════════

    private void adjustTemp(boolean driver, int delta) {
        DashboardSnapshot s = latest;
        int base = halfToC(s == null ? -1 : (driver ? s.driverTempHalf : s.passengerTempHalf));
        if (base < 0) base = 24;
        int target = clampTemp(base + delta);
        if (driver) runCommand("主驾温度 " + target + "℃", () -> vc.setAcTemperatureDriver(target));
        else runCommand("副驾温度 " + target + "℃", () -> vc.setAcTemperaturePassenger(target));
    }

    private void adjustFan(int delta) {
        DashboardSnapshot s = latest;
        int base = s == null ? -1 : s.fanSpeed;
        if (base < 0) base = 3;
        int target = Math.max(1, Math.min(7, base + delta));
        runCommand("风量 " + target, () -> vc.setAcFanSpeed(target));
    }

    private void adjustVolume(String key, int delta) {
        int base = volumeBase(key);
        if (base < 0) base = 8;
        final int target = Math.max(0, Math.min(15, base + delta));
        runCommand(key + " " + target, () -> vc.setGlobalKey(key, String.valueOf(target)));
    }

    private int volumeBase(String key) {
        DashboardSnapshot s = latest;
        if (s == null) return -1;
        switch (key) {
            case "C11_MUSIC":  return s.musicVol;
            case "C11_NAVI":   return s.naviVol;
            case "C11_SPEECH": return s.speechVol;
            case "C11_CALL":   return s.callVol;
            default: return -1;
        }
    }

    private void onToggleKey(String key, boolean c) {
        switch (key) {
            case "ac":     hvacToggle("ac", c); break;
            case "max":    hvacToggle("max", c); break;
            case "inner":  hvacToggle("inner", c); break;
            case "front":  hvacToggle("front", c); break;
            case "rear":   hvacToggle("rear", c); break;
            case "mirror": runCommand(c ? "后视镜加热开" : "后视镜加热关",
                    () -> c ? vc.mirrorHeatOn() : vc.mirrorHeatOff()); break;
            case "winlock": runCommand(c ? "车窗锁开" : "车窗锁关",
                    () -> c ? vc.windowForbitOn() : vc.windowForbitOff()); break;
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

        // 行车
        setInt("soc", s.batterySoc, "%");
        setInt("range", s.rangeDyn >= 0 ? s.rangeDyn : s.rangeStd, "km");
        setFloat("volt", s.voltage, "V");
        setFloat("current", s.current, "A");
        t("power").setValue(computePower(s), "kW");
        setInt("speed", s.speedKmh, "km/h");
        t("gear").setValue(s.gear == null || s.gear.isEmpty() ? null : s.gear, "");
        setInt("outtemp", s.outsideTemp, "℃");
        setInt("pm25", s.pm25, "");

        // 音量
        setStep("vol_music", s.musicVol);
        setStep("vol_navi", s.naviVol);
        setStep("vol_speech", s.speechVol);
        setStep("vol_call", s.callVol);

        // 胎压
        setTire("tire_fl", s, 0);
        setTire("tire_fr", s, 1);
        setTire("tire_rl", s, 2);
        setTire("tire_rr", s, 3);

        // 车门 / 舱盖
        setDoor("door_fl", s.doorStates, 0);
        setDoor("door_fr", s.doorStates, 1);
        setDoor("door_rl", s.doorStates, 2);
        setDoor("door_rr", s.doorStates, 3);
        setDoor("trunk_state", s.doorStates, 4);
        setDoor("hood", s.doorStates, 5);

        // 空调
        setStepC("temp_driver", s.driverTempHalf);
        setStepC("temp_pass", s.passengerTempHalf);
        setStep("fan", s.fanSpeed);
        setToggle("ac", s.acSwitch);
        setToggle("innerloop", s.innerCycle);
        setToggle("frontdef", s.frontDefrost);
        setToggle("reardef", s.rearDefrost);
        setToggle("mirrorheat", s.mirrorHeat);
        setToggle("winlock", s.windowForbit);

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

    private void setInt(String id, int value, String unit) {
        if (value >= 0) t(id).setValue(String.valueOf(value), unit);
        else t(id).setFailed();
    }

    private void setFloat(String id, float value, String unit) {
        if (value >= 0) t(id).setValue(fmt1(value), unit);
        else t(id).setFailed();
    }

    private void setStep(String id, int value) {
        t(id).setStepValue(value >= 0 ? String.valueOf(value) : null);
    }

    private void setStepC(String id, int half) {
        t(id).setStepValue(half >= 0 ? String.valueOf(halfToC(half)) : null);
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

    private void setDoor(String id, int[] states, int i) {
        if (states == null || i >= states.length || states[i] < 0) t(id).setFailed();
        else t(id).setValue(doorWord(states[i]), "");
    }

    private static String doorWord(int st) {
        return st == 0 ? "关" : st == 1 ? "开" : String.valueOf(st);
    }

    /** 功率 kW = 电压 V × 电流 A / 1000；任一缺失返 null（显失败，不臆测） */
    private static String computePower(DashboardSnapshot s) {
        if (s.voltage >= 0 && s.current >= 0) return fmt1(s.voltage * s.current / 1000f);
        return null;
    }

    private static int clampTemp(int t) {
        return Math.max(16, Math.min(32, t));
    }

    private static int halfToC(int half) {
        return half < 0 ? -1 : Math.round(half / 2f);
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

    private void enterImmersive() {
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
