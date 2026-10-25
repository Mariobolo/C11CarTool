package com.c11.cartool.assist;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * [v0.3.28] 系统 AVM 控制反射（灵眸 DVR 机制）。
 *
 * <p>零跑 framework 层提供 {@code lmos.framework.core.cam.AvmCtrlManager}（boot classpath），
 * 普通应用可反射取单例并调用：
 * <ul>
 *   <li>{@code switchAngle(int,int)} — 切换 360 视角（前后左右/环视，参数值域待实车标定）；</li>
 *   <li>{@code rotate3D(int,int)} — 3D 车模旋转；</li>
 *   <li>{@code enableTrack(..)} / {@code enableTs(..)} — 轨迹线/透明底盘。</li>
 * </ul>
 * 与 QCarCam 直采互补：AVM 走系统渲染（含标定鸟瞰/3D 合成），直采走原生摄像头流。
 * 全部调用写 Logcat（Tag=AvmCtrl），实车跑一轮即可标定参数。
 */
public final class AvmCtrlReflect {

    private static final String TAG = "AvmCtrl";
    private static final String CLS = "lmos.framework.core.cam.AvmCtrlManager";

    private static volatile Object sInst;
    private static volatile boolean sFailed;

    private AvmCtrlReflect() {}

    private static synchronized Object instance() {
        if (sFailed) return null;
        if (sInst != null) return sInst;
        try {
            Class<?> c = Class.forName(CLS);
            Method m = c.getMethod("getInstance");
            sInst = m.invoke(null);
            if (sInst == null) { Log.w(TAG, CLS + ".getInstance() 返回 null"); }
            else Log.i(TAG, "AvmCtrlManager 实例 OK: " + sInst);
        } catch (Throwable t) {
            sFailed = true;
            Log.w(TAG, "取 AvmCtrlManager 失败（车机版本可能无此类）: " + t);
        }
        return sInst;
    }

    /** 反射调用任意方法：方法名 + 参数（自动装箱 int/boolean/float/String）。 */
    private static boolean invoke(String name, Object... args) {
        Object inst = instance();
        if (inst == null) { Log.w(TAG, name + " 跳过：没有实例"); return false; }
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof Integer) types[i] = int.class;
                else if (a instanceof Boolean) types[i] = boolean.class;
                else if (a instanceof Float) types[i] = float.class;
                else types[i] = a.getClass();
            }
            Method m = inst.getClass().getMethod(name, types);
            Object r = m.invoke(inst, args);
            Log.i(TAG, name + ArraysStr(args) + " = " + r);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, name + " 调用失败: " + t);
            return false;
        }
    }

    private static String ArraysStr(Object[] a) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(","); sb.append(a[i]); }
        return sb.append(")").toString();
    }

    /** 切换 360 视角：参数 1=视角枚举（待标定），参数 2=0/1 开合？ */
    public static boolean switchAngle(int angle, int opt) { return invoke("switchAngle", angle, opt); }

    /** 3D 旋转（方位/俯仰，待标定）。 */
    public static boolean rotate3D(int x, int y) { return invoke("rotate3D", x, y); }

    /** 轨迹线开关（待标定签名）。 */
    public static boolean enableTrack(Object... args) { return invoke("enableTrack", args); }

    /** 透明底盘/上帝视角开关（待标定签名）。 */
    public static boolean enableTs(Object... args) { return invoke("enableTs", args); }
}
