package com.c11.cartool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

/**
 * C11 车控测试工具 v2.0
 *
 * 基于 c11assistant 确认的接口:
 *   - Settings.Global 读写
 *   - IVI 广播控制 (灯光/驾驶模式/场景/空调)
 *   - Logcat 事件监控
 *
 * 227 个参数, 23 个 Tab
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

    // 颜色
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

    // Tab 定义
    static final String[] TAB_NAMES = {
            "📊 全部", "❄️ 空调", "💡 灯光", "🪑 座椅", "🚗 车身",
            "🪟 车窗", "🏎️ 驾驶", "🎯 场景", "🔊 音量", "⚡ 充电",
            "🔋 电池", "🛡️ ADAS", "🔧 行车", "📡 系统", "🔒 安全",
            "🎵 媒体", "🗺️ 导航", "📱 蓝牙", "🌧️ 雨刷", "🌡️ 环境",
            "📏 里程", "🏷️ 车辆", "📋 日志"
    };

    // Tab 对应的参数过滤关键词
    static final String[] TAB_FILTERS = {
            null,
            "空调|hvac|temp|defrost|ac_",
            "灯光|light|ambient|氛围",
            "座椅|seat|steering|按摩|加热|通风",
            "车身|vehicle|child|mirror|trunk|window_lock|锁|门",
            "车窗|window|sunroof|sunshade|天窗|遮阳",
            "驾驶|drive|steer|energy|转向|能量|回收",
            "场景|scene|rest|camping|guard|sentinel|小憩|露营|守护|哨兵",
            "音量|volume|C11_|SPEECH|XIAOLING|语音|小灵",
            "充电|charging|charge|gun_lock",
            "电池|battery|range|续航|里程",
            "ADAS|adas|acc|aeb|lka|ldw|bsd|fcw|dow|rcta|rcw|alc|tsr|hwa|lcc|tja|ica|isa|slif|bsi|ir|sdis|sai|parking|pdc|hdc|ccs",
            "疲劳|face|dms|creep|one_pedal|低速|蠕行|踏板",
            "系统|system|wifi|bluetooth|hotspot|dark|pedestrian|update|usb|network",
            "安全|safety|lock_sound|find_car|flash|belt|password|guest|锁车声音|寻车|安全带|密码|访客",
            "媒体|media|source|playing|track|artist|album|fm|eq",
            "导航|navi|destination|distance|eta|traffic",
            "蓝牙|bt|phone|电话|来电",
            "雨刷|wiper|spray|喷水",
            "环境|env|pm25|temp_inside|temp_outside|PM2.5|车内温度|车外温度",
            "里程|odo|trip|总里程|行程",
            "车辆|car|vin|model|year|color|config|gear|speed|rpm|VIN|车型|年款|颜色|档位|车速",
            null // 日志 tab
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());
        Logger.setCallback((line, level) -> h.post(() -> appendLog(line, level)));
        setContentView(buildUI());

        new Thread(() -> {
            DeviceInfo info = DeviceInfo.detect();
            h.post(() -> statusLine.setText(String.format("UID:%d %s SELinux:%s",
                    info.uid, info.model, info.seLinux)));
            Logger.title("C11 车控测试工具 v2.0");
            Logger.info("设备: " + info.model + " | Android " + info.android + " | SDK " + info.sdk);
            Logger.info("UID: " + info.uid + " | SELinux: " + info.seLinux);
            Logger.info("参数总数: " + VehicleParams.getCount());
            Logger.info("基于 c11assistant 确认的接口");
        }).start();
    }

    // ═══════════════════════════════════════
    //  UI 构建
    // ═══════════════════════════════════════

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

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
        header.setBackgroundColor(C_SURFACE);
        header.setPadding(12, 8, 12, 8);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("🚗 C11 车控测试 v2.0");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusLine = new TextView(this);
        statusLine.setText("检测中...");
        statusLine.setTextColor(C_DIM);
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        header.addView(statusLine);

        return header;
    }

    private View buildSearchBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(C_SURFACE);
        row.setPadding(8, 0, 8, 4);
        row.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = new EditText(this);
        searchBox.setHint("🔍 搜索参数 (名称/key)...");
        searchBox.setHintTextColor(C_DIM);
        searchBox.setTextColor(C_TEXT);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        searchBox.setBackgroundColor(C_CARD);
        searchBox.setPadding(8, 6, 8, 6);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        row.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button searchBtn = makeSmallBtn("搜索", C_BLUE, v -> doSearch());
        row.addView(searchBtn);
        Button allBtn = makeSmallBtn("全部", C_SURFACE, v -> switchTab(0));
        row.addView(allBtn);

        return row;
    }

    private View buildTabBar() {
        ScrollView tabScroll = new ScrollView(this);
        tabScroll.setBackgroundColor(C_SURFACE);
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
        logArea.setBackgroundColor(C_SURFACE);
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
        logHeader.addView(makeSmallBtn("📖 全部刷新", C_PURPLE, v -> refreshAll()));
        logArea.addView(logHeader);

        // 应用日志
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(C_CARD);
        logScroll.setPadding(6, 6, 6, 6);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        logView.setTextColor(C_TEXT);
        logView.setLineSpacing(1, 1);
        logScroll.addView(logView);
        logArea.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 180));

        // 车辆日志
        vehLogScroll = new ScrollView(this);
        vehLogScroll.setBackgroundColor(C_CARD);
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

        if (idx == TAB_NAMES.length - 1) {
            buildLogTab();
            return;
        }

        String filter = TAB_FILTERS[idx];
        List<String[]> params;
        if (filter == null) {
            params = new ArrayList<>();
            for (String[] p : VehicleParams.PARAMS) params.add(p);
        } else {
            params = VehicleParams.getByCategory(filter);
        }

        // 统计
        TextView countTv = new TextView(this);
        countTv.setText("共 " + params.size() + " 个参数");
        countTv.setTextColor(C_DIM);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        countTv.setPadding(4, 4, 4, 4);
        contentArea.addView(countTv);

        // 快捷操作面板 (仅非全部 tab 显示)
        if (filter != null) {
            contentArea.addView(buildQuickActions(idx));
        }

        // 批量读取
        contentArea.addView(makeBtn("📖 批量读取本页全部参数", C_BLUE, v -> {
            new Thread(() -> {
                Logger.info("批量读取 " + params.size() + " 个参数...");
                for (String[] p : params) {
                    String val = VehicleControl.get(p[0], p[3]);
                    Logger.info(p[1] + " (" + p[0] + ") = " + val);
                }
                Logger.ok("批量读取完成");
            }).start();
        }));

        // 参数列表
        for (String[] p : params) {
            contentArea.addView(makeParamRow(p));
        }
    }

    /**
     * 快捷操作面板 - 根据 tab 显示常用操作按钮
     */
    private View buildQuickActions(int tabIdx) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(C_CARD);
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
                btnRow.addView(makeSmallBtn("❄️ 最大制冷 ON", C_GREEN, v -> {
                    VehicleControl.setAcMax(true);
                    Logger.ok("最大制冷 ON");
                }));
                btnRow.addView(makeSmallBtn("🔴 最大制冷 OFF", C_RED, v -> {
                    VehicleControl.setAcMax(false);
                    Logger.ok("最大制冷 OFF");
                }));
                break;

            case 2: // 灯光
                btnRow.addView(makeSmallBtn("💡 近光灯 ON", C_GREEN, v -> {
                    VehicleControl.setLowBeam(true);
                    Logger.ok("近光灯 ON");
                }));
                btnRow.addView(makeSmallBtn("🔴 近光灯 OFF", C_RED, v -> {
                    VehicleControl.setLowBeam(false);
                    Logger.ok("近光灯 OFF");
                }));
                btnRow.addView(makeSmallBtn("💡 示廓灯 ON", C_GREEN, v -> {
                    VehicleControl.setPositionLight(true);
                    Logger.ok("示廓灯 ON");
                }));
                btnRow.addView(makeSmallBtn("💡 后雾灯 ON", C_GREEN, v -> {
                    VehicleControl.setRearFog(true);
                    Logger.ok("后雾灯 ON");
                }));
                btnRow.addView(makeSmallBtn("🔊 行人警示 ON", C_GREEN, v -> {
                    VehicleControl.setPedestriansAlert(true);
                    Logger.ok("行人警示 ON");
                }));
                break;

            case 6: // 驾驶
                String[] modes = {"舒适", "运动", "自定义", "极致", "经济"};
                int[] colors = {C_GREEN, C_RED, C_YELLOW, C_PURPLE, C_CYAN};
                for (int i = 0; i < modes.length; i++) {
                    final int mode = i;
                    btnRow.addView(makeSmallBtn(modes[i], colors[i], v -> {
                        VehicleControl.setDriverMode(mode);
                        Logger.ok("驾驶模式: " + modes[mode]);
                    }));
                }
                break;

            case 7: // 场景
                btnRow.addView(makeSmallBtn("😴 小憩", C_BLUE, v -> {
                    VehicleControl.setRestMode(true);
                    Logger.ok("小憩模式 ON");
                }));
                btnRow.addView(makeSmallBtn("⛺ 露营", C_GREEN, v -> {
                    VehicleControl.setCampingMode(true);
                    Logger.ok("露营模式 ON");
                }));
                btnRow.addView(makeSmallBtn("🛡️ 守护", C_YELLOW, v -> {
                    VehicleControl.setGuardMode(true);
                    Logger.ok("守护模式 ON");
                }));
                btnRow.addView(makeSmallBtn("👁️ 哨兵", C_RED, v -> {
                    VehicleControl.setSentinelMode(true);
                    Logger.ok("哨兵模式 ON");
                }));
                btnRow.addView(makeSmallBtn("🔋 省电", C_CYAN, v -> {
                    VehicleControl.setPowerSaveMode(true);
                    Logger.ok("省电模式 ON");
                }));
                break;

            case 13: // 系统
                btnRow.addView(makeSmallBtn("📶 WiFi", C_GREEN, v -> {
                    VehicleControl.setWifi(true);
                    Logger.ok("WiFi ON");
                }));
                btnRow.addView(makeSmallBtn("📱 蓝牙", C_GREEN, v -> {
                    VehicleControl.setBluetooth(true);
                    Logger.ok("蓝牙 ON");
                }));
                btnRow.addView(makeSmallBtn("🌞 日间模式", C_YELLOW, v -> {
                    VehicleControl.setDayNightMode(true);
                    Logger.ok("日间模式");
                }));
                btnRow.addView(makeSmallBtn("🌙 夜间模式", C_PURPLE, v -> {
                    VehicleControl.setDayNightMode(false);
                    Logger.ok("夜间模式");
                }));
                break;

            case 15: // 媒体
                btnRow.addView(makeSmallBtn("🎵 打开音乐", C_GREEN, v -> {
                    VehicleControl.openMedia();
                    Logger.ok("打开音乐");
                }));
                break;

            case 16: // 导航
                btnRow.addView(makeSmallBtn("🗺️ 打开导航", C_GREEN, v -> {
                    VehicleControl.openAutonavi();
                    Logger.ok("打开导航");
                }));
                break;

            case 21: // 车辆
                btnRow.addView(makeSmallBtn("🔒 锁车", C_GREEN, v -> {
                    VehicleControl.setSetting("strCarVehicleLock", "1", "setting");
                    Logger.ok("锁车");
                }));
                btnRow.addView(makeSmallBtn("🔓 解锁", C_BLUE, v -> {
                    VehicleControl.setSetting("strCarVehicleLock", "0", "setting");
                    Logger.ok("解锁");
                }));
                btnRow.addView(makeSmallBtn("🏠 回主页", C_SURFACE, v -> {
                    VehicleControl.backToHome();
                    Logger.ok("回主页");
                }));
                break;
        }

        if (btnRow.getChildCount() > 0) {
            panel.addView(btnRow);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        panel.setLayoutParams(lp);
        return panel;
    }

    private void buildLogTab() {
        contentArea.addView(makeSectionTitle("📋 日志工具"));

        contentArea.addView(makeBtn("📖 读取 logcat (最近50行)", C_BLUE, v -> {
            new Thread(() -> {
                String log = VehicleControl.getLogcat(50);
                Logger.info("logcat:\n" + log);
            }).start();
        }));

        contentArea.addView(makeBtn("🗑 清除 logcat 缓冲区", C_RED, v -> {
            VehicleControl.clearLogcat();
            Logger.ok("logcat 已清除");
        }));

        contentArea.addView(makeBtn("📖 读取全部 leap.* 属性", C_GREEN, v -> {
            new Thread(() -> {
                String all = VehicleControl.getAllLeapProps();
                Logger.info("leap.* 属性:\n" + all);
                showResultDialog("leap.* 属性", all);
            }).start();
        }));

        contentArea.addView(makeBtn("📖 读取全部 strCar* 设置", C_GREEN, v -> {
            new Thread(() -> {
                String all = VehicleControl.getAllCarSettings();
                Logger.info("strCar* 设置:\n" + all);
                showResultDialog("strCar* 设置", all);
            }).start();
        }));

        contentArea.addView(makeSectionTitle("🔧 自定义命令"));
        contentArea.addView(makeEditRow("执行 getprop", "custom_prop", "prop", "leap.cabin.driver_temp", "属性名"));
        contentArea.addView(makeEditRow("执行 settings get", "custom_setting", "setting", "C11_MUSIC", "设置名"));
        contentArea.addView(makeEditRow("执行 shell 命令", "custom_shell", "shell", "id", "命令"));

        contentArea.addView(makeSectionTitle("🗣️ TTS 语音测试"));
        LinearLayout ttsRow = new LinearLayout(this);
        ttsRow.setOrientation(LinearLayout.HORIZONTAL);
        ttsRow.setPadding(0, 4, 0, 4);
        ttsRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText ttsInput = new EditText(this);
        ttsInput.setHint("输入语音内容...");
        ttsInput.setHintTextColor(C_DIM);
        ttsInput.setTextColor(C_TEXT);
        ttsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ttsInput.setBackgroundColor(C_CARD);
        ttsInput.setPadding(8, 6, 8, 6);
        ttsInput.setText("你好，我是小零");
        ttsRow.addView(ttsInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button ttsBtn = makeSmallBtn("🗣️ 播放", C_GREEN, v -> {
            String text = ttsInput.getText().toString().trim();
            if (!text.isEmpty()) {
                VehicleControl.speak(text);
                Logger.ok("TTS: " + text);
            }
        });
        ttsRow.addView(ttsBtn);
        contentArea.addView(ttsRow);

        contentArea.addView(makeSectionTitle("📋 ADB 授权"));
        contentArea.addView(makeBtn("📋 复制授权命令", C_YELLOW, v -> {
            copyToClipboard("adb shell pm grant com.c11.cartool android.permission.WRITE_SECURE_SETTINGS");
            Logger.ok("已复制授权命令");
        }));
    }

    // ═══════════════════════════════════════
    //  参数行
    // ═══════════════════════════════════════

    private View makeParamRow(String[] param) {
        String key = param[0];
        String name = param[1];
        String type = param[2];
        String ns = param[3];
        String hint = param[4];
        String range = param[5];

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(C_CARD);
        row.setPadding(8, 4, 8, 4);

        // 第一行: 名称 + key + 命名空间
        LinearLayout line1 = new LinearLayout(this);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(C_TEXT);
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

        // 第二行: 值 + 操作按钮
        LinearLayout line2 = new LinearLayout(this);
        line2.setOrientation(LinearLayout.HORIZONTAL);
        line2.setGravity(Gravity.CENTER_VERTICAL);
        line2.setPadding(0, 2, 0, 0);

        // 值显示
        TextView valTv = new TextView(this);
        valTv.setText("--");
        valTv.setTextColor(C_YELLOW);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);
        valTv.setPadding(4, 0, 8, 0);
        line2.addView(valTv);

        // 范围提示
        if (!range.isEmpty()) {
            TextView rangeTv = new TextView(this);
            rangeTv.setText(range);
            rangeTv.setTextColor(C_DIM);
            rangeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            rangeTv.setPadding(4, 0, 8, 0);
            line2.addView(rangeTv);
        }

        // 输入框
        EditText et = new EditText(this);
        et.setHint(hint.isEmpty() ? "值" : hint);
        et.setHintTextColor(C_DIM);
        et.setTextColor(C_YELLOW);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        et.setBackgroundColor(C_SURFACE);
        et.setPadding(6, 2, 6, 2);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        etLp.setMargins(4, 0, 4, 0);
        et.setLayoutParams(etLp);
        line2.addView(et);

        // 📖 读取
        line2.addView(makeSmallBtn("📖", C_BLUE, v -> {
            new Thread(() -> {
                String val = VehicleControl.get(key, ns);
                h.post(() -> valTv.setText(val));
                Logger.info(name + " = " + val + " (" + key + ")");
            }).start();
        }));

        // ON / OFF (bool/int 类型)
        if (type.equals("bool") || type.equals("int")) {
            line2.addView(makeSmallBtn("ON", C_GREEN, v -> {
                new Thread(() -> {
                    // 优先使用广播方式 (已确认可用)
                    if (tryBroadcastControl(key, true)) {
                        h.post(() -> { valTv.setText("1"); valTv.setTextColor(C_GREEN); });
                    } else if ("prop".equals(ns)) {
                        Logger.warn("getprop 属性不支持直接写入: " + key);
                    } else {
                        VehicleControl.setSetting(key, "1", ns);
                        h.post(() -> { valTv.setText("1"); valTv.setTextColor(C_GREEN); });
                        Logger.ok(name + " → ON (" + key + "=1)");
                    }
                }).start();
            }));

            line2.addView(makeSmallBtn("OFF", C_RED, v -> {
                new Thread(() -> {
                    if (tryBroadcastControl(key, false)) {
                        h.post(() -> { valTv.setText("0"); valTv.setTextColor(C_RED); });
                    } else if ("prop".equals(ns)) {
                        Logger.warn("getprop 属性不支持直接写入: " + key);
                    } else {
                        VehicleControl.setSetting(key, "0", ns);
                        h.post(() -> { valTv.setText("0"); valTv.setTextColor(C_RED); });
                        Logger.ok(name + " → OFF (" + key + "=0)");
                    }
                }).start();
            }));
        }

        // SET
        line2.addView(makeSmallBtn("SET", C_ORANGE, v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) { Logger.warn("请输入值"); return; }

            new Thread(() -> {
                if ("shell".equals(ns)) {
                    String out = Sh.out(val);
                    Logger.cmd(val, out);
                    h.post(() -> showResultDialog("Shell 输出", out));
                } else if ("prop".equals(ns)) {
                    String out = Sh.out("getprop " + val);
                    Logger.info("getprop " + val + " = " + out);
                    h.post(() -> showResultDialog("getprop " + val, out));
                } else if ("setting".equals(ns)) {
                    String out = Sh.out("settings get global " + val);
                    Logger.info("settings get global " + val + " = " + out);
                    h.post(() -> showResultDialog("settings " + val, out));
                } else {
                    VehicleControl.setSetting(key, val, ns);
                    h.post(() -> valTv.setText(val));
                    Logger.ok(name + " → " + val + " (" + key + "=" + val + ")");
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

    /**
     * 尝试使用广播方式控制 (基于 c11assistant 确认的接口)
     * @return true 如果使用了广播方式
     */
    private boolean tryBroadcastControl(String key, boolean on) {
        switch (key) {
            // 灯光
            case "leap.light.headlight":
            case "CARLIGHT_JINGUANG":
                VehicleControl.setLowBeam(on);
                Logger.ok("近光灯 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.light.rear_fog":
            case "CARLIGHT_REARFOGCTL":
                VehicleControl.setRearFog(on);
                Logger.ok("后雾灯 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.light.position":
            case "CARLIGHT_SHEKUODENG":
                VehicleControl.setPositionLight(on);
                Logger.ok("示廓灯 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.system.pedestrians_alert":
            case "PEDESTRIANS_ALERT":
                VehicleControl.setPedestriansAlert(on);
                Logger.ok("行人警示音 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            // 空调
            case "leap.hvac.ac_max":
            case "HVACACMAXREQ":
                VehicleControl.setAcMax(on);
                Logger.ok("最大制冷 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            // 场景模式
            case "leap.scene.guard":
            case "GUARD_MODE":
                VehicleControl.setGuardMode(on);
                Logger.ok("守护模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.scene.rest":
            case "REST_MODE":
                VehicleControl.setRestMode(on);
                Logger.ok("小憩模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.scene.camping":
            case "CAMPING_MODE":
                VehicleControl.setCampingMode(on);
                Logger.ok("露营模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.scene.power_save":
            case "POWER_SAVE_MODE":
                VehicleControl.setPowerSaveMode(on);
                Logger.ok("省电模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.scene.sentinel":
            case "SENTINEL_MODE":
                VehicleControl.setSentinelMode(on);
                Logger.ok("哨兵模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.scene.experience":
            case "EXPERIENCE_MODE":
                VehicleControl.setExperienceMode(on);
                Logger.ok("体验模式 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            // 系统
            case "leap.system.wifi":
                VehicleControl.setWifi(on);
                Logger.ok("WiFi " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.system.bluetooth":
                VehicleControl.setBluetooth(on);
                Logger.ok("蓝牙 " + (on ? "ON" : "OFF") + " (broadcast)");
                return true;
            case "leap.system.dark_mode":
                VehicleControl.setDayNightMode(!on);
                Logger.ok((on ? "夜间" : "日间") + "模式 (broadcast)");
                return true;
            // 儿童锁
            case "leap.vehicle.child_lock":
            case "strCarChildLock":
                VehicleControl.setChildLock(on);
                Logger.ok("儿童锁 " + (on ? "开" : "关") + " (broadcast JSON)");
                return true;
            default:
                return false;
        }
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

        if (results.isEmpty()) {
            TextView noneTv = new TextView(this);
            noneTv.setText("未找到匹配的参数");
            noneTv.setTextColor(C_DIM);
            noneTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            noneTv.setPadding(16, 32, 16, 32);
            noneTv.setGravity(Gravity.CENTER);
            contentArea.addView(noneTv);
        } else {
            for (String[] p : results) {
                contentArea.addView(makeParamRow(p));
            }
        }
    }

    // ═══════════════════════════════════════
    //  通用 UI 组件
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
        row.setBackgroundColor(C_CARD);
        row.setPadding(8, 6, 8, 6);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(C_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.35f));

        EditText et = new EditText(this);
        et.setText(hint);
        et.setTextColor(C_YELLOW);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        et.setBackgroundColor(C_SURFACE);
        et.setPadding(6, 2, 6, 2);
        row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        TextView rangeTv = new TextView(this);
        rangeTv.setText(range);
        rangeTv.setTextColor(C_DIM);
        rangeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        row.addView(rangeTv);

        Button setBtn = makeSmallBtn("SET", C_BLUE, v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) return;
            new Thread(() -> {
                if ("shell".equals(ns)) {
                    String out = Sh.out(val);
                    Logger.cmd(val, out);
                    h.post(() -> showResultDialog("Shell 输出", out));
                } else if ("prop".equals(ns)) {
                    String out = Sh.out("getprop " + val);
                    Logger.info("getprop " + val + " = " + out);
                    h.post(() -> showResultDialog("getprop " + val, out));
                } else {
                    String out = Sh.out("settings get global " + val);
                    Logger.info("settings get global " + val + " = " + out);
                    h.post(() -> showResultDialog("settings " + val, out));
                }
            }).start();
        });
        row.addView(setBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private void refreshAll() {
        new Thread(() -> {
            Logger.info("刷新全部 " + VehicleParams.getCount() + " 个参数...");
            int ok = 0, fail = 0;
            for (String[] p : VehicleParams.PARAMS) {
                String val = VehicleControl.get(p[0], p[3]);
                if (val != null && !val.isEmpty() && !val.equals("null")) ok++;
                else fail++;
            }
            Logger.ok("刷新完成: " + ok + " 有值, " + fail + " 无值");
        }).start();
    }

    // ═══════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════

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
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("cmd", text));
            Logger.ok("已复制到剪贴板");
        }
    }
}
