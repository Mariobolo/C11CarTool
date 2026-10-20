package com.c11.cartool.assist;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 通用无障碍服务（C11 功能辅助）。
 *
 * 职责（仅做“界面动作”，不含业务流程）：
 *  1) 开机自动点击系统 Demo 的 “turn on adb wifi”（窗口事件触发，需 adbAuto 开启）；
 *  2) 对外提供：按文本点击 / 按 viewId 片段（通配）点击 / 全局返回，供转向 360 等复用。
 *
 * 通信：静态实例 + 主线程请求队列；系统未开启服务时 instance=null，调用方先查 {@link #isReady}。
 */
public class C11AssistService extends AccessibilityService {

    private static final String TAG = "C11Assist";

    private static final String DEMO_PKG = "com.leapmotor.system";
    private static final String DEMO_ACT = "com.leapmotor.system.demo.DemoActivity";
    private static final String ADB_TEXT = "turn on adb wifi";

    private static volatile C11AssistService instance;
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface ResultCb { void onDone(boolean ok); }

    public static boolean isReady() { return instance != null; }

    /** 请求：按文本点击（文本/描述包含该短语的可点击控件）。 */
    public static void clickText(final String text, final ResultCb cb) {
        final C11AssistService s = instance;
        if (s == null) { if (cb != null) cb.onDone(false); return; }
        s.main.post(() -> s.doClickText(text, cb));
    }

    /** 请求：按 viewId 片段（通配，如 rear_btn）点击。 */
    public static void clickId(final String idPart, final ResultCb cb) {
        final C11AssistService s = instance;
        if (s == null) { if (cb != null) cb.onDone(false); return; }
        s.main.post(() -> s.doClickId(idPart, cb));
    }

    /** 请求：全局返回。 */
    public static void globalBack(final ResultCb cb) {
        final C11AssistService s = instance;
        if (s == null) { if (cb != null) cb.onDone(false); return; }
        s.main.post(() -> {
            boolean ok = s.performGlobalAction(GLOBAL_ACTION_BACK);
            if (cb != null) cb.onDone(ok);
        });
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;

        // [v0.3.22] ADB 授权弹窗自动确认：网络/USB 调试每次连接都会弹，
        // 自动点“始终允许”让车机记住（弹窗包名/类名以实车 logcat 标定为准）。
        // [v0.3.27] 按钮文本中英文双匹配（车机系统可能英文/中英混用）。
        if (new AssistConfig(this).adbAuthAuto() && isAuthDialog(pkg, cls)) {
            Log.i(TAG, "检测到 ADB 授权弹窗: " + pkg + "/" + cls);
            main.postDelayed(() -> {
                doClickText("始终允许", ok -> {
                    if (ok) { Log.i(TAG, "授权自动确认 成功(始终允许)"); return; }
                    doClickText("always allow", ok2 -> {
                        if (ok2) { Log.i(TAG, "授权自动确认 成功(Always allow)"); return; }
                        doClickText("允许", ok3 -> {
                            if (ok3) { Log.i(TAG, "授权自动确认 成功(允许)"); return; }
                            doClickText("allow", ok4 -> Log.i(TAG, "授权自动确认 "
                                    + (ok4 ? "成功(Allow)" : "失败，未见按钮（弹窗文本/类名待标定）")));
                        });
                    });
                });
            }, 800);
            return;
        }

        if (!DEMO_PKG.contentEquals(pkg) || !DEMO_ACT.contentEquals(cls)) return;

        // 仅当用户开启“开机自动 ADB”时自动点击
        if (!new AssistConfig(this).adbAuto()) return;
        Log.i(TAG, "检测到 Demo 窗口，延迟点击 ADB 开关");
        main.postDelayed(() -> doClickText(ADB_TEXT, ok ->
                Log.i(TAG, "ADB 开关自动点击 " + (ok ? "成功" : "失败"))), 500);
    }

    /** ADB 授权弹窗识别：systemui/settings 的 AdbAuthorize/UsbConfirm 类窗口（含零跑定制变体）。 */
    private boolean isAuthDialog(CharSequence pkg, CharSequence cls) {
        String p = pkg.toString().toLowerCase();
        String c = cls.toString().toLowerCase();
        boolean pkgHit = p.contains("systemui") || p.contains("settings")
                || p.contains("dialog");
        boolean clsHit = c.contains("adb") || c.contains("usb")
                || c.contains("authorize") || c.contains("confirm");
        Log.d(TAG, "auth dialog? pkg=" + p + " cls=" + c + " => " + (pkgHit && clsHit));
        return pkgHit && clsHit;
    }

    // ── 界面动作实现（均在主线程）──

    private void doClickText(String text, ResultCb cb) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo target = root == null ? null
                : findByText(root, text.toLowerCase());
        boolean ok = target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i(TAG, "clickText '" + text + "' => " + ok);
        if (cb != null) cb.onDone(ok);
    }

    private void doClickId(String idPart, ResultCb cb) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo target = root == null ? null : findByIdPart(root, idPart);
        boolean ok = target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i(TAG, "clickId '" + idPart + "' => " + ok);
        if (cb != null) cb.onDone(ok);
    }

    /** 递归找文本/描述包含短语的节点，命中后上溯可点击祖先。 */
    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String needle) {
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        boolean hit = (t != null && t.toString().toLowerCase().contains(needle))
                || (d != null && d.toString().toLowerCase().contains(needle));
        if (hit) {
            AccessibilityNodeInfo c = clickableOf(node);
            if (c != null) return c;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByText(node.getChild(i), needle);
            if (r != null) return r;
        }
        return null;
    }

    /** 递归找 viewIdResourceName 含片段的可点击节点（即 *xxx* 通配语义）。 */
    private AccessibilityNodeInfo findByIdPart(AccessibilityNodeInfo node, String part) {
        String id = node.getViewIdResourceName();
        if (id != null && id.contains(part)) {
            AccessibilityNodeInfo c = clickableOf(node);
            if (c != null) return c;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByIdPart(node.getChild(i), part);
            if (r != null) return r;
        }
        return null;
    }

    /** 节点自身或其可点击祖先（限 10 层）。 */
    private AccessibilityNodeInfo clickableOf(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    @Override
    public void onInterrupt() { Log.w(TAG, "无障碍被中断"); }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
