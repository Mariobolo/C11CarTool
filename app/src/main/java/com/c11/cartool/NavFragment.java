package com.c11.cartool;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 导航卡片 Fragment
 *
 * 功能:
 *   - 显示导航地图占位 (后续嵌入高德地图)
 *   - 快捷打开高德地图
 *   - 显示当前位置/目的地信息
 *   - PiP 画中画入口
 */
public class NavFragment extends Fragment {

    private static final String AMAP_PKG = "com.leapmotor.autonavi";
    private TextView statusView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1D27);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 标题栏
        LinearLayout titleBar = new LinearLayout(getActivity());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(12, 8, 12, 8);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(0xFF1E2330);

        TextView title = new TextView(getActivity());
        title.setText("🗺️ 导航");
        title.setTextColor(0xFFE4E4E7);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, tp);

        Button fullscreenBtn = makeSmallBtn("全屏", 0xFF3B82F6, v -> openAmap());
        titleBar.addView(fullscreenBtn);

        root.addView(titleBar);

        // 地图占位区
        ScrollView mapArea = new ScrollView(getActivity());
        mapArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout mapContent = new LinearLayout(getActivity());
        mapContent.setOrientation(LinearLayout.VERTICAL);
        mapContent.setGravity(Gravity.CENTER);
        mapContent.setPadding(20, 40, 20, 40);

        TextView mapIcon = new TextView(getActivity());
        mapIcon.setText("🗺️");
        mapIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        mapIcon.setGravity(Gravity.CENTER);
        mapContent.addView(mapIcon);

        TextView mapHint = new TextView(getActivity());
        mapHint.setText("导航地图区域\n(后续嵌入高德地图)");
        mapHint.setTextColor(0xFF8B8FA3);
        mapHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mapHint.setGravity(Gravity.CENTER);
        mapHint.setPadding(0, 12, 0, 20);
        mapContent.addView(mapHint);

        statusView = new TextView(getActivity());
        statusView.setText("状态: 未启动导航");
        statusView.setTextColor(0xFFEAB308);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, 8, 0, 8);
        mapContent.addView(statusView);

        Button openBtn = makeBtn("打开高德地图", 0xFF22C55E, v -> openAmap());
        mapContent.addView(openBtn);

        Button pipBtn = makeBtn("进入画中画 (PiP)", 0xFF06B6D4, v -> enterPip());
        mapContent.addView(pipBtn);

        mapArea.addView(mapContent);
        root.addView(mapArea);

        return root;
    }

    private void openAmap() {
        try {
            Intent intent = getActivity().getPackageManager().getLaunchIntentForPackage(AMAP_PKG);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                statusView.setText("状态: 已启动高德地图");
            } else {
                statusView.setText("错误: 未安装高德地图 (" + AMAP_PKG + ")");
            }
        } catch (Exception e) {
            statusView.setText("错误: " + e.getMessage());
        }
    }

    private void enterPip() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams params =
                        new android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(new android.util.Rational(16, 9))
                                .build();
                getActivity().enterPictureInPictureMode(params);
                statusView.setText("状态: 已进入画中画模式");
            } catch (Exception e) {
                statusView.setText("PiP 不可用: " + e.getMessage());
            }
        } else {
            statusView.setText("PiP 需要 Android 8.0+");
        }
    }

    private Button makeBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(16, 8, 16, 8);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button makeSmallBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(10, 4, 10, 4);
        btn.setOnClickListener(listener);
        return btn;
    }
}
