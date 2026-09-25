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

import java.util.concurrent.Callable;

/**
 * 仪表盘主界面（车机横屏全屏，基于 {@link Activity}，不依赖 AndroidX）。
 *
 * <p>三页结构（{@link ViewFlipper}）：
 * <ul>
 *   <li>页0 仪表盘：固定网格，数据方块 + 车控卡片；</li>
 *   <li>页1 全车信号清单（状态条「📊 信号」进入，支持搜索 / 折叠）；</li>
 *   <li>页2 全部车控（状态条「🎛 车控」进入，全量车控平铺、多通道候选）。</li>
 * </ul>
 * 数据采集由 {@link DashboardRepository} 后台线程 5s 一轮驱动；不设工程模式，
 * 一键诊断、日志导出、手机扫码测控均在前端直达。
 */
public class DashboardActivity extends Activity implements DashboardRepository.Callback {

    private VehicleController vc;
    private WebServer webServer;
    private DashboardRepository repository;

    private ViewFlipper pageSwitcher;
    private SignalListPage signalPage;
    private ControlListPage controlPage;

    // 数据卡
    private DataCardView powerCard, outTempCard, mediaVolCard, naviVolCard, speechVolCard, callVolCard;
    private HvacCardView hvacCard;
    private TiresCardView tiresCard;
    private DoorsCardView doorsCard;
    private DataCardView spanCard;

    // 车控卡
    private WindowCardView windowCard;
    private MiniGridCardView trunkCard;

    // 状态条
    private TextView adbChip, gearChip, speedChip, powerChip, timeChip, webChip;

    /** 最近一次快照（状态条与步进基准读取） */
    private volatile DashboardSnapshot latest;

    private Sh.StateListener stateListener;

    private final Handler ui = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);

        // 横屏 + 全屏沉浸 + 常亮
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
        Sh.startKeepAlive();   // 心跳保活：断线自动重连 + uid 周期刷新（幂等；此前仅旧 MainActivity 启动，导致 Dashboard 断线不重连）
        // ADB：打开即自动连接（多地址候选、最多 6 次）
        Sh.autoConnectLocal();

        // 数据采集：后台线程 5s 一轮
        repository = new DashboardRepository(ui, this);
        repository.start();
    }

    // ═══════════════════════════════════════════════
    //  UI 构建
    // ═══════════════════════════════════════════════

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DashboardTheme.BG);

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

    private View buildGridPage() {
        boolean compact = isCompactScreen();
        DashboardGridView grid = new DashboardGridView(this, 12, compact ? 4 : 7, 16, 10);

        powerCard = new DataCardView(this, "电量 / 续航 / 电压", 12);
        hvacCard = new HvacCardView(this, compact, 12);
        outTempCard = new DataCardView(this, "车外温度", 10);
        mediaVolCard = new DataCardView(this, "媒体音量", 10);
        naviVolCard = new DataCardView(this, "导航音量", 10);
        speechVolCard = new DataCardView(this, "语音", 10);
        callVolCard = new DataCardView(this, "通话音量", 10);
        tiresCard = new TiresCardView(this, 12);
        doorsCard = new DoorsCardView(this, 12);
        spanCard = new DataCardView(this, "功率 / 电流", 12);

        if (compact) buildCompactGrid(grid);
        else build1080Grid(grid);

        bindCardActions();
        return grid;
    }

    /** 中控 1920×1080：12 列 × 7 行 */
    private void build1080Grid(DashboardGridView grid) {
        // 数据区 row0-3
        grid.addCard(powerCard, 0, 0, 3, 2);
        grid.addCard(hvacCard, 3, 0, 4, 4);
        grid.addCard(outTempCard, 7, 0, 2, 1);
        grid.addCard(mediaVolCard, 9, 0, 2, 1);
        grid.addCard(naviVolCard, 7, 1, 2, 1);
        grid.addCard(speechVolCard, 11, 0, 1, 2);
        grid.addCard(callVolCard, 9, 1, 2, 1);
        grid.addCard(tiresCard, 0, 2, 6, 2);
        grid.addCard(doorsCard, 6, 2, 3, 2);
        grid.addCard(spanCard, 9, 2, 3, 2);

        // 车控区 row4-6
        windowCard = new WindowCardView(this, false);
        grid.addCard(windowCard, 0, 4, 4, 1);

        MiniGridCardView lightCard = new MiniGridCardView(this, "灯光（旧语音广播）",
                java.util.Arrays.asList(
                        new MiniGridCardView.Item("low_on", "近光开", "💡", false),
                        new MiniGridCardView.Item("low_off", "近光关", "💡", false),
                        new MiniGridCardView.Item("high_on", "远光开", "🔆", false),
                        new MiniGridCardView.Item("high_off", "远光关", "🔅", false),
                        new MiniGridCardView.Item("pos_on", "示廓开", "🔦", false),
                        new MiniGridCardView.Item("pos_off", "示廓关", "🔦", false),
                        new MiniGridCardView.Item("fog_on", "后雾开", "🌫", false),
                        new MiniGridCardView.Item("fog_off", "后雾关", "🌫", false)), 10);
        lightCard.setListener(this::onMiniToggle);
        grid.addCard(lightCard, 4, 4, 4, 2);

        MiniGridCardView childCard = new MiniGridCardView(this, "儿童锁（handMessage）",
                java.util.Arrays.asList(
                        new MiniGridCardView.Item("child_l_on", "左锁开", "🔒", false),
                        new MiniGridCardView.Item("child_l_off", "左锁关", "🔓", false),
                        new MiniGridCardView.Item("child_r_on", "右锁开", "🔒", false),
                        new MiniGridCardView.Item("child_r_off", "右锁关", "🔓", false)), 10);
        childCard.setListener(this::onMiniToggle);
        grid.addCard(childCard, 8, 4, 2, 2);

        trunkCard = new MiniGridCardView(this, "后备箱",
                java.util.Arrays.asList(
                        new MiniGridCardView.Item("trunk_open", "开", "📂", false),
                        new MiniGridCardView.Item("trunk_close", "关", "📁", false)), 10);
        trunkCard.setListener(this::onMiniToggle);
        grid.addCard(trunkCard, 10, 4, 2, 1);

        MiniGridCardView otherCard = new MiniGridCardView(this, "其它（settings）",
                java.util.Arrays.asList(
                        new MiniGridCardView.Item("mirror_on", "镜加热开", "♨", false),
                        new MiniGridCardView.Item("mirror_off", "镜加热关", "♨", false),
                        new MiniGridCardView.Item("winlock_on", "车窗锁开", "🚫", false),
                        new MiniGridCardView.Item("winlock_off", "车窗锁关", "✅", false),
                        new MiniGridCardView.Item("ac_page", "空调界面", "🌀", false)), 10);
        otherCard.setListener(this::onMiniToggle);
        grid.addCard(otherCard, 0, 5, 4, 2);

        ToolEntryCardView scanEntry = new ToolEntryCardView(this, "手机扫码测控", "📱", 10);
        scanEntry.setListener(card -> showWebInfoDialog());
        grid.addCard(scanEntry, 10, 5, 2, 1);

        LockCardView lockCard = new LockCardView(this, 12);
        grid.addCard(lockCard, 4, 6, 4, 1);

        ToolEntryCardView diagEntry = new ToolEntryCardView(this, "一键诊断", "🩺", 10);
        diagEntry.setListener(card -> runDiagnostic());
        grid.addCard(diagEntry, 8, 6, 2, 1);

        ToolEntryCardView logEntry = new ToolEntryCardView(this, "导出日志", "📋", 10);
        logEntry.setListener(card -> exportLogs());
        grid.addCard(logEntry, 10, 6, 2, 1);
    }

    /** 仪表 / 副驾 1920×720：12 列 × 4 行（完整车控请进「🎛 车控」页） */
    private void buildCompactGrid(DashboardGridView grid) {
        grid.addCard(powerCard, 0, 0, 3, 1);
        grid.addCard(hvacCard, 3, 0, 4, 2);
        grid.addCard(outTempCard, 7, 0, 2, 1);
        grid.addCard(mediaVolCard, 9, 0, 2, 1);
        grid.addCard(speechVolCard, 11, 0, 1, 2);
        grid.addCard(naviVolCard, 7, 1, 2, 1);
        grid.addCard(callVolCard, 9, 1, 2, 1);

        grid.addCard(tiresCard, 0, 2, 6, 1);
        grid.addCard(doorsCard, 6, 2, 6, 1);

        LockCardView lockCard = new LockCardView(this, 10);
        grid.addCard(lockCard, 0, 3, 4, 1);

        ToolEntryCardView scanEntry = new ToolEntryCardView(this, "扫码测控", "📱", 10);
        scanEntry.setListener(card -> showWebInfoDialog());
        grid.addCard(scanEntry, 4, 3, 2, 1);

        ToolEntryCardView diagEntry = new ToolEntryCardView(this, "诊断", "🩺", 10);
        diagEntry.setListener(card -> runDiagnostic());
        grid.addCard(diagEntry, 6, 3, 2, 1);

        ToolEntryCardView logEntry = new ToolEntryCardView(this, "日志", "📋", 10);
        logEntry.setListener(card -> exportLogs());
        grid.addCard(logEntry, 8, 3, 2, 1);

        trunkCard = new MiniGridCardView(this, "后备箱",
                java.util.Arrays.asList(
                        new MiniGridCardView.Item("trunk_open", "开", "📂", false),
                        new MiniGridCardView.Item("trunk_close", "关", "📁", false)), 10);
        trunkCard.setListener(this::onMiniToggle);
        grid.addCard(trunkCard, 10, 3, 2, 1);
    }

    // ═══════════════════════════════════════════════
    //  状态条
    // ═══════════════════════════════════════════════

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(DashboardTheme.SURFACE);
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
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(strong ? DashboardTheme.CYAN : DashboardTheme.TEXT);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), 0, dp(10), 0);
        if (click != null) t.setOnClickListener(click::onClick);
        return t;
    }

    private void showPage(int idx) {
        if (pageSwitcher.getDisplayedChild() != idx) pageSwitcher.setDisplayedChild(idx);
    }

    // ═══════════════════════════════════════════════
    //  Dock（底部快捷）
    // ═══════════════════════════════════════════════

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackgroundColor(DashboardTheme.SURFACE);

        String[] items = {"❄ 空调", "💧 除雾", "↻ 循环", "🎥 360", "📂 后备箱", "🔒 锁车", "🔓 解锁", "📱 扫码"};
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(items[i]);
            b.setTextColor(DashboardTheme.TEXT);
            b.setTextSize(12);
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
            case 3: runCommand("360全景", vc::open360View); break;
            case 4: runCommand("后备箱开", vc::openTrunk); break;
            case 5: runCommand("车门闭锁", vc::lockCar); break;
            case 6: runCommand("车门解锁", vc::unlockCar); break;
            case 7: showWebInfoDialog(); break;
            default: break;
        }
    }

    // ═══════════════════════════════════════════════
    //  卡片动作绑定
    // ═══════════════════════════════════════════════

    private void bindCardActions() {
        hvacCard.setListener(new HvacCardView.Listener() {
            @Override public void onTempStep(HvacCardView card, boolean driver, int delta) {
                adjustTemp(driver, delta);
            }
            @Override public void onFanStep(HvacCardView card, int delta) { adjustFan(delta); }
            @Override public void onToggle(HvacCardView card, String action, boolean checked) {
                hvacToggle(action, checked);
            }
        });

        if (windowCard != null) {
            WindowCardView.Listener wl = (voiceName, percent) ->
                    runCommand(voiceName + "→" + percent + "%",
                            () -> vc.setWindowByName(voiceName, percent));
            windowCard.setListener(wl);
            windowCard.setCustomListener(() -> WindowSliderDialog.show(
                    DashboardActivity.this, WindowCardView.voiceNames(),
                    latest != null ? latest.windowPct : null, wl));
        }
    }

    /** MiniGrid 回调：按按钮 id 明确动作（开/关成对，不依赖翻转状态） */
    private void onMiniToggle(MiniGridCardView card, String id, boolean checked) {
        switch (id) {
            case "low_on": runCommand("近光开", vc::lowBeamOn); break;
            case "low_off": runCommand("近光关", vc::lowBeamOff); break;
            case "high_on": runCommand("远光开", vc::highBeamOn); break;
            case "high_off": runCommand("远光关", vc::highBeamOff); break;
            case "pos_on": runCommand("示廓开", vc::positionLightOn); break;
            case "pos_off": runCommand("示廓关", vc::positionLightOff); break;
            case "fog_on": runCommand("后雾开", vc::fogLightOn); break;
            case "fog_off": runCommand("后雾关", vc::fogLightOff); break;
            case "view360": runCommand("360全景", vc::open360View); break;
            case "child_l_on": runCommand("左童锁开", vc::leftChildLockOn); break;
            case "child_l_off": runCommand("左童锁关", vc::leftChildLockOff); break;
            case "child_r_on": runCommand("右童锁开", vc::rightChildLockOn); break;
            case "child_r_off": runCommand("右童锁关", vc::rightChildLockOff); break;
            case "trunk_open": runCommand("后备箱开", vc::openTrunk); break;
            case "trunk_close": runCommand("后备箱关", vc::closeTrunk); break;
            case "mirror_on": runCommand("后视镜加热开", vc::mirrorHeatOn); break;
            case "mirror_off": runCommand("后视镜加热关", vc::mirrorHeatOff); break;
            case "winlock_on": runCommand("车窗锁开", vc::windowForbitOn); break;
            case "winlock_off": runCommand("车窗锁关", vc::windowForbitOff); break;
            case "ac_page": runCommand("空调界面", vc::openAcPage); break;
            default: Logger.warn("未知车控按钮 id: " + id);
        }
    }

    // ═══════════════════════════════════════════════
    //  HVAC 调节（步进基准取最近快照，无值才用兜底）
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

    private static int clampTemp(int t) { return Math.max(16, Math.min(32, t)); }

    private static int halfToC(int half) {
        return half < 0 ? -1 : Math.round(half / 2f);
    }

    private void hvacToggle(String action, boolean checked) {
        switch (action) {
            case "ac":
                if (checked) runCommand("空调开", vc::acSwitchOn);
                else runCommand("空调关", vc::acSwitchOff);
                break;
            case "max":
                if (checked) runCommand("最大制冷开", vc::acMaxOn);
                else runCommand("最大制冷关", vc::acMaxOff);
                break;
            case "inner":
                runCommand(checked ? "内循环" : "外循环", () -> vc.setAirInnerLoop(checked));
                break;
            case "front":
                if (checked) runCommand("前除霜开", vc::frontDefrostOn);
                else runCommand("前除霜关", vc::frontDefrostOff);
                break;
            case "rear":
                if (checked) runCommand("后除霜开", vc::rearDefrostOn);
                else runCommand("后除霜关", vc::rearDefrostOff);
                break;
            default: break;
        }
    }

    // ═══════════════════════════════════════════════
    //  命令执行（后台 + Toast），车控页与车控卡共用
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
            // 车控后稍等，主动触发一轮采集
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

    @Override public void onSnapshot(DashboardSnapshot snap) {
        latest = snap;
        applySnapshot(snap);
    }

    private void applySnapshot(DashboardSnapshot snap) {
        if (snap == null) {
            updateStatusBar();
            return;
        }

        String soc = snap.batterySoc >= 0 ? String.valueOf(snap.batterySoc) : null;
        int range = snap.rangeDyn >= 0 ? snap.rangeDyn : snap.rangeStd;
        String volt = snap.voltage >= 0 ? fmt1(snap.voltage) : "--";
        powerCard.setData(soc, "%",
                "续航 " + (range >= 0 ? range : "--") + " km · " + volt + " V");

        hvacCard.setTemps(halfToC(snap.driverTempHalf), halfToC(snap.passengerTempHalf));
        hvacCard.setFan(snap.fanSpeed);
        hvacCard.setToggles(snap.acSwitch, -1, snap.innerCycle, snap.frontDefrost, snap.rearDefrost);

        outTempCard.setData(snap.outsideTemp >= 0 ? String.valueOf(snap.outsideTemp) : null,
                "℃", "车外温度");
        setVol(mediaVolCard, snap.musicVol, "媒体音量");
        setVol(naviVolCard, snap.naviVol, "导航音量");
        setVol(speechVolCard, snap.speechVol, "语音音量");
        setVol(callVolCard, snap.callVol, "通话音量");

        tiresCard.setTires(snap.tirePressKpa, snap.tireTempC);
        doorsCard.setDoors(snap.doorStates);

        spanCard.setData(computePower(snap), "kW",
                "电流 " + (snap.current >= 0 ? fmt1(snap.current) : "--") + " A");

        if (windowCard != null) windowCard.setPositions(snap.windowPct);
        if (signalPage != null) signalPage.setRows(snap.rows);

        updateStatusBar();
    }

    private static void setVol(DataCardView card, int v, String label) {
        card.setData(v >= 0 ? String.valueOf(v) : null, "", label);
    }

    /** 功率 kW = 电压 V × 电流 A / 1000；任一缺失返 null（UI 显失败，不臆测） */
    private static String computePower(DashboardSnapshot s) {
        if (s.voltage >= 0 && s.current >= 0) {
            return fmt1(s.voltage * s.current / 1000f);
        }
        return null;
    }

    private static String fmt1(float v) {
        return String.format(java.util.Locale.US, "%.1f", v);
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

    private boolean isCompactScreen() {
        return getResources().getDisplayMetrics().heightPixels < 900;
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
