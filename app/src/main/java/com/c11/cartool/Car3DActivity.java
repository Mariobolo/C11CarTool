package com.c11.cartool;
import com.c11.cartool.dashboard.DashboardTheme;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.c11.cartool.dashboard.DashboardSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 3D 车模第二界面（v0.3.13）。
 *
 * <p>全屏沉浸 WebView 加载 assets/car3d 的零跑 3D 车模演示页（官方 C16 资源内联版），
 * 页面自带的点按/滑动/长按交互与场景切换保留；叠加 HUD 数据面板
 * （js/car_bridge.js）由本页定时把最新车辆快照推送为 JSON。
 *
 * <p>入口：仪表盘右侧悬浮「3D」按钮，或主界面左滑手势。
 * 返回：右上角悬浮 ✕ 按钮 / 硬件返回键。
 *
 * <p>数据源：{@link #latest} 静态快照，由 {@code DashboardActivity.onSnapshot}
 * 每轮更新（与仪表盘共用一份采集结果，不重复采集）。
 */
public class Car3DActivity extends Activity {

    /** 仪表盘最新快照（主线程写，本页线程读；volatile 保证可见性） */
    public static volatile DashboardSnapshot latest;

    private WebView wv;
    private boolean loaded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long PUSH_INTERVAL_MS = 1200;

    private final Runnable pusher = new Runnable() {
        @Override public void run() {
            if (loaded && wv != null) {
                DashboardSnapshot s = latest;
                if (s != null && s.hasTs()) {
                    String json = buildJson(s);
                    wv.evaluateJavascript("window.updateCarData(" + json + ")", null);
                }
            }
            handler.postDelayed(this, PUSH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // file:// 协议下 SystemJS 动态加载 bundle 与 wasm 需要放开同/跨文件域
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                loaded = true;
                handler.removeCallbacks(pusher);
                handler.post(pusher);
            }
        });
        // [v0.3.25] 补 loadUrl：v0.3.13 起缺失，WebView 空载导致 3D 页纯白
        wv.loadUrl("file:///android_asset/car3d/index.html");
        host.addView(wv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 右上悬浮返回按钮（避开 HUD 右上角数据块，置顶居中偏右更安全）
        TextView back = new TextView(this);
        back.setText("✕");
        back.setTextColor(DashboardTheme.TEXT);
        back.setTextSize(20);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundColor(0xAA0A0E18);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(52), dp(52),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = dp(14);
        host.addView(back, blp);
        back.setOnClickListener(v -> finish());

        setContentView(host);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wv != null) wv.onResume();
        if (loaded) {
            handler.removeCallbacks(pusher);
            handler.post(pusher);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(pusher);
        if (wv != null) wv.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pusher);
        if (wv != null) { wv.destroy(); wv = null; }
    }

    /** 快照 → HUD JSON（字段与 js/car_bridge.js 的 updateCarData 一一对应） */
    private String buildJson(DashboardSnapshot s) {
        JSONObject o = new JSONObject();
        try {
            o.put("speed", s.speedKmh);
            o.put("gear", s.gear);
            o.put("soc", s.batterySoc);
            o.put("rangeStd", s.rangeStd);
            o.put("outsideTemp", s.outsideTemp);
            o.put("voltage", s.voltage);
            o.put("current", s.current);
            o.put("doors", toArray(s.doorStates));
            o.put("windows", toArray(s.windowPct));
            o.put("tireKpa", toArray(s.tirePressKpa));
            o.put("acSwitch", s.acSwitch);
            o.put("fanSpeed", s.fanSpeed);
            o.put("frontDefrost", s.frontDefrost);
            o.put("innerCycle", s.innerCycle);
            o.put("vehicleLock", s.vehicleLock);
            o.put("childLock", s.childLock);
            o.put("adbConnected", s.adbConnected);
            String mode = s.extra.get("strCarAirMode");
            if (mode == null) mode = s.extra.get("strCarAirStatus");
            o.put("acModeRaw", mode == null ? "" : mode);
        } catch (Exception ignored) {
            // JSON 组装失败则本次跳过，下一轮重试
        }
        return o.toString();
    }

    private static JSONArray toArray(float[] v) throws org.json.JSONException {
        JSONArray a = new JSONArray();
        if (v != null) for (float x : v) a.put(x);
        return a;
    }

    private static JSONArray toArray(int[] v) throws org.json.JSONException {
        JSONArray a = new JSONArray();
        if (v != null) for (int x : v) a.put(x);
        return a;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
