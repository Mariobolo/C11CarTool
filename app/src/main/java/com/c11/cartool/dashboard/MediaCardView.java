package com.c11.cartool.dashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.c11.cartool.media.MediaHub;

import java.util.List;

/**
 * 播放器媒体卡（玻璃底）：播放器选择 + 封面 / 标题 / 艺术家 + 上一首 / 播放暂停 / 下一首。
 *
 * <p>{@link MediaHub} 连接成功走 TransportControls；失败通过 {@link Shell} 降级为
 * shell {@code input keyevent}（85 播放暂停 / 87 下一首 / 88 上一首）。
 */
public class MediaCardView extends FrameLayout implements MediaHub.Listener {

    /** 降级通道：执行 shell 媒体按键（85/87/88）；启动指定播放器。 */
    public interface Shell {
        void mediaKey(int keyCode);
        void launchPlayer(String pkg);
    }

    private final MediaHub hub;
    private final Shell shell;

    private TextView playerName;
    private TextView titleView;
    private TextView artistView;
    private ImageView coverView;
    private TextView playPauseBtn;

    public MediaCardView(Context ctx, MediaHub h, Shell sh) {
        super(ctx);
        this.hub = h;
        this.shell = sh;
        setBackground(Glass.bg(ctx, 14, Glass.NORMAL));
        setPadding(dp(12), dp(10), dp(12), dp(10));
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶行：播放器名 + 切换
        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        playerName = make(GridDimens.SP_ACTION, DashboardTheme.TEXT, true);
        playerName.setText("未选择播放器");
        TextView switchBtn = make(GridDimens.SP_ACTION, DashboardTheme.TEXT, true);
        switchBtn.setText("切换 ▾");
        switchBtn.setBackground(Glass.bg(getContext(), 10, Glass.NORMAL));
        switchBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        switchBtn.setOnClickListener(v -> showPicker());
        top.addView(playerName, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        top.addView(switchBtn);
        root.addView(top);

        // 中行：封面 + 标题 / 艺术家
        LinearLayout mid = new LinearLayout(getContext());
        mid.setGravity(Gravity.CENTER_VERTICAL);
        coverView = new ImageView(getContext());
        coverView.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams clp =
                new LinearLayout.LayoutParams(dp(64), dp(64));
        clp.rightMargin = dp(12);
        mid.addView(coverView, clp);

        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = make(GridDimens.SP_ACTION, DashboardTheme.TEXT, true);
        titleView.setText("--");
        artistView = make(GridDimens.SP_LABEL, DashboardTheme.DIM, false);
        artistView.setText("--");
        texts.addView(titleView);
        texts.addView(artistView);
        mid.addView(texts, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        root.addView(mid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底行：上一首 / 播放暂停 / 下一首
        LinearLayout ctrls = new LinearLayout(getContext());
        ctrls.setGravity(Gravity.CENTER);
        TextView prev = ctrlButton("⏮");
        playPauseBtn = ctrlButton("⏯");
        TextView next = ctrlButton("⏭");
        ctrls.addView(prev, ctrlLp(1f));
        ctrls.addView(playPauseBtn, ctrlLp(1.4f));
        ctrls.addView(next, ctrlLp(1f));
        root.addView(ctrls);

        prev.setOnClickListener(v -> run(hub.previous(), 88));
        playPauseBtn.setOnClickListener(v -> run(hub.playPause(), 85));
        next.setOnClickListener(v -> run(hub.next(), 87));
    }

    /** hub 已处理则结束；否则 shell 降级媒体按键。 */
    private void run(boolean handled, int keyCode) {
        if (!handled && shell != null) shell.mediaKey(keyCode);
    }

    private LinearLayout.LayoutParams ctrlLp(float weight) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, dp(64), weight);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private void showPicker() {
        List<MediaHub.PlayerInfo> players = hub.listPlayers();
        String[] labels = new String[players.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = players.get(i).name;
        new AlertDialog.Builder(getContext())
                .setTitle("选择播放器")
                .setItems(labels, (d, which) -> {
                    MediaHub.PlayerInfo p = players.get(which);
                    playerName.setText(p.name);
                    hub.select(p.pkg);
                    if (shell != null) shell.launchPlayer(p.pkg);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override public void onMetadata(String title, String artist, Bitmap cover, boolean playing) {
        titleView.setText(title == null || title.isEmpty() ? "--" : title);
        artistView.setText(artist == null || artist.isEmpty() ? "--" : artist);
        if (cover != null) coverView.setImageBitmap(cover);
        playPauseBtn.setText(playing ? "⏸" : "▶");
    }

    private TextView ctrlButton(String s) {
        TextView b = make(GridDimens.SP_ACTION, DashboardTheme.TEXT, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Glass.bg(getContext(), 10, Glass.NORMAL));
        return b;
    }

    private TextView make(int sp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
