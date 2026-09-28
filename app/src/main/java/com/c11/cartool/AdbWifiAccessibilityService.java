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

    /** 点击任务（可去重：新事件到来时先撤掉未执行的旧任务） */
    private final Runnable clickTask = new Runnable() {
        @Override public void run() { tryClickAdbWifiButton(); }
    };

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
        // [FIX] 同时校验目标 Activity（原注释声称校验但代码没做）：
        // com.leapmotor.system 其它窗口不应触发自动点击
        if (!TARGET_ACTIVITY.contentEquals(className)) {
            return;
        }

        Log.i(TAG, "Window state changed: " + packageName + "/" + className);

        // [FIX] 延迟一点等待界面完全加载；先撤掉未执行的旧任务，避免连环点击
        handler.removeCallbacks(clickTask);
        handler.postDelayed(clickTask, 500);
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
            // 如果 resource-id 找不到，尝试通过文本查找（完整文案匹配）
            button = findButtonByText(rootNode, "turn on adb wifi");
        }

        if (button == null) {
            Log.w(TAG, "Button not found, trying recursive search...");
            // 最后尝试：递归查找文本包含完整短语 "turn on adb wifi" 的可点击控件。
            // [FIX] 严禁宽泛 contains("adb wifi")：会把 "turn off adb wifi" 点掉（反向操作）
            button = findButtonByTextContains(rootNode, "turn on adb wifi");
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
                        intent.setPackage(getPackageName());  // [FIX] 显式定向本应用，不发隐式广播
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
            // [FIX] 只接受可点击节点（原 isEnabled() 对几乎所有节点为真，形同虚设）；
            // 命中文本节点时向上找可点击祖先
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(viewId)) {
                AccessibilityNodeInfo click = clickableOf(node);
                if (click != null) return click;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "findButtonById error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过完整文本查找节点（大小写不敏感、整句相等，避免子串误命中）
     */
    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo root, String text) {
        if (root == null) {
            return null;
        }
        try {
            // 注意：findAccessibilityNodeInfosByText 本身是“不区分大小写的包含匹配”，
            // 这里再校验整句相等才是真正的精确查找
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                CharSequence t = node.getText();
                if (t != null && text.equalsIgnoreCase(t.toString().trim())) {
                    AccessibilityNodeInfo click = clickableOf(node);
                    if (click != null) return click;
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "findButtonByText error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过包含文本递归查找节点（text 应为完整目标短语，如 "turn on adb wifi"）
     */
    private AccessibilityNodeInfo findButtonByTextContains(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return null;
        }

        String needle = text.toLowerCase();
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        boolean hit = (nodeText != null && nodeText.toString().toLowerCase().contains(needle))
                || (nodeDesc != null && nodeDesc.toString().toLowerCase().contains(needle));
        if (hit) {
            AccessibilityNodeInfo click = clickableOf(node);
            if (click != null) return click;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findButtonByTextContains(node.getChild(i), text);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * 返回节点自身或其可点击祖先（限探 10 层）；都不可点击则返回 null
     */
    private static AccessibilityNodeInfo clickableOf(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    @Override
    public void onDestroy() {
        // [FIX] 清理滞留回调，服务销毁后不再执行点击任务
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
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
