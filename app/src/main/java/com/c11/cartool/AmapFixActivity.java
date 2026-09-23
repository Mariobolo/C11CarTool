package com.c11.cartool;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 高德地图定位修复工具
 *
 * 问题: 新版高德地图(v3.30.0)内置 vsomeip 客户端, 但未读取系统 SOME/IP 配置,
 *       导致服务发现多播地址/单播IP/应用ID不匹配, 无法获取车辆定位, 兜底到北京天安门.
 *
 * 修复: 推送修正后的 vsomeip.json 到地图可读路径 + 授予定位权限 + 清除数据重启.
 *
 * 系统配置来源: /vendor/etc/vsomeip/someip.json
 *   - unicast: 192.168.1.3
 *   - 服务发现多播: 224.224.244.245:30490
 *   - 高德应用ID: gaode_sub=0x9011, gaode_pub=0x9012
 */
public class AmapFixActivity extends Activity {

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

    private static final String AMAP_PKG = "com.leapmotor.autonavi";

    private Handler h;
    private TextView diagView;
    private TextView logView;
    private ScrollView logScroll;
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());
        setContentView(buildUI());
        Sh.submitAsync(this::runDiagnosis);
    }

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 标题栏
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setBackgroundColor(C_SURFACE);
        titleBar.setPadding(12, 10, 12, 10);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        Button backBtn = makeBtn("← 返回", C_DIM, v -> finish());
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("🗺️ 高德地图定位修复");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(12, 0, 0, 0);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, tp);
        root.addView(titleBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(10, 10, 10, 10);

        // 诊断信息
        content.addView(makeSectionTitle("🔍 诊断信息"));
        diagView = new TextView(this);
        diagView.setText("正在诊断...");
        diagView.setTextColor(C_DIM);
        diagView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        diagView.setBackgroundColor(C_CARD);
        diagView.setPadding(10, 8, 10, 8);
        diagView.setMovementMethod(new ScrollingMovementMethod());
        content.addView(diagView);

        // 修复操作
        content.addView(makeSectionTitle("🛠️ 修复操作"));
        content.addView(makeBtn("🔄 重新诊断", C_BLUE,
                v -> Sh.submitAsync(this::runDiagnosis)));
        content.addView(makeBtn("✅ 一键修复（推送配置+授权+清数据+重启）", C_GREEN,
                v -> { if (!running) { running = true; Sh.submitAsync(this::runOneClickFix); } }));
        content.addView(makeBtn("📄 仅推送 vsomeip.json 配置", C_CYAN,
                v -> Sh.submitAsync(this::deployConfig)));
        content.addView(makeBtn("🔑 仅授予定位权限", C_YELLOW,
                v -> Sh.submitAsync(this::grantPermissions)));
        content.addView(makeBtn("🧹 仅清除地图数据", C_ORANGE,
                v -> Sh.submitAsync(() -> {
                    log("清除地图数据...");
                    Sh.Result r = Sh.run("pm clear " + AMAP_PKG);
                    log(r.ok() ? "✅ 数据已清除" : "❌ 清除失败: " + r.err);
                })));
        content.addView(makeBtn("🚀 重启高德地图", C_PURPLE,
                v -> Sh.submitAsync(() -> {
                    log("重启高德地图...");
                    Sh.run("am force-stop " + AMAP_PKG);
                    try { Thread.sleep(500); } catch (Exception e) {}
                    Sh.run("monkey -p " + AMAP_PKG + " -c android.intent.category.LAUNCHER 1");
                    log("✅ 已发送启动命令");
                })));

        // 日志工具
        content.addView(makeSectionTitle("📝 日志工具"));
        content.addView(makeBtn("📋 抓取 vsomeip/定位日志 (10秒)", C_BLUE,
                v -> Sh.submitAsync(this::captureLogs)));
        content.addView(makeBtn("🗑️ 清空操作日志", C_DIM,
                v -> logView.setText("")));

        // 操作日志
        content.addView(makeSectionTitle("📜 操作日志"));
        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 400));
        logView = new TextView(this);
        logView.setTextColor(C_TEXT);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logView.setBackgroundColor(C_CARD);
        logView.setPadding(10, 8, 10, 8);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logScroll.addView(logView);
        content.addView(logScroll);

        scroll.addView(content);
        root.addView(scroll);
        return root;
    }

    // ==================== 诊断 ====================

    private void runDiagnosis() {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统环境】\n");
        int uid = Sh.uid();
        sb.append("UID: ").append(uid).append(uid == 0 ? " (root ✅)" : " (非root ⚠️部分操作需root)").append("\n");

        sb.append("\n【网络接口】\n");
        Sh.Result ip = Sh.run("ip addr | grep -E 'inet |^[0-9]+:'");
        sb.append(ip.out).append("\n");
        sb.append(ip.out.contains("192.168.1.3") ? "✅ 内部以太网 192.168.1.3 存在\n" : "❌ 未找到 192.168.1.3 接口\n");

        sb.append("\n【SOME/IP 服务】\n");
        Sh.Result ps = Sh.run("ps -A | grep someip");
        sb.append(ps.out.length() > 0 ? ps.out : "未找到 someip 进程\n");
        Sh.Result svc = Sh.run("getprop init.svc.someip_route");
        sb.append("someip_route 状态: ").append(svc.out).append("\n");

        sb.append("\n【高德地图】\n");
        Sh.Result pkg = Sh.run("dumpsys package " + AMAP_PKG + " | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime'");
        sb.append(pkg.out.length() > 0 ? pkg.out : "未安装\n");

        sb.append("\n【定位权限】\n");
        Sh.Result perm = Sh.run("dumpsys package " + AMAP_PKG + " | grep -A5 'runtime permissions'");
        sb.append(perm.out).append("\n");
        Sh.Result appops = Sh.run("appops get " + AMAP_PKG + " android:location 2>&1");
        sb.append("appops location: ").append(appops.out).append("\n");

        sb.append("\n【配置文件检查】\n");
        String[] paths = {
                "/data/data/" + AMAP_PKG + "/files/vsomeip.json",
                "/data/data/" + AMAP_PKG + "/vsomeip.json",
                "/data/local/tmp/vsomeip.json",
                "/vendor/etc/vsomeip/someip.json",
                "/etc/vsomeip.json",
                "/etc/vsomeip/vsomeip.json"
        };
        for (String p : paths) {
            boolean exists = Sh.ok("test -f " + p);
            sb.append(exists ? "✅ " : "❌ ").append(p).append("\n");
        }

        sb.append("\n【SELinux】\n");
        Sh.Result se = Sh.run("getenforce");
        sb.append("状态: ").append(se.out).append("\n");
        Sh.Result avc = Sh.run("dmesg | grep -c 'avc:.*denied.*autonavi' 2>/dev/null || echo 0");
        sb.append("高德相关SELinux拒绝次数: ").append(avc.out).append("\n");

        h.post(() -> diagView.setText(sb.toString()));
    }

    // ==================== 一键修复 ====================

    private void runOneClickFix() {
        log("========== 开始一键修复 ==========");
        try {
            log("\n[1/5] 推送 vsomeip.json 配置...");
            deployConfigInternal();

            log("\n[2/5] 授予定位权限...");
            grantPermissionsInternal();

            log("\n[3/5] 停止高德地图...");
            Sh.run("am force-stop " + AMAP_PKG);
            log("✅ 已停止");

            log("\n[4/5] 清除地图数据...");
            Sh.Result clear = Sh.run("pm clear " + AMAP_PKG);
            log(clear.ok() ? "✅ 数据已清除" : "⚠️ 清除失败(需root): " + clear.err);
            if (clear.ok()) {
                log("数据被清除, 重新推送配置...");
                deployConfigInternal();
            }

            log("\n[5/5] 启动高德地图...");
            Sh.run("monkey -p " + AMAP_PKG + " -c android.intent.category.LAUNCHER 1");
            log("✅ 已发送启动命令");

            log("\n========== 修复完成 ==========");
            log("请查看地图定位是否恢复.");
            log("若仍定位到北京天安门, 请点击「抓取日志」排查具体错误.");
            log("常见原因: SELinux拦截多播socket / 配置路径不匹配 / 需系统级签名.");
        } catch (Exception e) {
            log("❌ 修复异常: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    // ==================== 配置部署 ====================

    private void deployConfig() {
        log("开始推送 vsomeip.json...");
        boolean ok = deployConfigInternal();
        log(ok ? "✅ 配置推送完成" : "❌ 配置推送失败");
    }

    private boolean deployConfigInternal() {
        try {
            String config = readAsset("vsomeip.json");
            if (config == null || config.length() == 0) {
                log("❌ 无法读取 assets/vsomeip.json");
                return false;
            }
            log("配置大小: " + config.length() + " 字节");

            // /data/local/tmp (全局可读)
            boolean tmpOk = Sh.writeFile("/data/local/tmp/vsomeip.json", config);
            log((tmpOk ? "✅ " : "❌ ") + "/data/local/tmp/vsomeip.json");

            // 应用 files 目录
            String filesDir = "/data/data/" + AMAP_PKG + "/files";
            Sh.run("mkdir -p " + filesDir);
            boolean filesOk = Sh.writeFile(filesDir + "/vsomeip.json", config);
            log((filesOk ? "✅ " : "⚠️ ") + filesDir + "/vsomeip.json" + (filesOk ? "" : " (需root)"));

            // root 时写入 /etc
            if (Sh.uid() == 0) {
                Sh.run("mkdir -p /etc/vsomeip");
                boolean etcOk = Sh.writeFile("/etc/vsomeip/vsomeip.json", config);
                log((etcOk ? "✅ " : "❌ ") + "/etc/vsomeip/vsomeip.json");
            } else {
                log("ℹ️ 非root, 跳过 /etc/vsomeip.json");
            }

            Sh.run("chmod 644 /data/local/tmp/vsomeip.json 2>/dev/null");
            Sh.run("chmod 644 " + filesDir + "/vsomeip.json 2>/dev/null");
            return tmpOk || filesOk;
        } catch (Exception e) {
            log("❌ 部署异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 权限 ====================

    private void grantPermissions() {
        log("开始授予定位权限...");
        grantPermissionsInternal();
    }

    private void grantPermissionsInternal() {
        String[] perms = {
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS"
        };
        for (String perm : perms) {
            Sh.Result r = Sh.run("pm grant " + AMAP_PKG + " " + perm + " 2>&1");
            log((r.ok() ? "✅ " : "⚠️ ") + perm + (r.ok() ? "" : " (" + r.err.trim() + ")"));
        }
        Sh.Result ao = Sh.run("appops set " + AMAP_PKG + " android:location allow 2>&1");
        log((ao.ok() ? "✅ " : "⚠️ ") + "appops location allow");
    }

    // ==================== 日志抓取 ====================

    private void captureLogs() {
        log("开始抓取日志(10秒)...");
        log("请在此期间操作高德地图尝试定位...");
        try {
            Sh.run("logcat -c");
            Thread.sleep(10000);
            Sh.Result r = Sh.run("logcat -d -t 800 | grep -iE 'vsomeip|someip|multicast|location|gnss|gps|gaode|autonavi|avc.*denied'");
            String result = r.out;
            if (result.length() == 0) result = "（未捕获到相关日志，可能地图未产生相关输出）";
            final String fr = result;
            h.post(() -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("日志抓取结果 (" + fr.length() + " 字符)");
                TextView tv = new TextView(this);
                tv.setText(fr);
                tv.setTextSize(10);
                tv.setMovementMethod(new ScrollingMovementMethod());
                tv.setPadding(20, 10, 20, 10);
                ScrollView sv = new ScrollView(this);
                sv.addView(tv);
                b.setView(sv);
                b.setPositiveButton("确定", null);
                b.show();
            });
            log("✅ 抓取完成, 共 " + result.length() + " 字符");
        } catch (Exception e) {
            log("❌ 抓取异常: " + e.getMessage());
        }
    }

    // ==================== 工具 ====================

    private String readAsset(String name) {
        try {
            InputStream is = getAssets().open(name);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            br.close();
            is.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        final String line = "[" + time + "] " + msg;
        h.post(() -> {
            logView.append(line + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(C_CYAN);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 12, 0, 6);
        return tv;
    }

    private Button makeBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(16, 10, 16, 10);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        btn.setLayoutParams(lp);
        return btn;
    }
}
