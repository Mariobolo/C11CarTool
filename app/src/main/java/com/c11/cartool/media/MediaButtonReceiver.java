package com.c11.cartool.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * 标准媒体按键接收器：把方向盘 / 蓝牙的 {@code MEDIA_BUTTON} 转发给当前播放器控制器。
 * 在 Manifest 注册（action {@code android.intent.action.MEDIA_BUTTON}）。
 *
 * <p>⚠️ 当前 {@link #setActiveHub} 尚无调用方（链路未接通），转发不会发生；
 * 接线时需补前台/来源校验（exported receiver 可被第三方 App 伪造按键）。
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
