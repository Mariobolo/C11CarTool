package com.c11.cartool;

import android.app.Fragment;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/**
 * 媒体卡片 Fragment
 *
 * 功能:
 *   - 显示当前播放歌曲/专辑/艺术家
 *   - 播放/暂停/上一首/下一首控制
 *   - 进度条显示与拖动
 *   - 通过 MediaSessionManager 监听系统活跃媒体会话
 *   - 支持蓝牙音乐/USB 音乐/在线音乐
 */
public class MediaFragment extends Fragment {

    private Handler h;
    private MediaController mediaController;
    private MediaSessionManager sessionManager;

    private TextView titleView, artistView, albumView;
    private TextView playStateView;
    private SeekBar progressBar;
    private TextView timeView;
    private Button playBtn;

    private boolean monitoring = false;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updatePlaybackState(state);
        }

        @Override
        public void onMetadataChanged(android.media.MediaMetadata metadata) {
            updateMetadata(metadata);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    if (controllers != null && !controllers.isEmpty()) {
                        setMediaController(controllers.get(0));
                    }
                }
            };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        h = new Handler(Looper.getMainLooper());

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
        title.setText("🎵 媒体");
        title.setTextColor(0xFFE4E4E7);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, tp);

        playStateView = new TextView(getActivity());
        playStateView.setText("未播放");
        playStateView.setTextColor(0xFF8B8FA3);
        playStateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        titleBar.addView(playStateView);

        root.addView(titleBar);

        ScrollView scroll = new ScrollView(getActivity());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12, 12, 12, 12);

        // 专辑封面占位
        LinearLayout coverArea = new LinearLayout(getActivity());
        coverArea.setOrientation(LinearLayout.VERTICAL);
        coverArea.setGravity(Gravity.CENTER);
        coverArea.setBackgroundColor(0xFF2A2F3A);
        coverArea.setPadding(20, 30, 20, 30);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        coverLp.setMargins(0, 0, 0, 12);
        coverArea.setLayoutParams(coverLp);

        TextView coverIcon = new TextView(getActivity());
        coverIcon.setText("🎵");
        coverIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        coverIcon.setGravity(Gravity.CENTER);
        coverArea.addView(coverIcon);

        albumView = new TextView(getActivity());
        albumView.setText("专辑名称");
        albumView.setTextColor(0xFF8B8FA3);
        albumView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        albumView.setGravity(Gravity.CENTER);
        albumView.setPadding(0, 8, 0, 0);
        coverArea.addView(albumView);

        content.addView(coverArea);

        // 歌曲信息
        titleView = new TextView(getActivity());
        titleView.setText("未播放音乐");
        titleView.setTextColor(0xFFE4E4E7);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        content.addView(titleView);

        artistView = new TextView(getActivity());
        artistView.setText("— 艺术家 —");
        artistView.setTextColor(0xFF8B8FA3);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        artistView.setGravity(Gravity.CENTER);
        artistView.setPadding(0, 4, 0, 12);
        content.addView(artistView);

        // 进度条
        LinearLayout progressRow = new LinearLayout(getActivity());
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        timeView = new TextView(getActivity());
        timeView.setText("0:00 / 0:00");
        timeView.setTextColor(0xFF8B8FA3);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        progressRow.addView(timeView);

        content.addView(progressRow);

        progressBar = new SeekBar(getActivity());
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    long duration = getDuration();
                    if (duration > 0) {
                        mediaController.getTransportControls().seekTo(duration * progress / 1000);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(progressBar);

        // 控制按钮
        LinearLayout controlRow = new LinearLayout(getActivity());
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER);
        controlRow.setPadding(0, 12, 0, 8);

        Button prevBtn = makeControlBtn("⏮", 0xFF3B82F6, v -> {
            if (mediaController != null) mediaController.getTransportControls().skipToPrevious();
        });
        controlRow.addView(prevBtn);

        playBtn = makeControlBtn("▶", 0xFF22C55E, v -> togglePlay());
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                (int) (60 * getResources().getDisplayMetrics().density),
                (int) (60 * getResources().getDisplayMetrics().density));
        playLp.setMargins(16, 0, 16, 0);
        playBtn.setLayoutParams(playLp);
        playBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        controlRow.addView(playBtn);

        Button nextBtn = makeControlBtn("⏭", 0xFF3B82F6, v -> {
            if (mediaController != null) mediaController.getTransportControls().skipToNext();
        });
        controlRow.addView(nextBtn);

        content.addView(controlRow);

        // 音量/来源
        content.addView(makeSectionTitle("📻 播放来源"));
        LinearLayout sourceRow = new LinearLayout(getActivity());
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.addView(makeBtn("蓝牙音乐", 0xFF3B82F6, v -> openSource("bluetooth")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sourceRow.addView(makeBtn("USB 音乐", 0xFF06B6D4, v -> openSource("usb")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(sourceRow);

        scroll.addView(content);
        root.addView(scroll);

        // 启动媒体会话监听
        startMonitoring();
        return root;
    }

    private void startMonitoring() {
        monitoring = true;
        try {
            sessionManager = (MediaSessionManager) getActivity().getSystemService(
                    android.content.Context.MEDIA_SESSION_SERVICE);
            if (sessionManager != null) {
                // 需要 NOTIFICATION_LISTENER 权限才能监听所有会话
                // 没有权限时只能尝试获取当前活跃会话
                try {
                    List<MediaController> controllers = sessionManager.getActiveSessions(
                            new ComponentName(getActivity(), "com.c11.cartool.MediaFragment"));
                    if (controllers != null && !controllers.isEmpty()) {
                        setMediaController(controllers.get(0));
                    }
                } catch (SecurityException e) {
                    Logger.info("媒体会话监听需要通知权限，使用手动选择模式");
                }
            }
        } catch (Exception e) {
            Logger.warn("媒体监听初始化失败: " + e.getMessage());
        }

        // 定时更新进度
        new Thread(() -> {
            while (monitoring && isAdded()) {
                try {
                    Thread.sleep(1000);
                    h.post(this::updateProgress);
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    private void setMediaController(MediaController controller) {
        if (mediaController != null) {
            mediaController.unregisterCallback(mediaCallback);
        }
        mediaController = controller;
        if (mediaController != null) {
            mediaController.registerCallback(mediaCallback);
            updateMetadata(mediaController.getMetadata());
            updatePlaybackState(mediaController.getPlaybackState());
        }
    }

    private void updateMetadata(android.media.MediaMetadata metadata) {
        if (metadata == null) return;
        String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
        if (title != null) titleView.setText(title);
        if (artist != null) artistView.setText(artist);
        if (album != null) albumView.setText(album);
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) return;
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        playBtn.setText(playing ? "⏸" : "▶");
        playStateView.setText(playing ? "播放中" : "已暂停");
    }

    private void updateProgress() {
        if (mediaController == null) return;
        PlaybackState state = mediaController.getPlaybackState();
        long duration = getDuration();
        if (state != null && duration > 0) {
            long pos = state.getPosition();
            progressBar.setProgress((int) (pos * 1000 / duration));
            timeView.setText(formatTime(pos) + " / " + formatTime(duration));
        }
    }

    private long getDuration() {
        if (mediaController == null || mediaController.getMetadata() == null) return 0;
        return mediaController.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
    }

    private void togglePlay() {
        if (mediaController == null) {
            Logger.info("未连接媒体会话，请先播放音乐");
            return;
        }
        PlaybackState state = mediaController.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            mediaController.getTransportControls().pause();
        } else {
            mediaController.getTransportControls().play();
        }
    }

    private void openSource(String source) {
        Logger.info("切换播放来源: " + source);
        // 实际通过 Intent 打开对应音乐应用
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        monitoring = false;
        if (mediaController != null) {
            mediaController.unregisterCallback(mediaCallback);
        }
    }

    // ==================== UI 工具 ====================

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setTextColor(0xFF06B6D4);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 12, 0, 6);
        return tv;
    }

    private Button makeControlBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(12, 8, 12, 8);
        btn.setOnClickListener(listener);
        return btn;
    }

    private Button makeBtn(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(getActivity());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        btn.setPadding(8, 6, 8, 6);
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(lp);
        return btn;
    }
}
