package com.c11.cartool;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * C11 车机桌面 — 三分屏 Launcher
 *
 * 布局:
 *   ┌─────────────────────────────────────────────┐
 *   │  状态栏 (时间/温度/网络/蓝牙/用户)            │
 *   ├──────────┬──────────────────┬───────────────┤
 *   │          │                  │               │
 *   │  导航卡片  │   车控/车辆信息卡片 │    媒体卡片     │
 *   │  (40%)   │     (35%)        │    (25%)      │
 *   │          │                  │               │
 *   ├──────────┴──────────────────┴───────────────┤
 *   │  底部 Dock (常用APP / 空调 / 座椅 / 设置)     │
 *   └─────────────────────────────────────────────┘
 *
 * 交互:
 *   - 拖动白色滑块调整卡片宽度
 *   - 点击卡片全屏展开
 *   - 三指横滑切换页面
 *   - 双击状态栏显示/隐藏设置
 *   - PiP 画中画 (导航卡片)
 */
public class CarLauncherActivity extends Activity {

    private static final String PREFS = "c11_launcher";
    private static final String KEY_RATIO_0 = "ratio_0";
    private static final String KEY_RATIO_1 = "ratio_1";
    private static final String KEY_RATIO_2 = "ratio_2";

    private Handler h;
    private SharedPreferences prefs;

    private ResizableSplitLayout splitLayout;
    private TextView timeView, dateView, tempView, netView;
    private boolean clockRunning = false;

    // 三指滑动检测
    private float[] fingerStartX = new float[3];
    private float[] fingerStartY = new float[3];
    private int fingerCount = 0;
    private static final float SWIPE_THRESHOLD = 100f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        h = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        setContentView(buildLauncher());
        startClock();
        Logger.info("C11 车机桌面已启动");
    }

    private View buildLauncher() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1117);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // === 顶部状态栏 ===
        root.addView(buildStatusBar());

        // === 三分屏主区域 ===
        splitLayout = new ResizableSplitLayout(this);
        splitLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 恢复保存的比例
        float r0 = prefs.getFloat(KEY_RATIO_0, 0.40f);
        float r1 = prefs.getFloat(KEY_RATIO_1, 0.35f);
        float r2 = prefs.getFloat(KEY_RATIO_2, 0.25f);
        splitLayout.setRatios(r0, r1, r2);

        splitLayout.setOnRatioChangedListener(ratios -> {
            prefs.edit()
                    .putFloat(KEY_RATIO_0, ratios[0])
                    .putFloat(KEY_RATIO_1, ratios[1])
                    .putFloat(KEY_RATIO_2, ratios[2])
                    .apply();
        });

        // 添加三个卡片容器
        FrameLayout navContainer = new FrameLayout(this);
        navContainer.setId(View.generateViewId());
        splitLayout.addView(navContainer);

        FrameLayout carContainer = new FrameLayout(this);
        carContainer.setId(View.generateViewId());
        splitLayout.addView(carContainer);

        FrameLayout mediaContainer = new FrameLayout(this);
        mediaContainer.setId(View.generateViewId());
        splitLayout.addView(mediaContainer);

        root.addView(splitLayout);

        // === 底部 Dock ===
        root.addView(buildDock());

        // 加载 Fragment
        getFragmentManager().beginTransaction()
                .add(navContainer.getId(), new NavFragment(), "nav")
                .add(carContainer.getId(), new CarControlFragment(), "car")
                .add(mediaContainer.getId(), new MediaFragment(), "media")
                .commit();

        return root;
    }

    // ==================== 状态栏 ====================

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF1A1D27);
        bar.setPadding(16, 6, 16, 6);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 左侧: 时间 + 日期
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        timeView = new TextView(this);
        timeView.setText("--:--");
        timeView.setTextColor(0xFFE4E4E7);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        timeView.setTypeface(null, Typeface.BOLD);
        left.addView(timeView);

        dateView = new TextView(this);
        dateView.setText("----/--/-- 周--");
        dateView.setTextColor(0xFF8B8FA3);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        left.addView(dateView);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(left, leftLp);

        // 中间: 车外温度
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        TextView tempIcon = new TextView(this);
        tempIcon.setText("🌡️");
        tempIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        center.addView(tempIcon);
        tempView = new TextView(this);
        tempView.setText("--°");
        tempView.setTextColor(0xFFE4E4E7);
        tempView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tempView.setPadding(4, 0, 0, 0);
        center.addView(tempView);
        bar.addView(center, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧: 网络 + 蓝牙 + 用户
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        netView = new TextView(this);
        netView.setText("📶 4G");
        netView.setTextColor(0xFF8B8FA3);
        netView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        netView.setPadding(0, 0, 12, 0);
        right.addView(netView);

        TextView btView = new TextView(this);
        btView.setText("🔵");
        btView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btView.setPadding(0, 0, 12, 0);
        right.addView(btView);

        TextView userView = new TextView(this);
        userView.setText("👤");
        userView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        right.addView(userView);

        bar.addView(right, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 双击状态栏重置比例
        bar.setOnClickListener(new View.OnClickListener() {
            private long lastClick = 0;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClick < 500) {
                    splitLayout.resetRatios();
                    Logger.info("已重置卡片比例为默认 40/35/25");
                }
                lastClick = now;
            }
        });

        return bar;
    }

    // ==================== 底部 Dock ====================

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackgroundColor(0xFF1A1D27);
        dock.setPadding(12, 8, 12, 8);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 常用 APP 快捷
        String[][] apps = {
                {"📞", "电话"},
                {"🎵", "音乐"},
                {"📻", "收音机"},
                {"📷", "行车记录"},
                {"⚙️", "设置"}
        };
        for (String[] app : apps) {
            LinearLayout appBtn = new LinearLayout(this);
            appBtn.setOrientation(LinearLayout.VERTICAL);
            appBtn.setGravity(Gravity.CENTER);
            appBtn.setPadding(8, 4, 8, 4);
            TextView icon = new TextView(this);
            icon.setText(app[0]);
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            icon.setGravity(Gravity.CENTER);
            appBtn.addView(icon);
            TextView label = new TextView(this);
            label.setText(app[1]);
            label.setTextColor(0xFF8B8FA3);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            label.setGravity(Gravity.CENTER);
            appBtn.addView(label);
            appBtn.setOnClickListener(v -> Logger.info("打开: " + app[1]));
            dock.addView(appBtn, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        // 分隔
        View sep = new View(this);
        sep.setBackgroundColor(0xFF333333);
        dock.addView(sep, new LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT));

        // 空调快捷
        LinearLayout acQuick = new LinearLayout(this);
        acQuick.setOrientation(LinearLayout.HORIZONTAL);
        acQuick.setGravity(Gravity.CENTER_VERTICAL);
        acQuick.setPadding(8, 0, 8, 0);

        Button acMinus = makeDockBtn("-", 0xFF3B82F6, v -> {
            Logger.info("空调温度 -1");
        });
        TextView acTemp = new TextView(this);
        acTemp.setText("22°");
        acTemp.setTextColor(0xFFE4E4E7);
        acTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        acTemp.setTypeface(null, Typeface.BOLD);
        acTemp.setGravity(Gravity.CENTER);
        acTemp.setPadding(8, 0, 8, 0);
        Button acPlus = makeDockBtn("+", 0xFF3B82F6, v -> {
            Logger.info("空调温度 +1");
        });
        acQuick.addView(acMinus);
        acQuick.addView(acTemp);
        acQuick.addView(acPlus);
        dock.addView(acQuick);

        // 分隔
        View sep2 = new View(this);
        sep2.setBackgroundColor(0xFF333333);
        dock.addView(sep2, new LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT));

        // 座椅加热
        LinearLayout seatQuick = new LinearLayout(this);
        seatQuick.setOrientation(LinearLayout.HORIZONTAL);
        seatQuick.setGravity(Gravity.CENTER_VERTICAL);
        seatQuick.setPadding(8, 0, 8, 0);
        Button seatHeat = makeDockBtn("🔥 座椅", 0xFFF97316, v -> {
            Logger.info("座椅加热切换");
        });
        seatQuick.addView(seatHeat);
        dock.addView(seatQuick);

        // 分隔
        View sep3 = new View(this);
        sep3.setBackgroundColor(0xFF333333);
        dock.addView(sep3, new LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT));

        // 进入原工具
        Button toolBtn = makeDockBtn("🔧 车控工具", 0xFFA855F7, v -> {
            startActivity(new Intent(this, MainActivity.class));
        });
        dock.addView(toolBtn);

        return dock;
    }

    // ==================== 时钟 ====================

    private void startClock() {
        clockRunning = true;
        new Thread(() -> {
            SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy/MM/dd EEE", Locale.getDefault());
            while (clockRunning) {
                final String time = timeFmt.format(new Date());
                final String date = dateFmt.format(new Date());
                h.post(() -> {
                    timeView.setText(time);
                    dateView.setText(date);
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    // ==================== 三指滑动 ====================

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int count = event.getPointerCount();

        if (count >= 3) {
            if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_DOWN) {
                fingerCount = count;
                for (int i = 0; i < Math.min(3, count); i++) {
                    fingerStartX[i] = event.getX(i);
                    fingerStartY[i] = event.getY(i);
                }
            } else if (action == MotionEvent.ACTION_MOVE && fingerCount >= 3) {
                // 检测三指横滑
                float dx0 = event.getX(0) - fingerStartX[0];
                float dx1 = event.getX(1) - fingerStartX[1];
                float dx2 = event.getX(2) - fingerStartX[2];
                float avgDx = (dx0 + dx1 + dx2) / 3f;

                if (Math.abs(avgDx) > SWIPE_THRESHOLD) {
                    if (avgDx > 0) {
                        Logger.info("三指右滑 -> 切换到驻车模式");
                    } else {
                        Logger.info("三指左滑 -> 切换到行车模式");
                    }
                    fingerCount = 0; // 重置，避免连续触发
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fingerCount = 0;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    // ==================== PiP ====================

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        Logger.info("PiP 模式: " + (isInPictureInPictureMode ? "进入" : "退出"));
        // PiP 模式下隐藏状态栏和 Dock，只显示导航
        if (isInPictureInPictureMode) {
            // 可以在这里调整布局
        }
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockRunning = false;
    }

    @Override
    public void onBackPressed() {
        // 桌面不响应返回键 (保持在桌面)
        // super.onBackPressed();
    }

    // ==================== UI 工具 ====================

    private Button makeDockBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(10, 6, 10, 6);
        btn.setOnClickListener(listener);
        return btn;
    }
}
