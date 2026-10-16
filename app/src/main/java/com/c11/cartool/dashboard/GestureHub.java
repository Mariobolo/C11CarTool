package com.c11.cartool.dashboard;

import android.content.Context;
import android.view.MotionEvent;

/**
 * [v0.3.19] 车机手势层：双指水平滑动识别器（与单指 3D 手势共存，供分屏/容器使用）。
 *
 * <p>设计参照跑跑桌面三分屏手势（双指左滑进分屏 / 双指右滑回主屏），
 * 适配到本项目 View 体系：宿主在 {@code dispatchTouchEvent} 中调用 {@link #onTouch(MotionEvent)}，
 * 消费双指手势（返回 true），其余事件照常交给下级（单指左滑开 3D 等不受影响）。
 *
 * <p>判定规则（防与纵向滚动冲突）：
 * <ul>
 *   <li>双指水平位移主导：{@code |dx| > 阈值} 且 {@code |dx| > 2*|dy|}（水平滑动手势）；</li>
 *   <li>阈值为 {@value #THRESHOLD_DP}dp（车机触摸，取偏大值防误触）；</li>
 *   <li>只在手指抬起/移出时做最终判定，移动过程不消费事件。</li>
 * </ul>
 *
 * <p>v0.3.19 接线：双指左滑暂时打开 3D 车模界面（占位动作）；
 * v0.3.20 分屏页上线后改接"进入分屏"，双指右滑改接"返回主界面"。
 */
public class GestureHub {

    /** 双指水平位移阈值（dp）。 */
    private static final int THRESHOLD_DP = 60;

    /** 手势监听（宿主实现具体动作）。 */
    public interface Listener {
        /** 双指左滑（v0.3.20 接分屏进入；当前由宿主决定占位动作）。 */
        void onTwoFingerSwipeLeft(float velocityX);

        /** 双指右滑（v0.3.20 接分屏返回；当前由宿主决定占位动作）。 */
        void onTwoFingerSwipeRight(float velocityX);
    }

    private final Listener listener;
    private final float thresholdPx;

    /** 双指手势跟踪状态：-1=未激活，>=0=双指按下时的平均起点。 */
    private boolean twoFingerTracking = false;
    private float downX, downY;

    public GestureHub(Context ctx, Listener l) {
        this.listener = l;
        this.thresholdPx = Math.round(THRESHOLD_DP * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * 在宿主 {@code dispatchTouchEvent} 中调用。
     *
     * @return true 表示该事件已被双指手势消费（宿主应短路，不再下发）。
     */
    public boolean onTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 第二指按下且此前已有一指 → 进入双指跟踪（记录平均起点）
                if (ev.getPointerCount() >= 2 && !twoFingerTracking) {
                    twoFingerTracking = true;
                    downX = avgX(ev);
                    downY = avgY(ev);
                }
                return false;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // 双指中一指抬起：若位移足够且水平主导，触发对应滑动手势
                if (twoFingerTracking && ev.getPointerCount() >= 2) {
                    finishGesture(avgX(ev), avgY(ev), 0);
                }
                return twoFingerTracking;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (twoFingerTracking) {
                    finishGesture(avgX(ev), avgY(ev), 0);
                }
                return false;
            }
            default:
                return false;
        }
    }

    private void finishGesture(float endX, float endY, float velocityX) {
        twoFingerTracking = false;
        float dx = endX - downX;
        float dy = endY - downY;
        // 水平主导：|dx| 超过阈值且明显大于 |dy|
        if (Math.abs(dx) < thresholdPx) return;
        if (Math.abs(dx) <= Math.abs(dy) * 2f) return;
        if (listener == null) return;
        if (dx < 0) listener.onTwoFingerSwipeLeft(velocityX);
        else listener.onTwoFingerSwipeRight(velocityX);
    }

    private static float avgX(MotionEvent ev) {
        float sum = 0f;
        for (int i = 0; i < ev.getPointerCount(); i++) sum += ev.getX(i);
        return sum / ev.getPointerCount();
    }

    private static float avgY(MotionEvent ev) {
        float sum = 0f;
        for (int i = 0; i < ev.getPointerCount(); i++) sum += ev.getY(i);
        return sum / ev.getPointerCount();
    }
}
