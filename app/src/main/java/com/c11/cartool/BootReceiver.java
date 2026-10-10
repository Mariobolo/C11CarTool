package com.c11.cartool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.c11.cartool.assist.BootAutomationService;

/**
 * 开机广播接收器：开机后启动 {@link BootAutomationService} 顺序执行自动化。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.i(TAG, "收到开机广播，启动自动化服务");
        Intent svc = new Intent(context, BootAutomationService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
        else context.startService(svc);
    }

    /** 启动零跑系统 Demo（供自动化服务复用）。 */
    public static boolean startDemoActivity(Context context) {
        try {
            Intent i = new Intent();
            i.setClassName("com.leapmotor.system", "com.leapmotor.system.demo.DemoActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
            Log.i(TAG, "已启动系统 Demo");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "启动 Demo 失败: " + e.getMessage());
            return false;
        }
    }
}
