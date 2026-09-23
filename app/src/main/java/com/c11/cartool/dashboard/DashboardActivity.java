package com.c11.cartool.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.c11.cartool.AppInfo;
import com.c11.cartool.CrashHandler;
import com.c11.cartool.Logger;
import com.c11.cartool.MainActivity;
import com.c11.cartool.Sh;
import com.c11.cartool.WebServer;
import com.c11.cartool.util.QrBitmap;
import com.c11.cartool.vehicle.VehicleController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * C11 车控 v0.3.5 —— 公开发布版仪表盘（打开即桌面）。
 *
 * <p>布局：顶部状态条（ADB/档位/车速/电量/时间/Web/工程） + 固定网格卡片（数据方块/车控按钮/工具入口） + 底部 Dock。
 * <p>数据：{@link DashboardRepository} 每 5s 采集 settings 真机键 + logcat 实时量，无值一律 "--"。
 * <p>车控：只接 VehicleController 已验证方法；锁车/后备箱/车窗等标注 ⚠ 实验性照常放出。
 * <p>工程模式：右上角进入 MainActivity（一键全测/诊断/车控实验/25 类参数浏览等调试工具）。
 */
public class DashboardActivity extends Activity {

    private Handler h;
    private VehicleController vc;
    private WebServer webServer;
    private DashboardRepository repository;
    private DashboardSnapshot lastSnap = new DashboardSnapshot();

    // 内容区两页：仪表盘网格 / 全车信号清单
    private ViewSwitcher contentSwitcher;
    private SignalListPage signalPage;
    private TextView signalChip;
    private ExecutorService controlPool = Executors.newSingleThreadExecutor();

    // 状态条
    private TextView adbStatusView;
    private TextView statusChipsView;   // 档位/车速/电量
    private TextView timeView;
    private TextView webView;

    // 卡片
    private DataCardView batteryCard;
    private DataCardView outsideTempCard;
    private DataCardView voltageCard;
    private DataCardView musicVolCard;
    private DataCardView naviVolCard;
    private DataCardView speechVolCard;
    private DataCardView pm25Card;
    private HvacCardView hvacCard;
    private TiresCardView tiresCard;
    private DoorsCardView doorsCard;
    private LockCardView lockCard;
    private MiniGridCardView lightsCard;
    private MiniGridCardView windowsCard;
    private MiniGridCardView extraCard;

    private volatile boolean is720 = false;
    private final Sh.StateListener adbListener = this::onAdbStateChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());
        CrashHandler.install(this);

        Sh.setContext(this);
        vc = new VehicleController(this);

        // Web 服务随启动（失败不阻塞；Dock/状态条可手动重试）
        webServer = new WebServer();
        webServer.setVehicleController(vc);
        try { startWebAuto(); } catch (Exception e) { Logger.error("Web 自启异常: " + e.getMessage()); }

        Sh.startKeepAlive();
        Sh.addStateListener(adbListener);

        setContentView(buildUI());
        bindCardActions();

        repository = new DashboardRepository(h, this::onSnapshot);
        // 采集在 onResume 启动（此时视图已挂载、卡片 buildContent 已完成，避免首帧竞争）

        startClock();
    }

    // ═════════════ UI 构建 ═════════

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DashboardTheme.BG);

        root.addView(buildStatusBar());

        // 内容区：ViewSwitcher 在「仪表盘网格」与「全车信号清单」两页间切换
        contentSwitcher = new ViewSwitcher(this);
        contentSwitcher.setInAnimation(this, android.R.anim.fade_in);
        contentSwitcher.setOutAnimation(this, android.R.anim.fade_out);

        DashboardGridView grid = new DashboardGridView(this, 12, rows(), marginDp(), gapDp());
        contentSwitcher.addView(grid, new ViewSwitcher.LayoutParams(
                ViewSwitcher.LayoutParams.MATCH_PARENT, ViewSwitcher.LayoutParams.MATCH_PARENT));
        buildGridCards(grid);

        signalPage = new SignalListPage(this);
        contentSwitcher.addView(signalPage, new ViewSwitcher.LayoutParams(
                ViewSwitcher.LayoutParams.MATCH_PARENT, ViewSwitcher.LayoutParams.MATCH_PARENT));

        root.addView(contentSwitcher, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildDock());

        return root;
    }

    private boolean isTallScreen() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.heightPixels >= 900;
    }

    private int rows() { is720 = !isTallScreen(); return is720 ? 4 : 7; }
    private int marginDp() { return is720 ? 12 : 16; }
    private int gapDp() { return is720 ? 8 : 10; }
    private int cornerDp() { return is720 ? 12 : 16; }

    // ── 顶部状态条 ──
    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(DashboardTheme.SURFACE);
        bar.setPadding(dp(14), dp(8), dp(8), dp(8));

        // 版本号（第一屏可辨，防止安装旧包白跑）
        TextView verView = chip("🚗 v" + AppInfo.VERSION, DashboardTheme.CYAN);
        verView.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(verView, chipLp());

        // ADB 状态（点击连接）
        adbStatusView = chip("ADB", DashboardTheme.RED);
        adbStatusView.setOnClickListener(v -> {
            if (!Sh.isAdbConnected()) {
                runBackground("连接 ADB", () -> {
                    boolean ok = Sh.connectLocalAdb();
                    post(() -> Toast.makeText(this, ok ? "ADB 已连接" : "ADB 连接失败（详见工程模式）",
                            Toast.LENGTH_SHORT).show());
                });
            } else {
                showWebInfoDialog(true);
            }
        });
        bar.addView(adbStatusView, chipLp());

        statusChipsView = chip("档 - / 速 - / 电 -", DashboardTheme.TEXT);
        bar.addView(statusChipsView, chipLp());

        // Web 状态
        webView = chip("Web 关", DashboardTheme.DIM);
        webView.setOnClickListener(v -> showWebInfoDialog(false));
        bar.addView(webView, chipLp());

        // 全车信号清单（点击切换整页）
        signalChip = chip("📊 信号", DashboardTheme.CYAN);
        signalChip.setOnClickListener(v -> toggleSignalPage());
        bar.addView(signalChip, chipLp());

        // 时间
        timeView = new TextView(this);
        timeView.setTextColor(DashboardTheme.TEXT);
        timeView.setTextSize(16);
        timeView.setTypeface(Typeface.DEFAULT_BOLD);
        timeView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(timeView, chipLp());

        // 工程模式
        TextView eng = chip("⚙ 工程", DashboardTheme.PURPLE);
        eng.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        bar.addView(eng, chipLp());

        return bar;
    }

    /** 在仪表盘网格与全车信号清单之间切换，chip 文案随页变化。 */
    private void toggleSignalPage() {
        boolean toList = contentSwitcher.getDisplayedChild() == 0;
        contentSwitcher.setDisplayedChild(toList ? 1 : 0);
        signalChip.setText(toList ? "← 仪表盘" : "📊 信号");
    }

    private TextView chip(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    // ── 网格卡片 ──
    private void buildGridCards(DashboardGridView grid) {
        int c = cornerDp();

        if (!is720) {
            // ── 中控 1920×1080：12×7 ──
            batteryCard = new DataCardView(this, "电量 / 续航 / 电压", c);
            grid.addCard(batteryCard, 0, 0, 3, 2);

            hvacCard = new HvacCardView(this, false, c);
            grid.addCard(hvacCard, 3, 0, 4, 4);

            outsideTempCard = new DataCardView(this, "车外温度", c);
            grid.addCard(outsideTempCard, 7, 0, 2, 1);

            musicVolCard = new DataCardView(this, "媒体音量", c);
            grid.addCard(musicVolCard, 9, 0, 2, 1);

            voltageCard = new DataCardView(this, "电池电压", c);
            grid.addCard(voltageCard, 7, 1, 2, 1);

            speechVolCard = new DataCardView(this, "语音音量", c);
            grid.addCard(speechVolCard, 9, 1, 2, 1);

            doorsCard = new DoorsCardView(this, c);
            grid.addCard(doorsCard, 0, 2, 3, 2);

            tiresCard = new TiresCardView(this, c);
            grid.addCard(tiresCard, 7, 2, 2, 2);

            naviVolCard = new DataCardView(this, "导航音量", c);
            grid.addCard(naviVolCard, 9, 2, 2, 1);

            pm25Card = new DataCardView(this, "车内 PM2.5", c);
            grid.addCard(pm25Card, 9, 3, 2, 1);

            // 第 5 行：车窗 / 灯光 / 儿童锁 / 后备箱（一排迷你开关）
            windowsCard = new MiniGridCardView(this, "车窗⚠",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("win_fl", "左前", "🪟", true));
                        add(new MiniGridCardView.Item("win_fr", "右前", "🪟", true));
                        add(new MiniGridCardView.Item("win_rl", "左后", "🪟", true));
                        add(new MiniGridCardView.Item("win_rr", "右后", "🪟", true));
                    }}, c);
            grid.addCard(windowsCard, 0, 4, 3, 1);

            lightsCard = new MiniGridCardView(this, "灯光",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("low_beam", "近光", "💡", false));
                        add(new MiniGridCardView.Item("position", "示廓", "🚥", false));
                        add(new MiniGridCardView.Item("fog", "后雾", "🌫", false));
                        add(new MiniGridCardView.Item("view360", "360", "📷", false));
                    }}, c);
            grid.addCard(lightsCard, 3, 4, 4, 1);

            MiniGridCardView childCard = new MiniGridCardView(this, "儿童锁",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("child_l", "左", "🔒", false));
                        add(new MiniGridCardView.Item("child_r", "右", "🔒", false));
                    }}, c);
            childCard.setListener(this::onMiniToggle);
            grid.addCard(childCard, 7, 4, 2, 1);

            MiniGridCardView trunkCard = new MiniGridCardView(this, "后备箱⚠",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("trunk_open", "开", "🚪", true));
                        add(new MiniGridCardView.Item("trunk_close", "关", "🚪", true));
                    }}, c);
            trunkCard.setListener(this::onMiniToggle);
            grid.addCard(trunkCard, 9, 4, 2, 1);

            // 第 6 行：工具条
            grid.addCard(makeToolCard("🌐", "Web 遥控", () -> showWebInfoDialog(false)), 0, 5, 2, 1);
            grid.addCard(makeToolCard("🧪", "一键诊断", () -> openEngine("诊断")), 2, 5, 2, 1);
            grid.addCard(makeToolCard("⚙", "一键全测", () -> openEngine("全测")), 4, 5, 2, 1);
            grid.addCard(makeToolCard("🎮", "车控实验", () -> openEngine("实验")), 6, 5, 2, 1);
            grid.addCard(makeToolCard("📋", "日志导出", () -> openEngine("日志")), 8, 5, 2, 1);
            grid.addCard(makeToolCard("⚙", "工程模式", () -> startActivity(new Intent(this, MainActivity.class))), 10, 5, 2, 1);

            // 第 7 行：锁车大卡 / 其它开关 / 快捷工具
            lockCard = new LockCardView(this, c);
            grid.addCard(lockCard, 0, 6, 4, 1);

            extraCard = new MiniGridCardView(this, "其它⚠",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("mirror_heat", "镜加热", "♨", true));
                        add(new MiniGridCardView.Item("window_forbit", "窗锁", "🔐", true));
                        add(new MiniGridCardView.Item("max_cool", "最大制冷", "❄", false));
                        add(new MiniGridCardView.Item("ac_page", "空调界面", "🖥", false));
                    }}, c);
            grid.addCard(extraCard, 4, 6, 4, 1);

            grid.addCard(makeToolCard("🔊", "音量", () -> openEngine("音量")), 8, 6, 2, 1);
            grid.addCard(makeToolCard("📡", "网络", () -> showWebInfoDialog(false)), 10, 6, 2, 1);
        } else {
            // ── 仪表/副驾 1920×720：12×4 精简 ──
            batteryCard = new DataCardView(this, "电量 / 续航 / 电压", c);
            grid.addCard(batteryCard, 0, 0, 3, 2);

            hvacCard = new HvacCardView(this, true, c);
            grid.addCard(hvacCard, 3, 0, 4, 2);

            outsideTempCard = new DataCardView(this, "车外温度", c);
            grid.addCard(outsideTempCard, 7, 0, 2, 1);

            musicVolCard = new DataCardView(this, "媒体音量", c);
            grid.addCard(musicVolCard, 9, 0, 2, 1);

            voltageCard = new DataCardView(this, "电池电压", c);
            grid.addCard(voltageCard, 7, 1, 2, 1);

            speechVolCard = new DataCardView(this, "语音音量", c);
            grid.addCard(speechVolCard, 9, 1, 2, 1);

            doorsCard = new DoorsCardView(this, c);
            grid.addCard(doorsCard, 0, 2, 3, 2);

            lightsCard = new MiniGridCardView(this, "灯光 / 车身",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("low_beam", "近光", "💡", false));
                        add(new MiniGridCardView.Item("position", "示廓", "🚥", false));
                        add(new MiniGridCardView.Item("fog", "后雾", "🌫", false));
                        add(new MiniGridCardView.Item("view360", "360", "📷", false));
                        add(new MiniGridCardView.Item("child_l", "左童锁", "🔒", false));
                        add(new MiniGridCardView.Item("child_r", "右童锁", "🔒", false));
                        add(new MiniGridCardView.Item("trunk_open", "后备箱开", "🚪", true));
                        add(new MiniGridCardView.Item("trunk_close", "后备箱关", "🚪", true));
                    }}, c);
            grid.addCard(lightsCard, 3, 2, 4, 2);

            tiresCard = new TiresCardView(this, c);
            grid.addCard(tiresCard, 7, 2, 2, 2);

            naviVolCard = new DataCardView(this, "导航音量", c);
            grid.addCard(naviVolCard, 9, 2, 2, 1);

            pm25Card = new DataCardView(this, "PM2.5", c);
            grid.addCard(pm25Card, 9, 3, 2, 1);

            windowsCard = new MiniGridCardView(this, "车窗⚠",
                    new ArrayList<MiniGridCardView.Item>() {{
                        add(new MiniGridCardView.Item("win_fl", "左前", "🪟", true));
                        add(new MiniGridCardView.Item("win_fr", "右前", "🪟", true));
                        add(new MiniGridCardView.Item("win_rl", "左后", "🪟", true));
                        add(new MiniGridCardView.Item("win_rr", "右后", "🪟", true));
                    }}, c);
            grid.addCard(windowsCard, 0, 3, 3, 1);

            lockCard = new LockCardView(this, c);
            grid.addCard(lockCard, 3, 3, 4, 1);

            grid.addCard(makeToolCard("⚙", "工程", () -> startActivity(new Intent(this, MainActivity.class))), 7, 3, 2, 1);
        }
    }

    private ToolEntryCardView makeToolCard(String emoji, String name, Runnable action) {
        ToolEntryCardView card = new ToolEntryCardView(this, name, emoji, cornerDp());
        card.setListener(c -> action.run());
        return card;
    }

    private void openEngine(String hint) {
        // 跳工程模式（MainActivity），并 toast 提示位置
        Toast.makeText(this, "工程模式 → " + hint + "（在下方标签页内）", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
    }

    // ── 底部 Dock ──
    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackgroundColor(DashboardTheme.SURFACE);
        dock.setPadding(dp(8), dp(6), dp(8), dp(6));

        dock.addView(dockBtn("❄ 空调", true, () -> hvacToggle("ac", !isAcOn())));
        dock.addView(dockBtn("💧 前除雾", false, () -> hvacToggle("front", !isFrontOn())));
        dock.addView(dockBtn("🔥 后除雾", false, () -> hvacToggle("rear", !isRearOn())));
        dock.addView(dockBtn("↻ 循环", false, () -> hvacToggle("inner", !isInnerOn())));
        dock.addView(dockBtn("📷 360", true, () -> execAction("360 全景", () -> vc.open360View())));
        dock.addView(dockBtn("🚪 后备箱", true, () -> execAction("后备箱开关（OPEN）", () -> vc.openTrunk())));
        dock.addView(dockBtn("🔒 锁车⚠", true, () -> execAction("锁车（实验性，观察是否落锁）", () -> vc.lockCar())));
        dock.addView(dockBtn("🔓 解锁⚠", true, () -> execAction("解锁（实验性）", () -> vc.unlockCar())));
        dock.addView(dockBtn("🌐 Web", true, () -> showWebInfoDialog(false)));

        return dock;
    }

    private TextView dockBtn(String label, boolean accent, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(accent ? DashboardTheme.TEXT : DashboardTheme.DIM);
        btn.setTextSize(13);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    // ═════════════ 卡片动作绑定 ═════════

    private void bindCardActions() {
        if (hvacCard != null) {
            hvacCard.setListener(new HvacCardView.Listener() {
                @Override public void onTempStep(HvacCardView card, boolean driver, int delta) {
                    tempStep(driver, delta);
                }
                @Override public void onFanStep(HvacCardView card, int delta) { fanStep(delta); }
                @Override public void onToggle(HvacCardView card, String action, boolean checked) {
                    hvacToggle(action, checked);
                }
            });
        }
        if (lightsCard != null) lightsCard.setListener(this::onMiniToggle);
        if (windowsCard != null) windowsCard.setListener(this::onMiniToggle);
        if (extraCard != null) extraCard.setListener(this::onMiniToggle);
        if (lockCard != null) lockCard.setListener((card, lock) ->
                execAction(lock ? "锁车（实验性，观察是否落锁）" : "解锁（实验性）",
                        () -> { if (lock) vc.lockCar(); else vc.unlockCar(); }));
    }

    private void onMiniToggle(MiniGridCardView card, String id, boolean checked) {
        switch (id) {
            case "low_beam": execToggle("近光灯", checked, () -> vc.lowBeamOn(), () -> vc.lowBeamOff()); break;
            case "position": execToggle("示廓灯", checked, () -> vc.positionLightOn(), () -> vc.positionLightOff()); break;
            case "fog": execToggle("后雾灯", checked, () -> vc.fogLightOn(), () -> vc.fogLightOff()); break;
            case "view360": execAction("360 全景", () -> vc.open360View()); break;
            case "child_l": execAction("左儿童锁（开）", () -> vc.leftChildLockOn()); break;
            case "child_r": execAction("右儿童锁（开）", () -> vc.rightChildLockOn()); break;
            case "trunk_open": execAction("后备箱开", () -> vc.openTrunk()); break;
            case "trunk_close": execAction("后备箱关", () -> vc.closeTrunk()); break;
            case "win_fl": execToggle("左前窗 全开/全关⚠", checked, () -> vc.setWindow("front_left", 100), () -> vc.setWindow("front_left", 0)); break;
            case "win_fr": execToggle("右前窗 全开/全关⚠", checked, () -> vc.setWindow("front_right", 100), () -> vc.setWindow("front_right", 0)); break;
            case "win_rl": execToggle("左后窗 全开/全关⚠", checked, () -> vc.setWindow("rear_left", 100), () -> vc.setWindow("rear_left", 0)); break;
            case "win_rr": execToggle("右后窗 全开/全关⚠", checked, () -> vc.setWindow("rear_right", 100), () -> vc.setWindow("rear_right", 0)); break;
            case "mirror_heat": execToggle("后视镜加热⚠", checked, () -> vc.setGlobalKey("strCarMirrorHeart", "1"), () -> vc.setGlobalKey("strCarMirrorHeart", "0")); break;
            case "window_forbit": execToggle("车窗锁⚠", checked, () -> vc.setGlobalKey("strCarWindowForbit", "1"), () -> vc.setGlobalKey("strCarWindowForbit", "0")); break;
            case "max_cool": execToggle("最大制冷", checked, () -> vc.acMaxOn(), () -> vc.acMaxOff()); break;
            case "ac_page": execAction("打开空调界面", () -> vc.setGlobalKey("strCar100006", "1")); break;
            default: break;
        }
    }

    private void tempStep(boolean driver, int delta) {
        int cur = driver ? tempOf(lastSnap.driverTempHalf) : tempOf(lastSnap.passengerTempHalf);
        int next = Math.max(16, Math.min(32, cur + delta));
        execAction((driver ? "主驾" : "副驾") + "温度 → " + next + "℃", () -> {
            if (driver) vc.setAcTemperature(next); else vc.setAcTemperaturePassenger(next);
        });
    }

    private int tempOf(int halfValue) {
        return halfValue > 0 ? halfValue / 2 : 24;
    }

    private void fanStep(int delta) {
        int cur = lastSnap.fanSpeed > 0 ? lastSnap.fanSpeed : 4;
        int next = Math.max(1, Math.min(7, cur + delta));
        execAction("风量 → " + next + " 档", () -> vc.setAcFanSpeed(next));
    }

    private void hvacToggle(String action, boolean checked) {
        switch (action) {
            case "ac": execToggle("空调", checked, () -> vc.acSwitchOn(), () -> vc.acSwitchOff()); break;
            case "inner": execToggle("内循环", checked, () -> vc.setAirInnerLoop(true), () -> vc.setAirInnerLoop(false)); break;
            case "front": execToggle("前除霜", checked, () -> vc.frontDefrostOn(), () -> vc.frontDefrostOff()); break;
            case "rear": execToggle("后除霜", checked, () -> vc.rearDefrostOn(), () -> vc.rearDefrostOff()); break;
            case "max": execToggle("最大制冷", checked, () -> vc.acMaxOn(), () -> vc.acMaxOff()); break;
            default: break;
        }
    }

    private boolean isAcOn() { return lastSnap.acSwitch == 1; }
    private boolean isFrontOn() { return lastSnap.frontDefrost == 1; }
    private boolean isRearOn() { return lastSnap.rearDefrost == 1; }
    private boolean isInnerOn() { return lastSnap.innerCycle == 1; }

    // ═════════════ 车控执行（统一：后台执行 + toast + 触发刷新） ═════════

    private void execAction(String name, Runnable action) {
        if (!Sh.isAdbConnected()) {
            Toast.makeText(this, "ADB 未连接，命令可能无效（点状态条连接）", Toast.LENGTH_SHORT).show();
        }
        Logger.info("车控: " + name);
        controlPool.execute(() -> {
            boolean ok;
            try { action.run(); ok = true; } catch (Exception e) { ok = false; Logger.error("车控失败: " + e.getMessage()); }
            final boolean fok = ok;
            post(() -> Toast.makeText(this, name + (fok ? " 已发送" : " 失败"), Toast.LENGTH_SHORT).show());
            repository.requestRefresh();
        });
    }

    private void execToggle(String name, boolean checked, Runnable on, Runnable off) {
        execAction(name + (checked ? " 开" : " 关"), checked ? on : off);
    }

    private void runBackground(String name, Runnable action) {
        controlPool.execute(action);
    }

    // ═════════════ 数据刷新 ═════════

    private void onSnapshot(DashboardSnapshot snap) {
        lastSnap = snap;
        // 信号清单页不依赖卡片挂载，首帧即可填充
        if (signalPage != null) signalPage.setRows(snap.rows);
        // 视图尚未挂载时跳过本轮（卡片 buildContent 未完成），下一轮 5s 后自动补上
        if (batteryCard == null || !batteryCard.isAttachedToWindow()) return;

        // ADB 状态
        if (snap.adbConnected) {
            adbStatusView.setText("ADB ✓");
            adbStatusView.setTextColor(DashboardTheme.GREEN);
        } else {
            adbStatusView.setText("ADB ✗");
            adbStatusView.setTextColor(DashboardTheme.RED);
        }

        // 档位/车速/电量
        StringBuilder chips = new StringBuilder();
        chips.append("档 ").append(snap.gear.isEmpty() ? "-" : snap.gear);
        chips.append("  速 ").append(snap.speedKmh >= 0 ? snap.speedKmh : "-").append("km/h");
        chips.append("  电 ").append(snap.batterySoc >= 0 ? snap.batterySoc + "%" : "-");
        statusChipsView.setText(chips.toString());

        // Web
        boolean webOn = webServer != null && webServer.isRunning();
        webView.setText(webOn ? "Web " + webServer.getPort() : "Web 关");
        webView.setTextColor(webOn ? DashboardTheme.GREEN : DashboardTheme.DIM);

        // 电量卡：电量% / 电压V / 续航km
        batteryCard.setData(snap.batterySoc >= 0 ? String.valueOf(snap.batterySoc) : null, "%",
                subLine(snap.voltage > 0 ? "电压 " + one(snap.voltage) + "V" : null,
                        snap.rangeStd > 0 ? "续航 " + snap.rangeStd + "km" : null));

        outsideTempCard.setData((snap.outsideTemp != -1 && inRange(snap.outsideTemp, -50, 60)) ? String.valueOf(snap.outsideTemp) : null, "℃", null);
        voltageCard.setData(snap.voltage > 100 && snap.voltage < 600 ? one(snap.voltage) : null, "V", null);
        musicVolCard.setData(inRange(snap.musicVol, 0, 100) ? String.valueOf(snap.musicVol) : null, null, null);
        speechVolCard.setData(inRange(snap.speechVol, 0, 100) ? String.valueOf(snap.speechVol) : null, null, null);
        naviVolCard.setData(inRange(snap.naviVol, 0, 100) ? String.valueOf(snap.naviVol) : null, null, null);
        pm25Card.setData(inRange(snap.pm25, 0, 1000) ? String.valueOf(snap.pm25) : null, "µg/m³", null);

        if (hvacCard != null) {
            hvacCard.setTemps(tempOf(snap.driverTempHalf), tempOf(snap.passengerTempHalf));
            hvacCard.setFan(snap.fanSpeed);
            hvacCard.setToggles(snap.acSwitch, -1, snap.innerCycle, snap.frontDefrost, snap.rearDefrost);
        }
        if (tiresCard != null) tiresCard.setTires(snap.tirePressKpa, snap.tireTempC);
        if (doorsCard != null) doorsCard.setDoors(snap.doorStates);
    }

    private String subLine(String a, String b) {
        if (a == null && b == null) return "";
        if (a == null) return b;
        if (b == null) return a;
        return a + "  " + b;
    }

    private static boolean inRange(int v, int lo, int hi) { return v >= lo && v <= hi; }

    private String one(float v) {
        return String.format(Locale.US, "%.1f", v);
    }

    // ═════════════ Web ═════════

    private void startWebAuto() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("c11cartool", MODE_PRIVATE);
            int port = prefs.getInt("web_port", 8080);
            boolean ok = webServer.start(port);
            if (ok) prefs.edit().putInt("web_port", webServer.getPort()).apply();
        } catch (Exception e) {
            Logger.error("Web 自启异常: " + e.getMessage());
        }
    }

    private void showWebInfoDialog(boolean adbOnly) {
        int port = webServer.isRunning() ? webServer.getPort() : 8080;
        String url = "http://" + WebServer.getDeviceIp() + ":" + port;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);

        // 二维码：手机连同一 WiFi 扫码即开
        ImageView qrView = new ImageView(this);
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap qrBmp = QrBitmap.toBitmap(url, 8);
        if (qrBmp != null) {
            qrView.setImageBitmap(qrBmp);
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(220), dp(220));
            qlp.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(qrView, qlp);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n手机连同一 WiFi，扫码或浏览器输入：\n  ").append(url).append("\n\n");
        sb.append("本机全部 IP（手机须与 wlan0 同网段）：\n");
        for (String s : WebServer.getAllIps()) sb.append("  ").append(s).append("\n");
        if (adbOnly) sb.append("\nADB 已连接，可点「工程模式」做完整调试。");
        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(13);
        box.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("🌐 Web 遥控")
                .setView(box)
                .setPositiveButton("好的", null)
                .show();
    }

    // ═════════════ 时钟 & 生命周期 ═════════

    private void startClock() {
        final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
        h.post(new Runnable() {
            @Override public void run() {
                timeView.setText(fmt.format(new Date()));
                h.postDelayed(this, 1000);
            }
        });
    }

    private void onAdbStateChanged(boolean connected, int uid) {
        post(() -> {
            if (connected) { adbStatusView.setText("ADB ✓"); adbStatusView.setTextColor(DashboardTheme.GREEN); }
            else { adbStatusView.setText("ADB ✗"); adbStatusView.setTextColor(DashboardTheme.RED); }
            repository.requestRefresh();
        });
    }

    private void post(Runnable r) { if (h != null) h.post(r); }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 视图已挂载，此时才启动采集（避免首帧与 buildContent 竞争）
        if (repository != null && !repository.isRunning()) {
            repository.start();
        }
        if (repository != null) repository.requestRefresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null) repository.stop();
        Sh.removeStateListener(adbListener);
        try { if (webServer != null) webServer.stop(); } catch (Exception ignored) {}
        controlPool.shutdownNow();
    }
}
