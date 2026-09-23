package com.c11.cartool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * C11 车控测试工具 v2.1
 *
 * 新增:
 *   - ADB 连接管理 (自动本地/自定义地址端口)
 *   - 日志导出 + 时间戳
 *   - 参数快照导出/导入
 *   - 主题跟随系统
 *   - 性能监控
 */
public class MainActivity extends Activity {

    private Handler h;
    private TextView statusLine;
    private TextView logView;
    private ScrollView logScroll;
    private TextView vehLogView;
    private ScrollView vehLogScroll;
    private boolean polling = false;
    private LinearLayout contentArea;
    private EditText searchBox;
    private SharedPreferences prefs;
    private com.c11.cartool.vehicle.VehicleController vehicleController;

    // ADB 连接配置
    private String adbHost = "127.0.0.1";
    private int adbPort = 5555;
    private boolean adbCustom = false;

    // Web 远程控制服务器
    private WebServer webServer = null;

    // 性能监控
    private long lastCmdTime = 0;
    private int cmdCount = 0;

    // 颜色 (深色主题)
    static final int C_BG = 0xFF0F1117;
    static final int C_SURFACE = 0xFF1A1D27;
    static final int C_CARD = 0xFF1E2330;
    static final int C_TEXT = 0xFFE4E4E7;
    static final int C_DIM = 0xFF8B8FA3;
    static final int C_BLUE = 0xFF3B82F6;
    static final int C_GREEN = 0xFF22C55E;
    static final int C_RED = 0xFFEF4444;
    static final int C_YELLOW = 0xFFEAB308;
    static final int C_CYAN = 0xFF06B6D4;
    static final int C_ORANGE = 0xFFF97316;
    static final int C_PURPLE = 0xFFA855F7;

    // 浅色主题
    static final int C_LIGHT_BG = 0xFFF5F5F5;
    static final int C_LIGHT_SURFACE = 0xFFFFFFFF;
    static final int C_LIGHT_CARD = 0xFFF0F0F0;
    static final int C_LIGHT_TEXT = 0xFF1A1A1A;
    static final int C_LIGHT_DIM = 0xFF666666;

    private boolean darkTheme = true;

    // 首页状态视图引用（每 3 秒自动刷新）
    private TextView homeAdbStatusView = null;
    private TextView homeWebStatusView = null;
    private android.widget.ImageView homeQrView = null;

    // Tab 定义
    static final String[] TAB_NAMES = {
            "📊 全部", "❄️ 空调", "💡 灯光", "🪑 座椅", "🚗 车身",
            "🪟 车窗", "🏎️ 驾驶", "🎯 场景", "🔊 音量", "⚡ 充电",
            "🔋 电池", "🛡️ ADAS", "🔧 行车", "📡 系统", "🔒 安全",
            "🎵 媒体", "🗺️ 导航", "📱 蓝牙", "🌧️ 雨刷", "🌡️ 环境",
            "📏 里程", "🏷️ 车辆", "🎮 车控实验", "📋 日志", "⚙️ 设置"
    };

    static final String[] TAB_FILTERS = {
            null, "空调|hvac|temp|defrost|ac_", "灯光|light|ambient|氛围",
            "座椅|seat|steering|按摩|加热|通风", "车身|vehicle|child|mirror|trunk|window_lock|锁|门",
            "车窗|window|sunroof|sunshade|天窗|遮阳", "驾驶|drive|steer|energy|转向|能量|回收",
            "场景|scene|rest|camping|guard|sentinel|小憩|露营|守护|哨兵", "音量|volume|C11_|SPEECH|XIAOLING|语音|小灵",
            "充电|charging|charge|gun_lock", "电池|battery|range|续航|里程",
            "ADAS|adas|acc|aeb|lka|ldw|bsd|fcw|dow|rcta|rcw|alc|tsr|hwa|lcc|tja|ica|isa|slif|bsi|ir|sdis|sai|parking|pdc|hdc|ccs",
            "疲劳|face|dms|creep|one_pedal|低速|蠕行|踏板", "系统|system|wifi|bluetooth|hotspot|dark|pedestrian|update|usb|network",
            "安全|safety|lock_sound|find_car|flash|belt|password|guest", "媒体|media|source|playing|track|artist|album|fm|eq",
            "导航|navi|destination|distance|eta|traffic", "蓝牙|bt|phone|电话|来电",
            "雨刷|wiper|spray|喷水", "环境|env|pm25|temp_inside|temp_outside", "里程|odo|trip",
            "车辆|car|vin|model|year|color|config|gear|speed|rpm", null, null, null
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());

        // 安装全局异常捕获（必须在最前面）
        CrashHandler.install(this);

        // 设置 Shell 上下文（用于 ADB 密钥存储）
        Sh.setContext(this);
        vehicleController = new com.c11.cartool.vehicle.VehicleController(this);

        // 加载配置
        prefs = getSharedPreferences("c11cartool", MODE_PRIVATE);
        adbHost = prefs.getString("adb_host", "127.0.0.1");
        adbPort = prefs.getInt("adb_port", 5555);
        adbCustom = prefs.getBoolean("adb_custom", false);
        darkTheme = prefs.getBoolean("dark_theme", true);

        Logger.setCallback((line, level) -> h.post(() -> appendLog(line, level)));

        // 性能监控: 记录命令耗时
        Logger.setPerfCallback((cmd, durationMs) -> {
            cmdCount++;
            lastCmdTime = durationMs;
        });

        setContentView(buildUI());

        // 初始化（本地运行模式，不需要 ADB 连接）
        Sh.submitAsync(() -> {
            DeviceInfo info = DeviceInfo.detect();
            int uid = Sh.getAdbUid();
            String uidLabel;
            if (uid == 0) uidLabel = "ROOT";
            else if (uid == 2000) uidLabel = "SHELL";
            else if (uid >= 10000) uidLabel = "APP(u" + (uid - 10000) + ")";
            else uidLabel = "uid=" + uid;

            h.post(() -> statusLine.setText(String.format("%s | %s | Android %s",
                    uidLabel, info.model, info.android)));

            Logger.title(AppInfo.TITLE);
            Logger.info("设备: " + info.model + " | Android " + info.android);
            Logger.info("运行模式: 车机本地 (uid=" + uid + ")");
            Logger.info("参数: " + VehicleParams.getCount() + " 个");
            Logger.warn("注意: 普通应用权限可能受限，如车控无效请运行诊断模式");
        });

        // v0.3 修复：Web 服务随 APP 自启（失败不阻塞，首页/设置可手动重试）
        try { startWebAuto(); } catch (Exception e) { Logger.error("Web 自启异常: " + e.getMessage()); }
        // v0.3 修复：ADB 心跳保活（长连接、断线自动重连、状态实时刷新）
        Sh.startKeepAlive();
        // v0.3 修复：诊断报告可导出到应用目录
        DiagnosticMode.setContext(this);
        // v0.3 修复：首页状态每 3 秒刷新
        startHomeStatusRefresh();
    }

    /**
     * 更新顶部状态栏（uid + 设备 + Android版本）
     */
    private void updateStatusLine() {
        Sh.submitAsync(() -> {
            int uid = Sh.getAdbUid();
            String uidLabel;
            if (uid == 0) uidLabel = "ROOT";
            else if (uid == 2000) uidLabel = "SHELL";
            else if (uid >= 10000) uidLabel = "APP(u" + (uid - 10000) + ")";
            else uidLabel = "uid=" + uid;

            String model = Sh.out("getprop ro.product.model");
            String android = Sh.out("getprop ro.build.version.release");
            if (model == null || model.isEmpty()) model = "unknown";
            if (android == null || android.isEmpty()) android = "?";

            final String text = String.format("%s | %s | Android %s", uidLabel, model, android);
            h.post(() -> {
                if (statusLine != null) statusLine.setText(text);
            });
        });
    }

    // ═══════════════════════════════════════
    //  ADB 连接管理（使用 Sh 中的 AdbClient）
    // ═══════════════════════════════════════

    // ═══════════════════════════════════════
    //  UI 构建
    // ═══════════════════════════════════════

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(darkTheme ? C_BG : C_LIGHT_BG);

        root.addView(buildHeader());
        root.addView(buildSearchBar());
        root.addView(buildTabBar());

        ScrollView scroll = new ScrollView(this);
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(8, 4, 8, 4);
        scroll.addView(contentArea);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildLogArea());
        switchTab(0);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        header.setPadding(12, 8, 12, 8);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(AppInfo.TITLE);
        title.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 性能指示
        TextView perf = new TextView(this);
        perf.setText("⚡");
        perf.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        perf.setTextColor(C_DIM);
        perf.setPadding(8, 0, 8, 0);
        header.addView(perf);

        statusLine = new TextView(this);
        statusLine.setText("连接中...");
        statusLine.setTextColor(C_DIM);
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        header.addView(statusLine);

        return header;
    }

    private View buildSearchBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        row.setPadding(8, 0, 8, 4);
        row.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = new EditText(this);
        searchBox.setHint("🔍 搜索参数...");
        searchBox.setHintTextColor(C_DIM);
        searchBox.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        searchBox.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        searchBox.setPadding(8, 6, 8, 6);
        row.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(makeSmallBtn("搜索", C_BLUE, v -> doSearch()));
        row.addView(makeSmallBtn("全部", C_SURFACE, v -> switchTab(0)));

        return row;
    }

    private View buildTabBar() {
        ScrollView tabScroll = new ScrollView(this);
        tabScroll.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        tabScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(4, 2, 4, 2);

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setText(TAB_NAMES[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            btn.setPadding(10, 4, 10, 4);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setTextColor(C_DIM);
            btn.setOnClickListener(v -> switchTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(1, 0, 1, 0);
            btn.setLayoutParams(lp);
            tabs.addView(btn);
        }

        tabScroll.addView(tabs);
        return tabScroll;
    }

    private View buildLogArea() {
        LinearLayout logArea = new LinearLayout(this);
        logArea.setOrientation(LinearLayout.VERTICAL);
        logArea.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        logArea.setPadding(6, 2, 6, 2);

        LinearLayout logHeader = new LinearLayout(this);
        logHeader.setOrientation(LinearLayout.HORIZONTAL);
        logHeader.addView(makeSmallBtn("📋 应用日志", C_BLUE, v -> {
            logScroll.setVisibility(View.VISIBLE);
            vehLogScroll.setVisibility(View.GONE);
        }));
        logHeader.addView(makeSmallBtn("📡 车辆日志", C_GREEN, v -> {
            logScroll.setVisibility(View.GONE);
            vehLogScroll.setVisibility(View.VISIBLE);
        }));
        logHeader.addView(makeSmallBtn("🗑 清空", C_RED, v -> {
            logView.setText("");
            vehLogView.setText("");
        }));
        logHeader.addView(makeSmallBtn("▶ 轮询", C_ORANGE, v -> togglePolling()));
        logHeader.addView(makeSmallBtn("📤 导出日志", C_PURPLE, v -> exportLogs()));
        logArea.addView(logHeader);

        // 应用日志
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        logScroll.setPadding(6, 6, 6, 6);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        logView.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        logView.setLineSpacing(1, 1);
        logScroll.addView(logView);
        logArea.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 180));

        // 车辆日志
        vehLogScroll = new ScrollView(this);
        vehLogScroll.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        vehLogScroll.setPadding(6, 6, 6, 6);
        vehLogScroll.setVisibility(View.GONE);
        vehLogView = new TextView(this);
        vehLogView.setTypeface(Typeface.MONOSPACE);
        vehLogView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        vehLogView.setTextColor(C_GREEN);
        vehLogView.setLineSpacing(1, 1);
        vehLogScroll.addView(vehLogView);
        logArea.addView(vehLogScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 180));

        return logArea;
    }

    // ═══════════════════════════════════════
    //  Tab 切换
    // ═══════════════════════════════════════

    private void switchTab(int idx) {
        contentArea.removeAllViews();

        // 首页：大按钮卡片（ADB状态 / Web服务 / 一键诊断 / 车控实验）
        if (idx == 0) { buildHomeTab(); return; }
        if (idx == TAB_NAMES.length - 2) { buildLogTab(); return; }
        if (idx == TAB_NAMES.length - 1) { buildSettingsTab(); return; }
        // 车控实验 tab（修复：此前 filter==null 落入全部参数列表，车控页从未显示）
        if (idx == 22) { buildCarControlTab(); return; }

        String filter = TAB_FILTERS[idx];
        List<String[]> params;
        if (filter == null) {
            params = new ArrayList<>();
            for (String[] p : VehicleParams.PARAMS) params.add(p);
        } else {
            params = VehicleParams.getByCategory(filter);
        }

        // 统计 + 快捷操作
        TextView countTv = new TextView(this);
        countTv.setText("共 " + params.size() + " 个参数 | 性能: " + cmdCount + " 次命令, 最近 " + lastCmdTime + "ms");
        countTv.setTextColor(C_DIM);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        countTv.setPadding(4, 4, 4, 4);
        contentArea.addView(countTv);

        // 全局快捷入口（所有 Tab 都显示，第一个 Tab 更醒目）
        contentArea.addView(buildGlobalQuickActions());

        if (filter != null) contentArea.addView(buildQuickActions(idx));

        contentArea.addView(makeBtn("📖 批量读取本页全部", C_BLUE, v -> {
            Sh.submitAsync(() -> {
                long start = System.currentTimeMillis();
                Logger.info("批量读取 " + params.size() + " 个参数...");
                for (String[] p : params) {
                    String val = VehicleControl.get(p[0], p[3]);
                    Logger.info(p[1] + " (" + p[0] + ") = " + val);
                }
                long elapsed = System.currentTimeMillis() - start;
                Logger.ok("批量读取完成, 耗时 " + elapsed + "ms");
            });
        }));

        for (String[] p : params) contentArea.addView(makeParamRow(p));
    }

    /**
     * 全局快捷入口栏（诊断/Web/ADB/日志，所有Tab顶部显示）
     */
    private View buildGlobalQuickActions() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF1A2540);
        panel.setPadding(10, 8, 10, 8);
        panel.setHorizontalGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("⚡ 核心功能（点击使用）");
        title.setTextColor(0xFF4FC3F7);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 6);
        panel.addView(title);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        // 一键诊断
        btnRow.addView(makeSmallBtn("🔧 一键诊断", 0xFF2E7D32, v -> {
            Sh.submitAsync(() -> {
                h.post(() -> Logger.title("=== 一键诊断开始 ==="));
                DiagnosticMode.runFullDiagnostic(new DiagnosticMode.ProgressCallback() {
                    @Override public void onProgress(String message) {
                        h.post(() -> Logger.info(message));
                    }
                    @Override public void onComplete(String fullReport) {
                        h.post(() -> {
                            Logger.ok("=== 诊断完成 ===");
                            showResultDialog("诊断报告", fullReport);
                            switchTab(TAB_NAMES.length - 2);
                        });
                    }
                });
            });
        }));

        // Web 远程控制
        boolean webRunning = webServer != null && webServer.isRunning();
        btnRow.addView(makeSmallBtn(webRunning ? "🌐 Web运行中" : "🌐 Web远程", webRunning ? 0xFF1565C0 : 0xFF0D47A1, v -> {
            switchTab(TAB_NAMES.length - 1); // 跳转到设置页
        }));

        // ADB 连接
        btnRow.addView(makeSmallBtn("📱 连接ADB", 0xFFE65100, v -> {
            Sh.submitAsync(() -> {
                h.post(() -> Logger.info("尝试连接本地 ADB..."));
                boolean ok = Sh.connectLocalAdb();
                h.post(() -> {
                    if (ok) {
                        // uid 检测重试（连接刚建立时前几个命令可能失败）
                        int uid = -1;
                        for (int i = 0; i < 3; i++) {
                            uid = Sh.getAdbUid();
                            if (uid == 2000) break;
                            try { Thread.sleep(300); } catch (Exception ignored) {}
                        }
                        Logger.ok("ADB 连接成功! uid=" + uid + (uid == 2000 ? " (SHELL权限)" : ""));
                        if (uid != 2000) {
                            Logger.warn("注意: uid=" + uid + " 不是 shell(2000)，车控可能受限");
                        }
                    } else {
                        Logger.error("ADB 连接失败，请确保 WiFi ADB 已开启");
                        Logger.info("开启方法: 系统 demo 软件 → turn on adb wifi");
                    }
                    updateStatusLine();
                });
            });
        }));

        // 查看日志
        btnRow.addView(makeSmallBtn("📋 查看日志", 0xFF4A148C, v -> {
            switchTab(TAB_NAMES.length - 2);
        }));

        panel.addView(btnRow);

        // 权限状态提示
        int uid = Sh.getAdbUid();
        String uidText;
        int uidColor;
        boolean adbConn = Sh.isAdbConnected();
        if (uid == 2000) { uidText = "✅ SHELL 权限 (uid=2000)，车控正常"; uidColor = 0xFF81C784; }
        else if (uid == 0) { uidText = "✅ ROOT 权限"; uidColor = 0xFF81C784; }
        else if (adbConn) { uidText = "⏳ ADB已连接，权限识别中..."; uidColor = 0xFFFFB74D; }
        else { uidText = "⚠️ 普通应用权限，车控受限，请连接ADB"; uidColor = 0xFFFFB74D; }

        TextView uidTip = new TextView(this);
        uidTip.setText(uidText);
        uidTip.setTextColor(uidColor);
        uidTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        uidTip.setGravity(Gravity.CENTER);
        uidTip.setPadding(0, 6, 0, 0);
        panel.addView(uidTip);

        return panel;
    }

    private View buildQuickActions(int tabIdx) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        panel.setPadding(8, 6, 8, 6);

        TextView title = new TextView(this);
        title.setText("⚡ 快捷操作");
        title.setTextColor(C_CYAN);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 4, 0, 0);

        switch (tabIdx) {
            case 1: // 空调
                btnRow.addView(makeSmallBtn("❄️ 最大制冷", C_GREEN, v -> { VehicleControl.setAcMax(true); Logger.ok("最大制冷 ON"); }));
                break;
            case 2: // 灯光
                btnRow.addView(makeSmallBtn("💡 近光灯", C_GREEN, v -> { VehicleControl.setLowBeam(true); Logger.ok("近光灯 ON"); }));
                btnRow.addView(makeSmallBtn("💡 示廓灯", C_GREEN, v -> { VehicleControl.setPositionLight(true); Logger.ok("示廓灯 ON"); }));
                btnRow.addView(makeSmallBtn("💡 后雾灯", C_GREEN, v -> { VehicleControl.setRearFog(true); Logger.ok("后雾灯 ON"); }));
                break;
            case 6: // 驾驶
                String[] modes = {"舒适", "运动", "自定义", "极致", "经济"};
                int[] colors = {C_GREEN, C_RED, C_YELLOW, C_PURPLE, C_CYAN};
                for (int i = 0; i < modes.length; i++) {
                    final int mode = i;
                    btnRow.addView(makeSmallBtn(modes[i], colors[i], v -> { VehicleControl.setDriverMode(mode); Logger.ok("驾驶模式: " + modes[mode]); }));
                }
                break;
            case 7: // 场景
                btnRow.addView(makeSmallBtn("😴 小憩", C_BLUE, v -> { VehicleControl.setRestMode(true); Logger.ok("小憩 ON"); }));
                btnRow.addView(makeSmallBtn("⛺ 露营", C_GREEN, v -> { VehicleControl.setCampingMode(true); Logger.ok("露营 ON"); }));
                btnRow.addView(makeSmallBtn("🛡️ 守护", C_YELLOW, v -> { VehicleControl.setGuardMode(true); Logger.ok("守护 ON"); }));
                btnRow.addView(makeSmallBtn("👁️ 哨兵", C_RED, v -> { VehicleControl.setSentinelMode(true); Logger.ok("哨兵 ON"); }));
                break;
            case 13: // 系统
                btnRow.addView(makeSmallBtn("📶 WiFi", C_GREEN, v -> { VehicleControl.setWifi(true); Logger.ok("WiFi ON"); }));
                btnRow.addView(makeSmallBtn("📱 蓝牙", C_GREEN, v -> { VehicleControl.setBluetooth(true); Logger.ok("蓝牙 ON"); }));
                break;
            case 21: // 车辆
                btnRow.addView(makeSmallBtn("🔒 锁车", C_GREEN, v -> { VehicleControl.setSetting("strCarVehicleLock", "1", "setting"); Logger.ok("锁车"); }));
                btnRow.addView(makeSmallBtn("🔓 解锁", C_BLUE, v -> { VehicleControl.setSetting("strCarVehicleLock", "0", "setting"); Logger.ok("解锁"); }));
                break;
        }

        if (btnRow.getChildCount() > 0) panel.addView(btnRow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        panel.setLayoutParams(lp);
        return panel;
    }

    // ═══════════════════════════════════════
    //  日志 Tab
    // ═══════════════════════════════════════

    private void buildLogTab() {
        contentArea.addView(makeSectionTitle("🔬 诊断模式"));
        contentArea.addView(makeBtn("🧪 运行完整诊断（检查权限/命令/广播/logcat）", C_ORANGE, v -> {
            Sh.submitAsync(() -> {
                Logger.title("开始运行诊断...");
                DiagnosticMode.runFullDiagnostic(new DiagnosticMode.ProgressCallback() {
                    @Override
                    public void onProgress(String message) {
                        h.post(() -> Logger.info(message));
                    }
                    @Override
                    public void onComplete(String fullReport) {
                        h.post(() -> {
                            Logger.ok("诊断完成！");
                            showResultDialog("诊断报告", fullReport);
                            // 自动导出诊断报告
                            String filename = "/sdcard/c11_diagnostic_" +
                                    new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) + ".txt";
                            Sh.writeFile(filename, fullReport);
                            Logger.info("诊断报告已保存: " + filename);
                        });
                    }
                });
            });
        }));

        contentArea.addView(makeSectionTitle("📋 日志工具"));

        contentArea.addView(makeBtn("📖 读取 logcat (最近50行)", C_BLUE, v -> {
            Sh.submitAsync(() -> {
                String log = VehicleControl.getLogcat(50);
                Logger.info("logcat:\n" + log);
            });
        }));

        contentArea.addView(makeBtn("🗑 清除 logcat", C_RED, v -> { VehicleControl.clearLogcat(); Logger.ok("logcat 已清除"); }));
        contentArea.addView(makeBtn("📖 读取全部 leap.* 属性", C_GREEN, v -> {
            Sh.submitAsync(() -> { String all = VehicleControl.getAllLeapProps(); Logger.info("leap.*:\n" + all); showResultDialog("leap.*", all); });
        }));
        contentArea.addView(makeBtn("📖 读取全部 strCar* 设置", C_GREEN, v -> {
            Sh.submitAsync(() -> { String all = VehicleControl.getAllCarSettings(); Logger.info("strCar*:\n" + all); showResultDialog("strCar*", all); });
        }));

        contentArea.addView(makeSectionTitle("📤 日志导出"));
        contentArea.addView(makeBtn("📤 导出应用日志到文件", C_PURPLE, v -> exportLogs()));
        contentArea.addView(makeBtn("📤 导出 logcat 到文件", C_PURPLE, v -> exportLogcat()));

        contentArea.addView(makeSectionTitle("🔧 自定义命令"));
        contentArea.addView(makeEditRow("getprop", "custom_prop", "prop", "leap.cabin.driver_temp", "属性名"));
        contentArea.addView(makeEditRow("settings get", "custom_setting", "setting", "C11_MUSIC", "设置名"));
        contentArea.addView(makeEditRow("shell 命令", "custom_shell", "shell", "id", "命令"));

        contentArea.addView(makeSectionTitle("🗣️ TTS 测试"));
        LinearLayout ttsRow = new LinearLayout(this);
        ttsRow.setOrientation(LinearLayout.HORIZONTAL);
        ttsRow.setPadding(0, 4, 0, 4);
        ttsRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText ttsInput = new EditText(this);
        ttsInput.setText("你好，我是小零");
        ttsInput.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        ttsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ttsInput.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        ttsInput.setPadding(8, 6, 8, 6);
        ttsRow.addView(ttsInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ttsRow.addView(makeSmallBtn("🗣️ 播放", C_GREEN, v -> {
            String text = ttsInput.getText().toString().trim();
            if (!text.isEmpty()) { VehicleControl.speak(text); Logger.ok("TTS: " + text); }
        }));
        contentArea.addView(ttsRow);

        contentArea.addView(makeSectionTitle("📋 ADB 授权"));
        contentArea.addView(makeBtn("📋 复制授权命令", C_YELLOW, v -> {
            copyToClipboard("adb shell pm grant com.c11.cartool android.permission.WRITE_SECURE_SETTINGS");
            Logger.ok("已复制");
        }));
    }

    // ═══════════════════════════════════════
    //  设置 Tab
    // ═══════════════════════════════════════

    private void buildSettingsTab() {
        contentArea.addView(makeSectionTitle("⚙️ 设置"));

        // ═══ ADB 连接管理（真实 ADB 客户端） ═══
        contentArea.addView(makeSectionTitle("📡 ADB Shell 连接（获取 shell uid=2000 权限）"));

        // 连接状态
        boolean adbConnected = Sh.isAdbConnected();
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        statusRow.setPadding(8, 6, 8, 6);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("状态:");
        statusLabel.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusRow.addView(statusLabel);

        TextView statusValue = new TextView(this);
        if (adbConnected) {
            statusValue.setText("✅ 已连接 (shell uid=2000)");
            statusValue.setTextColor(C_GREEN);
        } else {
            statusValue.setText("❌ 未连接 (应用 uid=10107)");
            statusValue.setTextColor(C_RED);
        }
        statusValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusValue.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(statusValue);
        contentArea.addView(statusRow);

        // 说明
        TextView adbHint = new TextView(this);
        adbHint.setText("连接后所有命令通过 adb shell 执行，可突破零跑系统的 MANAGE_USERS 权限限制，读取/写入 settings、执行 am broadcast、读取系统 logcat。\n\n前提：车机已开启 WiFi ADB（通过系统 demo 软件按钮开启）");
        adbHint.setTextColor(C_DIM);
        adbHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        adbHint.setPadding(8, 4, 8, 8);
        contentArea.addView(adbHint);

        // 连接按钮行
        LinearLayout connRow = new LinearLayout(this);
        connRow.setOrientation(LinearLayout.HORIZONTAL);
        connRow.setPadding(0, 4, 0, 4);
        connRow.addView(makeSmallBtn("🔌 连接本地 adbd", C_GREEN, v -> {
            Sh.submitAsync(() -> {
                Logger.info("正在连接本地 adbd (127.0.0.1:5555)...");
                boolean ok = Sh.connectLocalAdb();
                h.post(() -> {
                    if (ok) {
                        Logger.ok("ADB 连接成功！当前 shell uid: " + Sh.out("id -u"));
                        // 连接成功后自动授权
                        autoGrantPermissions();
                    } else {
                        Logger.error("ADB 连接失败，请确认 WiFi ADB 已开启");
                    }
                    switchTab(TAB_NAMES.length - 1);
                });
            });
        }));
        connRow.addView(makeSmallBtn("🔌 断开", C_RED, v -> {
            Sh.disconnectAdb();
            Logger.info("ADB 已断开，恢复本地 shell 模式");
            switchTab(TAB_NAMES.length - 1);
        }));
        contentArea.addView(connRow);

        // 自定义地址
        LinearLayout addrRow = new LinearLayout(this);
        addrRow.setOrientation(LinearLayout.HORIZONTAL);
        addrRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        addrRow.setPadding(8, 6, 8, 6);
        addrRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText hostEt = new EditText(this);
        hostEt.setText(adbHost);
        hostEt.setTextColor(C_YELLOW);
        hostEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hostEt.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        hostEt.setPadding(6, 2, 6, 2);
        addrRow.addView(hostEt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextColor(C_DIM);
        addrRow.addView(colon);

        EditText portEt = new EditText(this);
        portEt.setText(String.valueOf(adbPort));
        portEt.setTextColor(C_YELLOW);
        portEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        portEt.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        portEt.setPadding(6, 2, 6, 2);
        portEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        addrRow.addView(portEt, new LinearLayout.LayoutParams(80, ViewGroup.LayoutParams.WRAP_CONTENT));

        addrRow.addView(makeSmallBtn("连接", C_BLUE, v -> {
            final String host = hostEt.getText().toString().trim();
            int port = 5555;
            try { port = Integer.parseInt(portEt.getText().toString().trim()); } catch (Exception ignored) {}
            final int p = port;
            adbHost = host; adbPort = p;
            prefs.edit().putString("adb_host", host).putInt("adb_port", p).apply();
            Sh.submitAsync(() -> {
                boolean ok = Sh.connectAdb(host, p, 5000);
                h.post(() -> {
                    Logger.ok(ok ? "ADB 连接成功" : "ADB 连接失败");
                    if (ok) autoGrantPermissions();
                    switchTab(TAB_NAMES.length - 1);
                });
            });
        }));
        contentArea.addView(addrRow);

        // 测试 ADB 命令
        contentArea.addView(makeBtn("🧪 测试 ADB 连接 (执行 id 命令)", C_CYAN, v -> {
            Sh.submitAsync(() -> {
                Sh.Result r = Sh.run("id");
                h.post(() -> {
                    Logger.info("ADB 测试结果:\n" + r.toDiagnosticString());
                    showResultDialog("ADB 测试", r.toDiagnosticString());
                });
            });
        }));

        // ═══ Web 远程控制 ═══
        contentArea.addView(makeSectionTitle("🌐 Web 远程控制（手机扫码访问）"));

        boolean webRunning = webServer != null && webServer.isRunning();
        LinearLayout webStatusRow = new LinearLayout(this);
        webStatusRow.setOrientation(LinearLayout.HORIZONTAL);
        webStatusRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        webStatusRow.setPadding(8, 6, 8, 6);
        webStatusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView webStatusLabel = new TextView(this);
        webStatusLabel.setText("状态:");
        webStatusLabel.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        webStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        webStatusRow.addView(webStatusLabel);

        TextView webStatusValue = new TextView(this);
        if (webRunning) {
            String ip = WebServer.getDeviceIp();
            webStatusValue.setText("✅ 运行中  http://" + ip + ":8080");
            webStatusValue.setTextColor(C_GREEN);
        } else {
            webStatusValue.setText("❌ 未启动");
            webStatusValue.setTextColor(C_RED);
        }
        webStatusValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        webStatusValue.setTypeface(Typeface.DEFAULT_BOLD);
        webStatusRow.addView(webStatusValue);
        contentArea.addView(webStatusRow);

        TextView webHint = new TextView(this);
        webHint.setText("启动后，手机连接同一 WiFi，浏览器访问上述地址即可远程查看车况、执行车控、查看日志、运行诊断。");
        webHint.setTextColor(C_DIM);
        webHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        webHint.setPadding(8, 4, 8, 8);
        contentArea.addView(webHint);

        LinearLayout webBtnRow = new LinearLayout(this);
        webBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        webBtnRow.setPadding(0, 4, 0, 4);
        webBtnRow.addView(makeSmallBtn(webRunning ? "🛑 停止服务" : "🚀 启动服务", webRunning ? C_RED : C_GREEN, v -> {
            if (webServer != null && webServer.isRunning()) {
                webServer.stop();
                webServer = null;
                Logger.ok("Web 远程控制已停止");
            } else {
                webServer = new WebServer();
                webServer.setVehicleController(vehicleController);
                boolean ok = webServer.start(8080);
                if (ok) {
                    String ip = WebServer.getDeviceIp();
                    Logger.ok("Web 远程控制已启动: http://" + ip + ":8080");
                    Logger.info("手机连接同一 WiFi 后访问上述地址");
                } else {
                    Logger.error("Web 远程控制启动失败，端口 8080 可能被占用");
                    webServer = null;
                }
            }
            switchTab(TAB_NAMES.length - 1);
        }));
        webBtnRow.addView(makeSmallBtn("📋 复制地址", C_BLUE, v -> {
            String ip = WebServer.getDeviceIp();
            String url = "http://" + ip + ":8080";
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(url);
            Logger.ok("已复制: " + url);
        }));
        contentArea.addView(webBtnRow);

        // 主题
        contentArea.addView(makeSectionTitle("🎨 主题"));
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setPadding(0, 4, 0, 4);

        themeRow.addView(makeSmallBtn("🌙 深色", darkTheme ? C_GREEN : C_SURFACE, v -> {
            darkTheme = true;
            prefs.edit().putBoolean("dark_theme", true).apply();
            setContentView(buildUI());
        }));
        themeRow.addView(makeSmallBtn("☀️ 浅色", darkTheme ? C_SURFACE : C_GREEN, v -> {
            darkTheme = false;
            prefs.edit().putBoolean("dark_theme", false).apply();
            setContentView(buildUI());
        }));
        contentArea.addView(themeRow);

        // 参数快照
        contentArea.addView(makeSectionTitle("📸 参数快照"));
        contentArea.addView(makeBtn("📸 导出全部参数快照", C_PURPLE, v -> exportSnapshot()));
        contentArea.addView(makeBtn("📥 导入参数快照", C_CYAN, v -> importSnapshot()));

        // WiFi ADB 自动开启
        contentArea.addView(makeSectionTitle("📶 WiFi ADB 自动开启"));

        // 无障碍服务状态
        boolean accEnabled = BootReceiver.isAccessibilityServiceEnabled(this);
        LinearLayout accRow = new LinearLayout(this);
        accRow.setOrientation(LinearLayout.HORIZONTAL);
        accRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        accRow.setPadding(8, 6, 8, 6);
        accRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView accLabel = new TextView(this);
        accLabel.setText("无障碍服务:");
        accLabel.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        accLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        accRow.addView(accLabel);

        TextView accStatus = new TextView(this);
        accStatus.setText(accEnabled ? "✅ 已开启" : "❌ 未开启");
        accStatus.setTextColor(accEnabled ? C_GREEN : C_RED);
        accStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        accStatus.setTypeface(Typeface.DEFAULT_BOLD);
        accStatus.setPadding(8, 0, 8, 0);
        accRow.addView(accStatus);

        Button accSettingBtn = makeSmallBtn("去开启", C_BLUE, v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Logger.fail("打开无障碍设置失败: " + e.getMessage());
            }
        });
        accRow.addView(accSettingBtn);
        contentArea.addView(accRow);

        // 说明文字
        TextView accHint = new TextView(this);
        accHint.setText("需要先开启无障碍服务，才能自动点击系统 Demo 软件的 WiFi ADB 按钮。\n" +
                "原理：persist.sys.leap.wifiadb 属性需要 system 权限，普通应用无法直接设置，" +
                "只能通过启动系统 Demo 软件(com.leapmotor.system)并模拟点击按钮的方式开启。");
        accHint.setTextColor(C_DIM);
        accHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        accHint.setPadding(8, 4, 8, 8);
        contentArea.addView(accHint);

        // 开机自动开启开关
        boolean autoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START_ADB_WIFI, false);
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        autoRow.setPadding(8, 6, 8, 6);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView autoLabel = new TextView(this);
        autoLabel.setText("开机自动开启:");
        autoLabel.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        autoLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        autoRow.addView(autoLabel);

        Button autoToggleBtn = makeSmallBtn(autoStart ? "✅ 已开启" : "❌ 已关闭",
                autoStart ? C_GREEN : C_SURFACE, v -> {
            boolean current = prefs.getBoolean(BootReceiver.KEY_AUTO_START_ADB_WIFI, false);
            boolean newVal = !current;
            prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START_ADB_WIFI, newVal).apply();
            Logger.ok("开机自动开启 WiFi ADB: " + (newVal ? "已开启" : "已关闭"));
            switchTab(TAB_NAMES.length - 1); // 刷新页面
        });
        autoRow.addView(autoToggleBtn);
        contentArea.addView(autoRow);

        // 立即开启按钮
        contentArea.addView(makeBtn("🚀 立即开启 WiFi ADB", C_GREEN, v -> {
            if (!BootReceiver.isAccessibilityServiceEnabled(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("需要无障碍服务")
                        .setMessage("请先在系统设置中开启本应用的无障碍服务，才能自动点击 WiFi ADB 按钮。\n\n是否现在去开启？")
                        .setPositiveButton("去开启", (d, w) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } catch (Exception e) {
                                Logger.fail("打开无障碍设置失败: " + e.getMessage());
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            Logger.info("正在启动系统 Demo 软件以开启 WiFi ADB...");
            BootReceiver.startDemoActivity(this);
            Logger.ok("已启动 Demo 软件，无障碍服务将自动点击 WiFi ADB 按钮");
        }));

        // 查看当前 WiFi ADB 状态
        contentArea.addView(makeBtn("📊 查看 WiFi ADB 状态", C_BLUE, v -> {
            Sh.submitAsync(() -> {
                String result = Sh.out("getprop persist.sys.leap.wifiadb");
                String status = (result != null && result.trim().equals("1")) ? "✅ 已开启" : "❌ 未开启";
                h.post(() -> showResultDialog("WiFi ADB 状态",
                        "persist.sys.leap.wifiadb = " + (result == null ? "null" : result.trim()) + "\n\n" +
                        "状态: " + status + "\n\n" +
                        "如果已开启，可以通过以下命令连接:\n" +
                        "adb connect <车机IP>:5555"));
            });
        }));

        // 扩展工具
        contentArea.addView(makeSectionTitle("🧰 扩展工具"));
        contentArea.addView(makeBtn("🗺️ 高德地图定位修复", C_GREEN, v -> {
            startActivity(new android.content.Intent(this, AmapFixActivity.class));
        }));

        // 关于
        contentArea.addView(makeSectionTitle("ℹ️ 关于"));
        TextView about = new TextView(this);
        about.setText("C11 车控测试工具 v2.1\n" +
                "参数: " + VehicleParams.getCount() + " 个\n" +
                "Tab: " + TAB_NAMES.length + " 个\n" +
                "命令次数: " + cmdCount + "\n" +
                "ADB: " + (adbCustom ? adbHost + ":" + adbPort : "本地") + "\n" +
                "主题: " + (darkTheme ? "深色" : "浅色"));
        about.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        about.setPadding(8, 8, 8, 8);
        contentArea.addView(about);
    }

    // ═══════════════════════════════════════
    //  参数行
    // ═══════════════════════════════════════

    private View makeParamRow(String[] param) {
        String key = param[0], name = param[1], type = param[2], ns = param[3], hint = param[4], range = param[5];

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        row.setPadding(8, 4, 8, 4);

        // 第一行: 名称 + key + ns
        LinearLayout line1 = new LinearLayout(this);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        line1.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView keyTv = new TextView(this);
        keyTv.setText(key);
        keyTv.setTextColor(C_DIM);
        keyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        keyTv.setPadding(4, 0, 4, 0);
        line1.addView(keyTv);

        TextView nsTv = new TextView(this);
        nsTv.setText(ns);
        nsTv.setTextColor(C_PURPLE);
        nsTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        line1.addView(nsTv);

        row.addView(line1);

        // 第二行: 值 + 操作
        LinearLayout line2 = new LinearLayout(this);
        line2.setOrientation(LinearLayout.HORIZONTAL);
        line2.setGravity(Gravity.CENTER_VERTICAL);
        line2.setPadding(0, 2, 0, 0);

        TextView valTv = new TextView(this);
        valTv.setText("--");
        valTv.setTextColor(C_YELLOW);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);
        valTv.setPadding(4, 0, 8, 0);
        line2.addView(valTv);

        if (!range.isEmpty()) {
            TextView rangeTv = new TextView(this);
            rangeTv.setText(range);
            rangeTv.setTextColor(C_DIM);
            rangeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            rangeTv.setPadding(4, 0, 8, 0);
            line2.addView(rangeTv);
        }

        EditText et = new EditText(this);
        et.setHint(hint.isEmpty() ? "值" : hint);
        et.setHintTextColor(C_DIM);
        et.setTextColor(C_YELLOW);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        et.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        et.setPadding(6, 2, 6, 2);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        etLp.setMargins(4, 0, 4, 0);
        et.setLayoutParams(etLp);
        line2.addView(et);

        // 📖 读取
        line2.addView(makeSmallBtn("📖", C_BLUE, v -> {
            Sh.submitAsync(() -> {
                long t = System.currentTimeMillis();
                Sh.Result r = VehicleControl.getWithResult(key, ns);
                long ms = System.currentTimeMillis() - t;
                h.post(() -> {
                    if (r.ok() && !r.trim().isEmpty()) {
                        valTv.setText(r.trim());
                        valTv.setTextColor(C_YELLOW);
                    } else if (r.timeout) {
                        valTv.setText("超时");
                        valTv.setTextColor(C_ORANGE);
                    } else {
                        valTv.setText("读取失败");
                        valTv.setTextColor(C_RED);
                    }
                });
                Logger.info(name + " = " + (r.ok() ? r.trim() : "[失败 exit=" + r.exit + "]") + " (" + ms + "ms)");
                if (!r.ok()) {
                    Logger.warn("读取失败详情: " + r.toDiagnosticString());
                }
            });
        }));

        // ON / OFF
        if (type.equals("bool") || type.equals("int")) {
            line2.addView(makeSmallBtn("ON", C_GREEN, v -> {
                Sh.submitAsync(() -> {
                    long t = System.currentTimeMillis();
                    if (tryBroadcastControl(key, true)) {
                        h.post(() -> { valTv.setText("1"); valTv.setTextColor(C_GREEN); });
                    } else if ("prop".equals(ns)) {
                        Logger.warn("getprop 不支持写入: " + key);
                    } else {
                        VehicleControl.setSetting(key, "1", ns);
                        h.post(() -> { valTv.setText("1"); valTv.setTextColor(C_GREEN); });
                    }
                    Logger.ok(name + " → ON (" + (System.currentTimeMillis() - t) + "ms)");
                });
            }));
            line2.addView(makeSmallBtn("OFF", C_RED, v -> {
                Sh.submitAsync(() -> {
                    long t = System.currentTimeMillis();
                    if (tryBroadcastControl(key, false)) {
                        h.post(() -> { valTv.setText("0"); valTv.setTextColor(C_RED); });
                    } else if ("prop".equals(ns)) {
                        Logger.warn("getprop 不支持写入: " + key);
                    } else {
                        VehicleControl.setSetting(key, "0", ns);
                        h.post(() -> { valTv.setText("0"); valTv.setTextColor(C_RED); });
                    }
                    Logger.ok(name + " → OFF (" + (System.currentTimeMillis() - t) + "ms)");
                });
            }));
        }

        // SET
        line2.addView(makeSmallBtn("SET", C_ORANGE, v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) { Logger.warn("请输入值"); return; }
            Sh.submitAsync(() -> {
                long t = System.currentTimeMillis();
                if ("shell".equals(ns)) {
                    String out = Sh.out(val);
                    Logger.cmd(val, out);
                    h.post(() -> showResultDialog("Shell", out));
                } else if ("prop".equals(ns)) {
                    String out = Sh.out("getprop " + val);
                    Logger.info("getprop " + val + " = " + out);
                    h.post(() -> showResultDialog("getprop", out));
                } else {
                    VehicleControl.setSetting(key, val, ns);
                    h.post(() -> valTv.setText(val));
                    Logger.ok(name + " → " + val + " (" + (System.currentTimeMillis() - t) + "ms)");
                }
            });
        }));

        row.addView(line2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        row.setLayoutParams(lp);
        return row;
    }

    private boolean tryBroadcastControl(String key, boolean on) {
        switch (key) {
            case "leap.light.headlight": VehicleControl.setLowBeam(on); return true;
            case "leap.light.rear_fog": VehicleControl.setRearFog(on); return true;
            case "leap.light.position": VehicleControl.setPositionLight(on); return true;
            case "leap.system.pedestrians_alert": VehicleControl.setPedestriansAlert(on); return true;
            case "leap.hvac.ac_max": VehicleControl.setAcMax(on); return true;
            case "leap.scene.guard": VehicleControl.setGuardMode(on); return true;
            case "leap.scene.rest": VehicleControl.setRestMode(on); return true;
            case "leap.scene.camping": VehicleControl.setCampingMode(on); return true;
            case "leap.scene.power_save": VehicleControl.setPowerSaveMode(on); return true;
            case "leap.scene.sentinel": VehicleControl.setSentinelMode(on); return true;
            case "leap.scene.experience": VehicleControl.setExperienceMode(on); return true;
            case "leap.system.wifi": VehicleControl.setWifi(on); return true;
            case "leap.system.bluetooth": VehicleControl.setBluetooth(on); return true;
            case "leap.system.dark_mode": VehicleControl.setDayNightMode(!on); return true;
            case "leap.vehicle.child_lock": case "strCarChildLock": VehicleControl.setChildLock(on); return true;
            default: return false;
        }
    }

    // ═══════════════════════════════════════
    //  ADB 自动授权
    // ═══════════════════════════════════════

    /**
     * ADB 连接成功后，自动给 APP 授予权限
     * 通过 adb shell pm grant 命令
     */
    private void autoGrantPermissions() {
        Sh.submitAsync(() -> {
            Logger.info("正在自动授权...");
            String packageName = getPackageName();

            String[] permissions = {
                "android.permission.READ_LOGS",
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.MANAGE_USERS",
                "android.permission.DUMP",
                "android.permission.PACKAGE_USAGE_STATS"
            };

            for (String perm : permissions) {
                Sh.Result r = Sh.run("pm grant " + packageName + " " + perm);
                if (r.ok()) {
                    Logger.ok("已授权: " + perm);
                } else {
                    Logger.warn("授权失败: " + perm + " - " + (r.err != null ? r.err.trim() : ""));
                }
            }

            // 验证权限
            Logger.info("权限验证:");
            Sh.Result check = Sh.run("dumpsys package " + packageName + " | grep -A5 'grantedPermissions'");
            if (check.out != null && !check.out.isEmpty()) {
                Logger.info(check.out);
            }

            Logger.ok("自动授权完成");
        });
    }

    // ═══════════════════════════════════════
    //  导出/导入
    // ═══════════════════════════════════════

    private void exportLogs() {
        Sh.submitAsync(() -> {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sb.append("# C11 车控测试 日志导出\n");
            sb.append("# 时间: ").append(sdf.format(new Date())).append("\n");
            sb.append("# ADB: ").append(Sh.isAdbConnected() ? "ADB shell (uid=2000)" : "本地 (应用uid)").append("\n\n");
            sb.append(logView.getText().toString());

            String filename = "c11_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            // 优先用 Java FileWriter 写 APP 私有目录，避免存储权限问题
            try {
                java.io.File dir = new java.io.File(getExternalFilesDir(null), "logs");
                if (!dir.exists()) dir.mkdirs();
                java.io.File f = new java.io.File(dir, filename);
                java.io.FileWriter fw = new java.io.FileWriter(f);
                fw.write(sb.toString());
                fw.close();
                Logger.ok("日志已导出: " + f.getAbsolutePath());
            } catch (Exception e) {
                // fallback: 用 shell 写
                boolean ok = Sh.writeFile("/sdcard/" + filename, sb.toString());
                Logger.ok(ok ? "日志已导出: /sdcard/" + filename : "导出失败: " + e.getMessage());
            }
        });
    }

    private void exportLogcat() {
        Sh.submitAsync(() -> {
            String logcat = VehicleControl.getLogcat(200);
            String filename = "c11_logcat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            boolean ok = Sh.writeFile("/sdcard/" + filename, logcat);
            Logger.ok(ok ? "logcat 已导出: /sdcard/" + filename : "导出失败");
        });
    }

    private void exportSnapshot() {
        Sh.submitAsync(() -> {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sb.append("# C11 参数快照\n");
            sb.append("# 时间: ").append(sdf.format(new Date())).append("\n");
            sb.append("# 参数数: ").append(VehicleParams.getCount()).append("\n\n");

            int ok = 0;
            for (String[] p : VehicleParams.PARAMS) {
                String val = VehicleControl.get(p[0], p[3]);
                sb.append(p[0]).append("=").append(val != null ? val : "").append("\n");
                if (val != null && !val.isEmpty() && !val.equals("null")) ok++;
            }

            String filename = "c11_snapshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            boolean written = Sh.writeFile("/sdcard/" + filename, sb.toString());
            Logger.ok(written ? "快照已导出: /sdcard/" + filename + " (" + ok + " 有值)" : "导出失败");
        });
    }

    private void importSnapshot() {
        Sh.submitAsync(() -> {
            String content = Sh.readFile("/sdcard/c11_snapshot_latest.txt");
            if (content.isEmpty()) {
                Logger.warn("未找到快照文件: /sdcard/c11_snapshot_latest.txt");
                return;
            }
            int imported = 0;
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.startsWith("#") || !line.contains("=")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    // 找到对应的 ns
                    for (String[] p : VehicleParams.PARAMS) {
                        if (p[0].equals(key) && !"prop".equals(p[3])) {
                            VehicleControl.setSetting(key, val, p[3]);
                            imported++;
                            break;
                        }
                    }
                }
            }
            Logger.ok("已导入 " + imported + " 个参数");
        });
    }

    // ═══════════════════════════════════════
    //  搜索
    // ═══════════════════════════════════════

    private void doSearch() {
        String query = searchBox.getText().toString().trim();
        if (query.isEmpty()) { switchTab(0); return; }
        contentArea.removeAllViews();
        List<String[]> results = VehicleParams.search(query);

        TextView countTv = new TextView(this);
        countTv.setText("搜索 \"" + query + "\" → " + results.size() + " 个结果");
        countTv.setTextColor(C_CYAN);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        countTv.setPadding(4, 4, 4, 8);
        contentArea.addView(countTv);

        for (String[] p : results) contentArea.addView(makeParamRow(p));
    }

    // ═══════════════════════════════════════
    //  UI 组件
    // ═══════════════════════════════════════

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C_CYAN);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 12, 0, 6);
        return tv;
    }

    private Button makeBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(12, 8, 12, 8);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 3, 0, 3);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button makeSmallBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        btn.setPadding(10, 2, 10, 2);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private View makeEditRow(String label, String key, String ns, String hint, String range) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        row.setPadding(8, 6, 8, 6);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.35f));

        EditText et = new EditText(this);
        et.setText(hint);
        et.setTextColor(C_YELLOW);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        et.setBackgroundColor(darkTheme ? C_SURFACE : C_LIGHT_SURFACE);
        et.setPadding(6, 2, 6, 2);
        row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        TextView rangeTv = new TextView(this);
        rangeTv.setText(range);
        rangeTv.setTextColor(C_DIM);
        rangeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        row.addView(rangeTv);

        row.addView(makeSmallBtn("SET", C_BLUE, v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) return;
            Sh.submitAsync(() -> {
                if ("shell".equals(ns)) { String out = Sh.out(val); Logger.cmd(val, out); h.post(() -> showResultDialog("Shell", out)); }
                else if ("prop".equals(ns)) { String out = Sh.out("getprop " + val); Logger.info("getprop " + val + " = " + out); h.post(() -> showResultDialog("getprop", out)); }
                else { String out = Sh.out("settings get global " + val); Logger.info("settings " + val + " = " + out); h.post(() -> showResultDialog("settings", out)); }
            });
        }));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        row.setLayoutParams(lp);
        return row;
    }

    // ═══════════════════════════════════════
    //  日志
    // ═══════════════════════════════════════

    private void appendLog(String msg, Logger.Level level) {
        if (logView == null) return;
        logView.append(msg + "\n");
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void togglePolling() {
        polling = !polling;
        if (polling) {
            Logger.info("开始轮询 logcat (每3秒)");
            Sh.submitAsync(() -> {
                while (polling) {
                    String log = VehicleControl.pollLogcat();
                    if (!log.isEmpty()) {
                        h.post(() -> {
                            vehLogView.append(log + "\n");
                            vehLogScroll.post(() -> vehLogScroll.fullScroll(View.FOCUS_DOWN));
                        });
                    }
                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                }
            });
        } else {
            Logger.info("停止轮询");
        }
    }

    private void showResultDialog(String title, String content) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content.isEmpty() ? "(空)" : content)
                .setPositiveButton("📋 复制", (d, w) -> { copyToClipboard(content); d.dismiss(); })
                .setNegativeButton("关闭", (d, w) -> d.dismiss())
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("cmd", text)); Logger.ok("已复制"); }
    }
    // ═══════════════════════════════════════
    //  🏠 首页（重点入口：ADB状态 / Web服务 / 一键诊断 / 车控实验）
    // ═══════════════════════════════════════

    /** Web 服务自启（幂等） */
    private String lastQrUrl = null;

    /** 当前 Web 访问地址（端口随实际监听端口自适应，IP 纯 Java 枚举不阻塞） */
    private String webUrl() {
        int port = webServer != null ? webServer.getPort() : 8080;
        return "http://" + WebServer.getDeviceIp() + ":" + port;
    }

    private void startWebAuto() {
        if (webServer != null && webServer.isRunning()) return;
        webServer = new WebServer();
        webServer.setVehicleController(vehicleController);
        boolean ok = webServer.start(8080);
        if (ok) {
            Logger.ok("Web 服务已自启: " + webUrl());
            lastQrUrl = null; // 强制刷新二维码
            Sh.submitAsync(() -> {
                boolean st = webServer.selfTest();
                h.post(() -> Logger.info("Web 本机自检(127.0.0.1): " + (st ? "✅ 通过" : "❌ 失败")));
            });
        } else {
            Logger.error("Web 服务自启失败（8080~8100 均不可用？），请手动启动");
            webServer = null;
        }
    }

    /** 启动首页状态周期刷新（每 3 秒，仅更新文本视图，不重建页面） */
    private void startHomeStatusRefresh() {
        h.post(new Runnable() {
            @Override public void run() {
                refreshHomeStatus();
                h.postDelayed(this, 3000);
            }
        });
    }

    /** 刷新首页 ADB/Web/二维码状态 */
    private void refreshHomeStatus() {
        if (homeAdbStatusView != null) {
            boolean conn = Sh.isAdbConnected();
            int uid = Sh.getAdbUid();
            String txt = conn
                    ? (uid == 2000 ? "✅ SHELL 权限 (uid=2000) — 车控已解锁"
                                   : "✅ 已连接 (uid=" + uid + ")")
                    : "❌ 未连接 — 请连接本地 ADB";
            homeAdbStatusView.setText(txt);
            homeAdbStatusView.setTextColor(conn ? C_GREEN : C_RED);
        }
        if (homeWebStatusView != null) {
            boolean run = webServer != null && webServer.isRunning();
            String txt = run ? ("✅ 运行中  " + webUrl()) : "❌ 未启动 — 点击下方启动";
            homeWebStatusView.setText(txt);
            homeWebStatusView.setTextColor(run ? C_GREEN : C_RED);
        }
        if (homeQrView != null) {
            boolean run = webServer != null && webServer.isRunning();
            if (run) {
                String q = webUrl();
                if (!q.equals(lastQrUrl)) {
                    homeQrView.setImageBitmap(renderQr(q));
                    lastQrUrl = q;
                }
            } else if (lastQrUrl != null) {
                homeQrView.setImageBitmap(null);
                lastQrUrl = null;
            }
        }
    }

    /** 构建首页（大按钮卡片，重排操作逻辑与功能入口） */
    private void buildHomeTab() {
        // 顶部：版本与功能清单
        TextView verTv = new TextView(this);
        verTv.setText(AppInfo.TITLE + "  (code " + AppInfo.VERSION_CODE + ")");
        verTv.setTextColor(C_BLUE);
        verTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        verTv.setTypeface(Typeface.DEFAULT_BOLD);
        verTv.setPadding(4, 4, 4, 6);
        contentArea.addView(verTv);

        TextView featTv = new TextView(this);
        featTv.setText(AppInfo.FEATURE_LIST);
        featTv.setTextColor(C_DIM);
        featTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        featTv.setPadding(4, 0, 4, 8);
        contentArea.addView(featTv);

        // ── 卡片 1：ADB 状态 ──
        contentArea.addView(makeSectionTitle("📡 ADB 连接"));
        homeAdbStatusView = new TextView(this);
        homeAdbStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        homeAdbStatusView.setTypeface(Typeface.DEFAULT_BOLD);
        homeAdbStatusView.setGravity(Gravity.CENTER);
        homeAdbStatusView.setPadding(0, 8, 0, 8);
        contentArea.addView(homeAdbStatusView);

        LinearLayout adbRow = new LinearLayout(this);
        adbRow.setOrientation(LinearLayout.HORIZONTAL);
        adbRow.addView(makeBtnL("🔌 连接", C_GREEN, v -> {
            Sh.submitAsync(() -> {
                boolean ok = Sh.connectLocalAdb();
                h.post(() -> Logger.ok(ok ? "ADB 连接成功（心跳保活已接管）" : "ADB 连接失败，请确认 WiFi ADB 已开启"));
            });
        }));
        adbRow.addView(makeBtnL("🔌 断开", C_RED, v -> Sh.disconnectAdb()));
        contentArea.addView(adbRow);

        // ── 卡片 2：Web 远程 ──
        contentArea.addView(makeSectionTitle("🌐 Web 远程（手机扫码）"));
        homeWebStatusView = new TextView(this);
        homeWebStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        homeWebStatusView.setTypeface(Typeface.DEFAULT_BOLD);
        homeWebStatusView.setGravity(Gravity.CENTER);
        homeWebStatusView.setPadding(0, 4, 0, 4);
        contentArea.addView(homeWebStatusView);

        // 网卡地址列表（车机以太网 eth0 / WiFi wlan0 全列出）
        StringBuilder ipTxt = new StringBuilder("可用地址:\n");
        for (String s : WebServer.getAllIps()) ipTxt.append("  ").append(s).append("\n");
        ipTxt.append("提示: 手机与车机连同一 WiFi/热点、IP 前三位一致即可；车机联网 IP 见上，车内 192.168.1.x 不可用。");
        TextView ipTv = new TextView(this);
        ipTv.setText(ipTxt.toString().trim());
        ipTv.setTextColor(C_DIM);
        ipTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        ipTv.setPadding(4, 0, 4, 4);
        contentArea.addView(ipTv);

        // 二维码（每 3 秒随状态刷新）
        homeQrView = new android.widget.ImageView(this);
        homeQrView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        homeQrView.setPadding(4, 4, 4, 4);
        homeQrView.setBackgroundColor(0xFFFFFFFF);
        contentArea.addView(homeQrView, new LinearLayout.LayoutParams(220, 220));

        LinearLayout webRow = new LinearLayout(this);
        webRow.setOrientation(LinearLayout.HORIZONTAL);
        webRow.addView(makeBtnL("🚀 启动", C_GREEN, v -> startWebAuto()));
        webRow.addView(makeBtnL("🛑 停止", C_RED, v -> {
            if (webServer != null) { webServer.stop(); webServer = null; Logger.ok("Web 服务已停止"); }
        }));
        webRow.addView(makeBtnL("📋 复制地址", C_BLUE, v -> {
            String url = webUrl();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", url));
            Logger.ok("已复制: " + url);
        }));
        contentArea.addView(webRow);

        // ── 卡片 3：一键诊断 ──
        contentArea.addView(makeSectionTitle("🔧 一键诊断"));
        contentArea.addView(makeBtn("🧪 运行完整诊断（15项检查 + 报告导出）", C_ORANGE, v -> {
            Sh.submitAsync(() -> {
                h.post(() -> Logger.title("=== 一键诊断开始 ==="));
                DiagnosticMode.runFullDiagnostic(new DiagnosticMode.ProgressCallback() {
                    @Override public void onProgress(String message) { h.post(() -> Logger.info(message)); }
                    @Override public void onComplete(String fullReport) {
                        h.post(() -> {
                            Logger.ok("=== 诊断完成 ===");
                            showResultDialog("诊断报告", fullReport);
                            switchTab(TAB_NAMES.length - 2);
                        });
                    }
                });
            });
        }));

        // ── 卡片 4：车控实验 ──
        contentArea.addView(makeSectionTitle("🎮 车控实验 / 全测"));
        contentArea.addView(makeBtn("🧰 一键全测（连接+通道+200项数据+车控矩阵+logcat，自动导出）", C_GREEN, v -> runWorkbench()));
        contentArea.addView(makeBtn("🚀 启动顺序实验（逐项确认 + 自动判定）", 0xFF9C27B0, v -> runCarExperiment()));

        // 首次刷新状态
        refreshHomeStatus();
    }

    /** 车机大按钮（等高，触控友好） */
    private Button makeBtnL(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(color);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(2, 2, 2, 2);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 生成二维码位图（内嵌 qrcodegen，纯 Java 无依赖） */
    private android.graphics.Bitmap renderQr(String text) {
        try {
            com.c11.cartool.util.QrCode qr = com.c11.cartool.util.QrCode.encodeText(text, com.c11.cartool.util.QrCode.Ecc.MEDIUM);
            int n = qr.size;
            int scale = 8;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(n * scale, n * scale, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
            cv.drawColor(0xFFFFFFFF);
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColor(0xFF000000);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (qr.getModule(x, y)) {
                        cv.drawRect(x * scale, y * scale, (x + 1) * scale, (y + 1) * scale, p);
                    }
                }
            }
            return bmp;
        } catch (Exception e) {
            Logger.warn("二维码生成失败: " + e.getMessage());
            return null;
        }
    }

    /** 一键全测：连接 + 通道 + 数据快照 + 车控矩阵 + logcat，自动导出报告 */
    private void runWorkbench() {
        new AlertDialog.Builder(this)
                .setTitle("🧰 一键全测（约 2-3 分钟）")
                .setMessage("将自动顺序完成：连接 ADB → 通道能力探测 → 读取 200+ 车辆数据 → 车控方案矩阵（空调/灯光/儿童锁/车锁等，成对、间隔 3 秒，车锁最终保持解锁）→ 抓取 logcat，并导出报告到 /sdcard。\n\n请确保：车辆 P 挡驻车、周围无人、车窗与后备箱无障碍。\n\n车窗升降、后备箱因安全风险不自动测试。")
                .setPositiveButton("开始全测", (d, w) -> {
                    Logger.title("=== 一键全测开始，请勿操作车机 ===");
                    switchTab(TAB_NAMES.length - 2);
                    WorkbenchTest.start(vehicleController, new WorkbenchTest.Callback() {
                        @Override public void onProgress(String m) { h.post(() -> Logger.info(m)); }
                        @Override public void onFinished(String report, String path) {
                            h.post(() -> {
                                Logger.ok("=== 一键全测完成 ===" + (path != null ? " 报告: " + path : ""));
                                showResultDialog("一键全测报告" + (path != null ? "\n已保存: " + path : ""), report);
                            });
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 车控顺序实验（多方案逐一确认，自动判定，间隔 4 秒） */
    private void runCarExperiment() {
        if (!Sh.isAdbConnected()) {
            showResultDialog("车控实验", "需要先连接 ADB（shell 权限）。\n\n请先在首页或设置页连接后重试。");
            return;
        }
        java.util.List<CarControlExperiment.Step> steps = CarControlExperiment.buildDefaultSteps(vehicleController);
        CarControlExperiment.runAsync(steps, new CarControlExperiment.Callback() {
            @Override public void onConfirmRequest(CarControlExperiment.Step step, CarControlExperiment.ConfirmListener listener) {
                h.post(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("执行: " + step.title)
                        .setMessage(step.confirmText + "\n\n通道: " + step.channel + "\n\n请观察车辆是否响应后选择：")
                        .setPositiveButton("✅ 执行", (d, w) -> { d.dismiss(); listener.onConfirmed(); })
                        .setNegativeButton("⏭️ 跳过", (d, w) -> { d.dismiss(); listener.onSkipped(); })
                        .setCancelable(false)
                        .show());
            }
            @Override public void onProgress(String message) { h.post(() -> Logger.info(message)); }
            @Override public void onStepResult(CarControlExperiment.Step step, CarControlExperiment.StepResult result, String detail) {
                String icon = result == CarControlExperiment.StepResult.SUCCESS ? "✅"
                        : result == CarControlExperiment.StepResult.FAILED ? "❌"
                        : result == CarControlExperiment.StepResult.SKIPPED ? "⏭️"
                        : result == CarControlExperiment.StepResult.BLOCKED ? "⛔" : "👁️";
                h.post(() -> Logger.info(icon + " " + step.title + " — " + detail));
            }
            @Override public void onFinished(String summary) { h.post(() -> showResultDialog("车控顺序实验结果", summary)); }
        });
    }

    // ═══════════════════════════════════════
    //  🎮 车控实验页面（所有可行通道集中验证）
    // ═══════════════════════════════════════
    private void buildCarControlTab() {
        contentArea.addView(buildGlobalQuickActions());

        // 顺序实验（推荐）：多方案逐一确认执行，自动判定结果
        contentArea.addView(makeSectionTitle("🧰 一键全测（推荐·一次拿全部结论）"));
        contentArea.addView(makeBtn("🧰 一键全测（连接+通道+200项数据+车控矩阵+logcat，自动导出）", C_GREEN, v -> runWorkbench()));
        contentArea.addView(makeSectionTitle("🧪 顺序实验（逐项确认）"));
        contentArea.addView(makeBtn("🚀 启动顺序实验（每步确认 + 自动判定）", 0xFF9C27B0, v -> runCarExperiment()));
        contentArea.addView(makeBtn("🔌 未连接 ADB？点此连接", C_ORANGE, v -> {
            Sh.submitAsync(() -> {
                boolean ok = Sh.connectLocalAdb();
                h.post(() -> Logger.ok(ok ? "ADB 已连接" : "ADB 连接失败，请确认 WiFi ADB 已开启"));
            });
        }));

        // 状态提示
        TextView statusTv = new TextView(this);
        statusTv.setText("ADB: " + (Sh.isAdbConnected() ? "✅ 已连接 (shell权限)" : "❌ 未连接 (普通应用权限，车控可能被拒)"));
        statusTv.setTextColor(Sh.isAdbConnected() ? 0xFF4CAF50 : 0xFFFF9800);
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusTv.setPadding(8, 8, 8, 8);
        contentArea.addView(statusTv);

        // 说明
        TextView tipTv = new TextView(this);
        tipTv.setText("通道: 车锁→Rightware直接Intent | 其他→讯飞handMessage广播\n点击按钮后查看日志页确认执行结果");
        tipTv.setTextColor(C_DIM);
        tipTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tipTv.setPadding(8, 0, 8, 8);
        contentArea.addView(tipTv);

        // 1. 车锁
        contentArea.addView(makeSectionTitle("🔑 车锁 (Rightware通道)"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"🔒 锁车", "lockCar"},
            {"🔓 解锁", "unlockCar"}
        }));

        // 2. 后备箱
        contentArea.addView(makeSectionTitle("🚪 后备箱"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"📂 开后备箱", "openTrunk"},
            {"📦 关后备箱", "closeTrunk"}
        }));

        // 3. 车窗
        contentArea.addView(makeSectionTitle("🪟 车窗"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"⬆️ 主驾全开", "window_fl_100"},
            {"⬇️ 主驾全关", "window_fl_0"},
            {"⬆️ 副驾全开", "window_fr_100"},
            {"⬇️ 副驾全关", "window_fr_0"}
        }));
        contentArea.addView(makeBtnRow(new String[][]{
            {"⬆️ 左后全开", "window_rl_100"},
            {"⬇️ 左后全关", "window_rl_0"},
            {"⬆️ 右后全开", "window_rr_100"},
            {"⬇️ 右后全关", "window_rr_0"}
        }));

        // 4. 儿童锁
        contentArea.addView(makeSectionTitle("👶 儿童锁"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"👈 左儿童锁", "leftChildLockOn"},
            {"👉 右儿童锁", "rightChildLockOn"}
        }));

        // 5. 灯光（旧广播通道，已验证）
        contentArea.addView(makeSectionTitle("💡 灯光 (tocarcontrol旧广播)"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"💡 近光开", "lowBeamOn"},
            {"🌑 近光关", "lowBeamOff"},
            {"🌫️ 雾灯开", "fogLightOn"},
            {"🌤️ 雾灯关", "fogLightOff"}
        }));
        contentArea.addView(makeBtnRow(new String[][]{
            {"🔦 示廓灯开", "positionLightOn"},
            {"🔦 示廓灯关", "positionLightOff"}
        }));

        // 6. 除霜
        contentArea.addView(makeSectionTitle("❄️ 除霜"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"🪟 前除霜", "frontDefrostOn"},
            {"🔙 后除霜", "rearDefrostOn"}
        }));

        // 7. 空调
        contentArea.addView(makeSectionTitle("🌡️ 空调 (最大制冷=旧广播已验证, 其他=handMessage)"));
        contentArea.addView(makeBtnRow(new String[][]{
            {"❄️ 空调开", "acOn"},
            {"🚫 空调关", "acOff"},
            {"🧊 最大制冷开", "acMaxOn"},
            {"🧊 最大制冷关", "acMaxOff"}
        }));
        contentArea.addView(makeBtnRow(new String[][]{
            {"🌡️ 温度+", "acTempUp"},
            {"🌡️ 温度-", "acTempDown"},
            {"💨 风量+", "acFanUp"},
            {"💨 风量-", "acFanDown"}
        }));
        contentArea.addView(makeBtnRow(new String[][]{
            {"🅿️ 360全景", "open360View"}
        }));

        // 一键全测
        contentArea.addView(makeSectionTitle("🧪 一键全测"));
        contentArea.addView(makeBtn("⚡ 快速全测（不确认，间隔1秒，仅供熟悉通道）", 0xFF6D4C41, v -> {
            Sh.submitAsync(() -> runAllCarControls()).start();
        }));
    }

    private int acTemp = 22;
    private int acFan = 3;

    private void runCarAction(String action) {
        Sh.submitAsync(() -> {
            try {
                boolean ok = false;
                switch (action) {
                    case "lockCar": ok = vehicleController.lockCar(); break;
                    case "unlockCar": ok = vehicleController.unlockCar(); break;
                    case "openTrunk": ok = vehicleController.openTrunk(); break;
                    case "closeTrunk": ok = vehicleController.closeTrunk(); break;
                    case "window_fl_100": ok = vehicleController.setWindow("front_left", 100); break;
                    case "window_fl_0": ok = vehicleController.setWindow("front_left", 0); break;
                    case "window_fr_100": ok = vehicleController.setWindow("front_right", 100); break;
                    case "window_fr_0": ok = vehicleController.setWindow("front_right", 0); break;
                    case "window_rl_100": ok = vehicleController.setWindow("rear_left", 100); break;
                    case "window_rl_0": ok = vehicleController.setWindow("rear_left", 0); break;
                    case "window_rr_100": ok = vehicleController.setWindow("rear_right", 100); break;
                    case "window_rr_0": ok = vehicleController.setWindow("rear_right", 0); break;
                    case "leftChildLockOn": ok = vehicleController.leftChildLockOn(); break;
                    case "rightChildLockOn": ok = vehicleController.rightChildLockOn(); break;
                    case "lowBeamOn": ok = vehicleController.lowBeamOn(); break;
                    case "lowBeamOff": ok = vehicleController.lowBeamOff(); break;
                    case "fogLightOn": ok = vehicleController.fogLightOn(); break;
                    case "fogLightOff": ok = vehicleController.fogLightOff(); break;
                    case "positionLightOn": ok = vehicleController.positionLightOn(); break;
                    case "positionLightOff": ok = vehicleController.positionLightOff(); break;
                    case "acMaxOn": ok = vehicleController.acMaxOn(); break;
                    case "acMaxOff": ok = vehicleController.acMaxOff(); break;
                    case "frontDefrostOn": ok = vehicleController.frontDefrostOn(); break;
                    case "rearDefrostOn": ok = vehicleController.rearDefrostOn(); break;
                    case "acOn": ok = vehicleController.acOn(); break;
                    case "acOff": ok = vehicleController.acOff(); break;
                    case "acTempUp": acTemp = Math.min(32, acTemp + 1); ok = vehicleController.setAcTemperature(acTemp); break;
                    case "acTempDown": acTemp = Math.max(18, acTemp - 1); ok = vehicleController.setAcTemperature(acTemp); break;
                    case "acFanUp": acFan = Math.min(8, acFan + 1); ok = vehicleController.setAcFanSpeed(acFan); break;
                    case "acFanDown": acFan = Math.max(0, acFan - 1); ok = vehicleController.setAcFanSpeed(acFan); break;
                    case "open360View": ok = vehicleController.open360View(); break;
                }
                Logger.ok("CarCtrl", action + " → " + (ok ? "✅ 发送成功" : "❌ 发送失败"));
            } catch (Exception e) {
                Logger.error("CarCtrl", action + " 异常: " + e.getMessage());
            }
        });
    }

    private void runAllCarControls() {
        String[] actions = {
            "lockCar", "unlockCar",
            "openTrunk", "closeTrunk",
            "window_fl_100", "window_fl_0",
            "window_fr_100", "window_fr_0",
            "leftChildLockOn", "rightChildLockOn",
            "lowBeamOn", "lowBeamOff",
            "fogLightOn", "fogLightOff",
            "positionLightOn", "positionLightOff",
            "frontDefrostOn", "rearDefrostOn",
            "acMaxOn", "acMaxOff",
            "acOn", "acOff",
            "acTempUp", "acTempDown",
            "acFanUp", "acFanDown",
            "open360View"
        };
        Logger.info("CarCtrl", "=== 一键全测开始，共 " + actions.length + " 项 ===");
        for (String act : actions) {
            runCarAction(act);
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        }
        Logger.info("CarCtrl", "=== 一键全测完成 ===");
    }

    private android.widget.LinearLayout makeBtnRow(String[][] btns) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(4, 2, 4, 2);
        for (String[] b : btns) {
            android.widget.Button btn = new android.widget.Button(this);
            btn.setText(b[0]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btn.setBackgroundColor(0xFF37474F);
            btn.setTextColor(0xFFFFFFFF);
            btn.setAllCaps(false);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(lp);
            final String act = b[1];
            btn.setOnClickListener(v -> runCarAction(act));
            row.addView(btn);
        }
        return row;
    }

}