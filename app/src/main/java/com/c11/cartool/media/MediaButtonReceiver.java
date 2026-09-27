package com.c11.cartool.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * 标准媒体按键接收器：把方向盘 / 蓝牙的 {@code MEDIA_BUTTON} 转发给当前播放器控制器。
 * 在 Manifest 注册（action {@code android.intent.action.MEDIA_BUTTON}）。
 *
 * <p>仅在 App 前台、已注入 {@link MediaHub} 时转发；零跑方控是否走标准媒体路由需真机验证。
 */
public class MediaButtonReceiver extends BroadcastReceiver {

    private static volatile MediaHub activeHub;

    public static void setActiveHub(MediaHub hub) {
        activeHub = hub;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || activeHub == null) return;
        if (event.getAction() == KeyEvent.ACTION_UP) activeHub.dispatchButton(event);
    }
}
