package com.c11.cartool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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

    // ADB 连接配置
    private String adbHost = "127.0.0.1";
    private int adbPort = 5555;
    private boolean adbCustom = false;
    private boolean adbConnected = false;

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

    // Tab 定义
    static final String[] TAB_NAMES = {
            "📊 全部", "❄️ 空调", "💡 灯光", "🪑 座椅", "🚗 车身",
            "🪟 车窗", "🏎️ 驾驶", "🎯 场景", "🔊 音量", "⚡ 充电",
            "🔋 电池", "🛡️ ADAS", "🔧 行车", "📡 系统", "🔒 安全",
            "🎵 媒体", "🗺️ 导航", "📱 蓝牙", "🌧️ 雨刷", "🌡️ 环境",
            "📏 里程", "🏷️ 车辆", "📋 日志", "⚙️ 设置"
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
            "车辆|car|vin|model|year|color|config|gear|speed|rpm", null, null
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());

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

        // 自动连接 ADB
        new Thread(() -> {
            connectAdb();
            DeviceInfo info = DeviceInfo.detect();
            h.post(() -> statusLine.setText(String.format("UID:%d %s %s",
                    info.uid, info.model, adbConnected ? "ADB✅" : "ADB❌")));
            Logger.title("C11 车控测试工具 v2.1");
            Logger.info("设备: " + info.model + " | Android " + info.android);
            Logger.info("ADB: " + (adbCustom ? adbHost + ":" + adbPort : "本地"));
            Logger.info("参数: " + VehicleParams.getCount() + " 个");
        }).start();
    }

    // ═══════════════════════════════════════
    //  ADB 连接管理
    // ═══════════════════════════════════════

    private void connectAdb() {
        if (adbCustom) {
            String result = Sh.out("connect " + adbHost + ":" + adbPort);
            adbConnected = result.contains("connected");
            Logger.info("ADB 连接 " + adbHost + ":" + adbPort + " → " + result);
        } else {
            // 本地模式，直接检查
            String id = Sh.out("id");
            adbConnected = id.contains("uid=");
        }
    }

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
        title.setText("🚗 C11 车控 v2.1");
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

        if (idx == TAB_NAMES.length - 2) { buildLogTab(); return; }
        if (idx == TAB_NAMES.length - 1) { buildSettingsTab(); return; }

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

        if (filter != null) contentArea.addView(buildQuickActions(idx));

        contentArea.addView(makeBtn("📖 批量读取本页全部", C_BLUE, v -> {
            new Thread(() -> {
                long start = System.currentTimeMillis();
                Logger.info("批量读取 " + params.size() + " 个参数...");
                for (String[] p : params) {
                    String val = VehicleControl.get(p[0], p[3]);
                    Logger.info(p[1] + " (" + p[0] + ") = " + val);
                }
                long elapsed = System.currentTimeMillis() - start;
                Logger.ok("批量读取完成, 耗时 " + elapsed + "ms");
            }).start();
        }));

        for (String[] p : params) contentArea.addView(makeParamRow(p));
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
        contentArea.addView(makeSectionTitle("📋 日志工具"));

        contentArea.addView(makeBtn("📖 读取 logcat (最近50行)", C_BLUE, v -> {
            new Thread(() -> {
                String log = VehicleControl.getLogcat(50);
                Logger.info("logcat:\n" + log);
            }).start();
        }));

        contentArea.addView(makeBtn("🗑 清除 logcat", C_RED, v -> { VehicleControl.clearLogcat(); Logger.ok("logcat 已清除"); }));
        contentArea.addView(makeBtn("📖 读取全部 leap.* 属性", C_GREEN, v -> {
            new Thread(() -> { String all = VehicleControl.getAllLeapProps(); Logger.info("leap.*:\n" + all); showResultDialog("leap.*", all); }).start();
        }));
        contentArea.addView(makeBtn("📖 读取全部 strCar* 设置", C_GREEN, v -> {
            new Thread(() -> { String all = VehicleControl.getAllCarSettings(); Logger.info("strCar*:\n" + all); showResultDialog("strCar*", all); }).start();
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

        // ADB 连接
        contentArea.addView(makeSectionTitle("📡 ADB 连接"));

        LinearLayout adbRow = new LinearLayout(this);
        adbRow.setOrientation(LinearLayout.HORIZONTAL);
        adbRow.setBackgroundColor(darkTheme ? C_CARD : C_LIGHT_CARD);
        adbRow.setPadding(8, 6, 8, 6);
        adbRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView adbLabel = new TextView(this);
        adbLabel.setText("连接模式:");
        adbLabel.setTextColor(darkTheme ? C_TEXT : C_LIGHT_TEXT);
        adbLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        adbRow.addView(adbLabel);

        Button localBtn = makeSmallBtn("本地", adbCustom ? C_SURFACE : C_GREEN, v -> {
            adbCustom = false;
            prefs.edit().putBoolean("adb_custom", false).apply();
            new Thread(() -> { connectAdb(); h.post(() -> switchTab(TAB_NAMES.length - 1)); }).start();
        });
        adbRow.addView(localBtn);

        Button customBtn = makeSmallBtn("自定义", adbCustom ? C_GREEN : C_SURFACE, v -> {
            adbCustom = true;
            prefs.edit().putBoolean("adb_custom", true).apply();
            switchTab(TAB_NAMES.length - 1);
        });
        adbRow.addView(customBtn);

        contentArea.addView(adbRow);

        if (adbCustom) {
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

            Button connectBtn = makeSmallBtn("连接", C_BLUE, v -> {
                adbHost = hostEt.getText().toString().trim();
                try { adbPort = Integer.parseInt(portEt.getText().toString().trim()); } catch (Exception e) {}
                prefs.edit().putString("adb_host", adbHost).putInt("adb_port", adbPort).apply();
                new Thread(() -> { connectAdb(); h.post(() -> {
                    Logger.ok("ADB 连接 " + (adbConnected ? "成功" : "失败"));
                    switchTab(TAB_NAMES.length - 1);
                }); }).start();
            });
            addrRow.addView(connectBtn);

            contentArea.addView(addrRow);
        }

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
            new Thread(() -> {
                long t = System.currentTimeMillis();
                String val = VehicleControl.get(key, ns);
                long ms = System.currentTimeMillis() - t;
                h.post(() -> valTv.setText(val));
                Logger.info(name + " = " + val + " (" + ms + "ms)");
            }).start();
        }));

        // ON / OFF
        if (type.equals("bool") || type.equals("int")) {
            line2.addView(makeSmallBtn("ON", C_GREEN, v -> {
                new Thread(() -> {
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
                }).start();
            }));
            line2.addView(makeSmallBtn("OFF", C_RED, v -> {
                new Thread(() -> {
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
                }).start();
            }));
        }

        // SET
        line2.addView(makeSmallBtn("SET", C_ORANGE, v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) { Logger.warn("请输入值"); return; }
            new Thread(() -> {
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
            }).start();
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
    //  导出/导入
    // ═══════════════════════════════════════

    private void exportLogs() {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sb.append("# C11 车控测试 日志导出\n");
            sb.append("# 时间: ").append(sdf.format(new Date())).append("\n");
            sb.append("# ADB: ").append(adbCustom ? adbHost + ":" + adbPort : "本地").append("\n\n");
            sb.append(logView.getText().toString());

            String filename = "c11_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            boolean ok = Sh.writeFile("/sdcard/" + filename, sb.toString());
            Logger.ok(ok ? "日志已导出: /sdcard/" + filename : "导出失败");
        }).start();
    }

    private void exportLogcat() {
        new Thread(() -> {
            String logcat = VehicleControl.getLogcat(200);
            String filename = "c11_logcat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            boolean ok = Sh.writeFile("/sdcard/" + filename, logcat);
            Logger.ok(ok ? "logcat 已导出: /sdcard/" + filename : "导出失败");
        }).start();
    }

    private void exportSnapshot() {
        new Thread(() -> {
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
        }).start();
    }

    private void importSnapshot() {
        new Thread(() -> {
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
        }).start();
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
            new Thread(() -> {
                if ("shell".equals(ns)) { String out = Sh.out(val); Logger.cmd(val, out); h.post(() -> showResultDialog("Shell", out)); }
                else if ("prop".equals(ns)) { String out = Sh.out("getprop " + val); Logger.info("getprop " + val + " = " + out); h.post(() -> showResultDialog("getprop", out)); }
                else { String out = Sh.out("settings get global " + val); Logger.info("settings " + val + " = " + out); h.post(() -> showResultDialog("settings", out)); }
            }).start();
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
            new Thread(() -> {
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
            }).start();
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
}
