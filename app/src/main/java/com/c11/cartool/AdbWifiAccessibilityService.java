package com.c11.cartool;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 无障碍服务：自动点击零跑系统 Demo 软件的 "turn on adb wifi" 按钮
 *
 * 原理：
 * 1. 监听窗口状态变化
 * 2. 当检测到 com.leapmotor.system.demo.DemoActivity 窗口时
 * 3. 找到 resource-id 为 "turn_on_adb_wifi_btn" 的按钮
 * 4. 自动执行点击
 * 5. 点击成功后延迟2秒，发送广播通知完成
 *
 * 为什么需要这个服务：
 * persist.sys.leap.wifiadb 属性需要 system 权限才能设置，
 * 普通应用无法直接调用 SystemProperties.set()。
 * 零跑系统自带的 Demo 软件（com.leapmotor.system）运行在 system 权限下，
 * 其 "turn on adb wifi" 按钮会调用 SystemProperties.set("persist.sys.leap.wifiadb", "1")。
 * 因此只能通过启动 Demo 软件并模拟点击按钮的方式来开启 WiFi ADB。
 */
public class AdbWifiAccessibilityService extends AccessibilityService {

    private static final String TAG = "AdbWifiAutoClick";

    /** 零跑系统 Demo 软件包名 */
    private static final String TARGET_PACKAGE = "com.leapmotor.system";

    /** Demo Activity 类名 */
    private static final String TARGET_ACTIVITY = "com.leapmotor.system.demo.DemoActivity";

    /** "turn on adb wifi" 按钮的 resource-id */
    private static final String BTN_TURN_ON_ADB_WIFI = "com.leapmotor.system:id/turn_on_adb_wifi_btn";

    /** 完成广播 Action */
    public static final String ACTION_ADB_WIFI_CLICKED = "com.c11.cartool.ADB_WIFI_CLICKED";

    /** 是否正在执行自动点击（防止重复点击） */
    private boolean isClicking = false;

    /** 点击超时时间（毫秒） */
    private static final long CLICK_TIMEOUT = 10000;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();

        if (packageName == null || className == null) {
            return;
        }

        // 只处理目标包名和 Activity
        if (!TARGET_PACKAGE.contentEquals(packageName)) {
            return;
        }

        Log.i(TAG, "Window state changed: " + packageName + "/" + className);

        // 延迟一点等待界面完全加载
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tryClickAdbWifiButton();
            }
        }, 500);
    }

    /**
     * 尝试找到并点击 "turn on adb wifi" 按钮
     */
    private void tryClickAdbWifiButton() {
        if (isClicking) {
            Log.d(TAG, "Already clicking, skip");
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "Root node is null");
            return;
        }

        // 通过 resource-id 查找按钮
        AccessibilityNodeInfo button = findButtonById(rootNode, BTN_TURN_ON_ADB_WIFI);

        if (button == null) {
            // 如果 resource-id 找不到，尝试通过文本查找
            button = findButtonByText(rootNode, "turn on adb wifi");
        }

        if (button == null) {
            Log.w(TAG, "Button not found, trying recursive search...");
            // 最后尝试：递归查找所有可点击的按钮，看文本是否包含 "adb wifi"
            button = findButtonByTextContains(rootNode, "adb wifi");
        }

        if (button != null) {
            isClicking = true;
            Log.i(TAG, "Found button, performing click...");

            // 执行点击
            boolean clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            if (clicked) {
                Log.i(TAG, "Click successful!");
                // 点击成功后，延迟2秒发送完成广播
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(ACTION_ADB_WIFI_CLICKED);
                        intent.putExtra("success", true);
                        sendBroadcast(intent);
                        Log.i(TAG, "Sent completion broadcast");
                        isClicking = false;
                    }
                }, 2000);
            } else {
                Log.e(TAG, "Click failed!");
                isClicking = false;
            }
        } else {
            Log.e(TAG, "Button not found after all attempts");
        }
    }

    /**
     * 通过 resource-id 查找节点
     */
    private AccessibilityNodeInfo findButtonById(AccessibilityNodeInfo root, String viewId) {
        if (root == null) {
            return null;
        }
        try {
            return root.findAccessibilityNodeInfosByViewId(viewId).stream()
                    .filter(node -> node.isClickable() || node.isEnabled())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            Log.e(TAG, "findButtonById error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过精确文本查找节点
     */
    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo root, String text) {
        if (root == null) {
            return null;
        }
        try {
            return root.findAccessibilityNodeInfosByText(text).stream()
                    .filter(node -> node.isClickable() || node.isEnabled())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            Log.e(TAG, "findButtonByText error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过包含文本递归查找节点
     */
    private AccessibilityNodeInfo findButtonByTextContains(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return null;
        }

        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase())) {
            if (node.isClickable()) {
                return node;
            }
            // 如果文本节点不可点击，找它的可点击父节点
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) {
                    return parent;
                }
                parent = parent.getParent();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findButtonByTextContains(node.getChild(i), text);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
        isClicking = false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected");
    }
}
