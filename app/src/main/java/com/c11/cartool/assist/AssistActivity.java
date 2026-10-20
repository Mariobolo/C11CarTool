package com.c11.cartool.assist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.c11.cartool.Sh;
import com.c11.cartool.vehicle.VehicleController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 功能辅助页面：开机自动化、多屏启动、限制解除、转向灯360、TTS、语音控制。
 * 配置实时持久化（{@link AssistConfig}）；开机执行由 {@link BootAutomationService} 承担。
 */
public class AssistActivity extends Activity {

    private AssistConfig cfg;
    private VehicleController vc;
    private LinearLayout root;

    private int pickingDisplay = 0;
    private TextView subTargetText, dashTargetText, accessibilityState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = new AssistConfig(this);
        vc = new VehicleController(this);

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(24));
        sv.addView(root);

        buildHeader();
        buildBootSection();
        buildDisplaySection();
        buildLimitsSection();
        buildTurn360Section();
        buildTtsSection();
        buildVoiceSection();
        buildCameraPreviewSection();

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accessibilityState != null)
            accessibilityState.setText("无障碍服务：" + (isAssistOn() ? "已开启 ✅" : "未开启 ❌"));
    }

    // ── header ──
    private void buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("← 返回");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("  功能辅助");
        title.setTextSize(22);
        h.addView(back);
        h.addView(title);
        root.addView(h);
    }

    // ── 1 开机自动化 ──
    private void buildBootSection() {
        LinearLayout s = section("一、开机自动化");
        s.addView(switchRow("开机自动开启无线 ADB（启动系统 Demo 并点击）",
                cfg.adbAuto(), cfg::setAdbAuto));
        s.addView(switchRow("同时启动 Shizuku（intent 方式）",
                cfg.shizuku(), cfg::setShizuku));
        s.addView(switchRow("ADB 授权弹窗自动确认（自动点“始终允许”，重启不再手动授权）",
                cfg.adbAuthAuto(), cfg::setAdbAuthAuto));
        accessibilityState = new TextView(this);
        accessibilityState.setPadding(0, dp(8), 0, dp(8));
        s.addView(accessibilityState);
        s.addView(button("去开启无障碍服务",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(s);
    }

    // ── 2 多屏启动：选择后立即启动一次（验证）+ 开关控制每次开机自动执行 ──
    private void buildDisplaySection() {
        LinearLayout s = section("二、多屏启动（选择后立即启动一次验证；开关=每次开机自动执行）");

        s.addView(switchRow("副驾娱乐屏（display 1）开机自动启动", cfg.subScreen(), cfg::setSubScreen));
        subTargetText = targetText(cfg.subPkg(), cfg.subAct());
        s.addView(subTargetText);
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.addView(button("选择副屏程序（选中即启动一次）", v -> {
            pickingDisplay = AssistConfig.DISPLAY_SUB; pickApp();
        }));
        subRow.addView(button("启动副屏一次", v -> startOnce(AssistConfig.DISPLAY_SUB)));
        s.addView(subRow);

        s.addView(switchRow("仪表屏（display 2）开机自动启动", cfg.dashScreen(), cfg::setDashScreen));
        dashTargetText = targetText(cfg.dashPkg(), cfg.dashAct());
        s.addView(dashTargetText);
        LinearLayout dashRow = new LinearLayout(this);
        dashRow.setOrientation(LinearLayout.HORIZONTAL);
        dashRow.addView(button("选择仪表程序（选中即启动一次）", v -> {
            pickingDisplay = AssistConfig.DISPLAY_DASH; pickApp();
        }));
        dashRow.addView(button("启动仪表一次", v -> startOnce(AssistConfig.DISPLAY_DASH)));
        s.addView(dashRow);

        root.addView(s);
    }

    /**
     * [v0.3.26] 多屏一次性启动：am start --display N（选择完自动调一次，也可手动再点）。
     * [v0.3.27] 解析 am start 输出给明确失败原因：普通第三方 app 的 launcher 活动
     * 未经多屏适配时会被拒（Error type 3/SecurityException），车机上已知可在副屏/仪表
     * 屏运行的入口（原车高德仪表导航 ThirdActivity 等）需要 action 上下文，故失败时
     * 把真实原因展示出来，而不是笼统“见日志”。
     */
    private void startOnce(int display) {
        String pkg = display == AssistConfig.DISPLAY_SUB ? cfg.subPkg() : cfg.dashPkg();
        String act = display == AssistConfig.DISPLAY_SUB ? cfg.subAct() : cfg.dashAct();
        if (pkg == null || pkg.isEmpty() || act == null || act.isEmpty()) {
            Toast.makeText(this, "请先选择程序", Toast.LENGTH_SHORT).show();
            return;
        }
        final String label = display == AssistConfig.DISPLAY_SUB ? "副屏" : "仪表屏";
        String cmd = "am start -n " + pkg + "/" + act + " --display " + display;
        Sh.Result r = Sh.run(cmd, 10000);
        if (r.ok()) {
            Toast.makeText(this, "已启动（" + label + " display " + display + "）",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String err = r.err == null ? "" : r.err;
        String out = r.out == null ? "" : r.out.trim();
        String all = (err + " " + out).toLowerCase();
        String detail;
        if (all.contains("type 3") || all.contains("does not exist"))
            detail = "活动不存在/未安装（该程序不支持副屏启动）";
        else if (all.contains("securityexception") || all.contains("not allowed"))
            detail = "无权限（程序未声明多屏支持）";
        else if (all.contains("not found"))
            detail = "程序未找到";
        else if (all.contains("unable to resolve"))
            detail = "无法解析（可改用预置入口）";
        else detail = "详见日志: " + (err.isEmpty() ? out : err);
        if (detail.length() > 120) detail = detail.substring(0, 120) + "…";
        Toast.makeText(this, "启动失败：" + detail, Toast.LENGTH_LONG).show();
    }

    // ── 3 限制解除 ──
    private void buildLimitsSection() {
        LinearLayout s = section("三、限制解除（开机执行）");
        s.addView(switchRow("行人警示：开机时关闭行人提示音",
                cfg.pedestrianOff(), cfg::setPedestrianOff));
        s.addView(switchRow("行驶中可看视频（C11_VIDEO_ENABLE=1）",
                cfg.videoEnable(), cfg::setVideoEnable));
        s.addView(switchRow("任何时速均可开启 360（camera_overspeed=0）",
                cfg.camera360AnySpeed(), cfg::setCamera360AnySpeed));
        root.addView(s);
    }

    // ── 4 转向灯 360 ──
    private void buildTurn360Section() {
        LinearLayout s = section("四、转向灯 360 视觉辅助");
        s.addView(switchRow("启用（转向自动开360+后视；回正后 2 秒防抖再关）",
                cfg.turnSignal360(), on -> {
                    cfg.setTurnSignal360(on);
                    startService(new Intent(this, TurnSignal360Service.class)
                            .setAction(on ? TurnSignal360Service.ACTION_START
                                          : TurnSignal360Service.ACTION_STOP));
                }));
        root.addView(s);
    }

    // ── 5 TTS ──
    private void buildTtsSection() {
        LinearLayout s = section("五、TTS 语音播报");
        final EditText input = new EditText(this);
        input.setHint("输入要播报的文本");
        s.addView(input);
        s.addView(button("播放", v -> {
            boolean ok = vc.speak(input.getText().toString());
            if (ok) {
                Toast.makeText(this, "已发送播报", Toast.LENGTH_SHORT).show();
            } else {
                String reason = VehicleController.lastVoiceError();
                Toast.makeText(this, reason == null || reason.isEmpty()
                                ? "播报失败（需 ADB 连接）" : "播报失败：" + reason,
                        Toast.LENGTH_LONG).show();
            }
        }));
        root.addView(s);
    }

    // ── 6 语音控制 ──
    private void buildVoiceSection() {
        LinearLayout s = section("六、语音控制（小零）");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("唤起", v -> toast(vc.invokeVoice(), "已唤起小零")));
        row.addView(button("停止/释放", v -> toast(vc.releaseVoice(), "已释放麦克风")));
        row.addView(button("退出", v -> toast(vc.exitVoice(), "已退出语音")));
        s.addView(row);
        root.addView(s);
    }

    // ── 7 摄像头预览（lp_around，实车标定） ──
    private void buildCameraPreviewSection() {
        LinearLayout s = section("七、摄像头预览（lp_around 反射客户端）");
        s.addView(button("打开摄像头预览页", v ->
                startActivity(new Intent(this, CamPreviewActivity.class))));
        root.addView(s);
    }

    // ── 应用选择 ──
    private void pickApp() {
        final List<AppEntry> apps = loadApps();
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("选择程序（取其主 Activity）")
                .setItems(labels, (d, which) -> {
                    AppEntry e = apps.get(which);
                    if (pickingDisplay == AssistConfig.DISPLAY_SUB) {
                        cfg.setSubTarget(e.pkg, e.act);
                        subTargetText.setText(target(e.pkg, e.act));
                    } else {
                        cfg.setDashTarget(e.pkg, e.act);
                        dashTargetText.setText(target(e.pkg, e.act));
                    }
                    // [v0.3.26] 选择完立即在对应屏启动一次（验证目标可用）
                    startOnce(pickingDisplay);
                }).show();
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            String act = ri.activityInfo.name;
            out.add(new AppEntry(ri.loadLabel(pm) + "  (" + pkg + ")", pkg, act));
        }
        Collections.sort(out, Comparator.comparing(a -> a.label));
        return out;
    }

    // ── 小工具 ──
    private LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackgroundColor(Color.argb(28, 0, 150, 200));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setPadding(0, 0, 0, dp(8));
        box.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        box.setLayoutParams(lp);
        return box;
    }

    private interface OnSwitch { void on(boolean v); }

    private Switch switchRow(String label, boolean initial, OnSwitch set) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(16);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((b, checked) -> set.on(checked));
        return sw;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView targetText(String pkg, String act) {
        TextView tv = new TextView(this);
        tv.setText(target(pkg, act));
        tv.setPadding(0, dp(8), 0, dp(8));
        return tv;
    }

    private String target(String pkg, String act) {
        if (pkg == null || pkg.isEmpty()) return "未选择程序";
        return "目标：" + pkg + "/" + act;
    }

    private boolean isAssistOn() {
        String en = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return en != null
                && en.contains(getPackageName() + "/" + C11AssistService.class.getName());
    }

    private void toast(boolean ok, String okText) {
        Toast.makeText(this, ok ? okText : "操作失败（需 ADB，见日志）",
                Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppEntry {
        final String label, pkg, act;
        AppEntry(String label, String pkg, String act) {
            this.label = label; this.pkg = pkg; this.act = act;
        }
    }
}
