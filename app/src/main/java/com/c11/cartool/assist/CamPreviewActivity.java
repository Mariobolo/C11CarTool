package com.c11.cartool.assist;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * [v0.3.21] FCT 摄像头预览 demo 页（lp_around 反射客户端）
 *
 * <p>依据车机 FCT/cameraaround 反编译源码（{@code ILpCameraRender} AIDL 全签名 +
 * 事务码 + 调用序列）实现，Android 9 无可用 LMOS-API.jar（空壳），改走双模式：
 * <ul>
 *   <li>模式 A：反射 {@code ILpCameraRender.Stub.asInterface} 调 AIDL 方法（boot classpath 含该类时可用）；</li>
 *   <li>模式 B（默认）：Parcel transact 直发，事务码已确认，不依赖任何车机类。</li>
 * </ul>
 * 所有调用写 Logcat（Tag=CamPreview），实车跑一轮日志即可标定服务名 / 视角值 / 鉴权。
 *
 * <p>调用序列（源自 LpRenderSurfaceView）：addRender → updateDisplayId → updateTransform
 * → updateRenderArea(x,y,w,h) → updateRelativeLayer(surfaceHandle) → showRender；2s 心跳 heartRender。
 */
public class CamPreviewActivity extends Activity {

    private static final String TAG = "CamPreview";

    // ── lp_around AIDL 描述符与事务码（源自 FCT 反编译 ILpCameraRender.java） ──
    private static final String DESCRIPTOR = "com.leapmotor.camerarender.ILpCameraRender";
    private static final int T_addRender = 1, T_rmRender = 2, T_heartRender = 3,
            T_updateRenderArea = 4, T_updateRelativeLayer = 5, T_updateTransform = 6,
            T_updateScaling = 7, T_updateDisplayId = 8, T_showRender = 9, T_hideRender = 10,
            T_extension = 11, T_switchAroundModel = 12, T_switchAround2D = 13,
            T_switchAround3D = 14;

    /** 服务名候选：FCT 360 实际用 lp_around（LpCameraDefine.AROUND_SERVICE）；
     *  Define 另有 camera_around_service / camera_front_service。实车逐一试。 */
    private static final String[] SERVICES = {
            "lp_around", "camera_around_service", "camera_front_service"
    };

    /** RenderId=FCT(5) / Level=0（LpCameraDefine.RENDER_FCT_ID/LEVEL） */
    private static final int RENDER_ID = 5, LEVEL = 0;

    /** 视角候选（AvmAngle 数值在 framework vdex 未提取到，实车逐个试并看日志/画面） */
    private static final String[] ANGLES = {
            "前", "后", "左", "右", "轮", "3D", "俯视", "盲区A", "盲区B"
    };

    private SurfaceView surfaceView;
    private TextView statusView;
    private TextView qcLogView;   // [v0.3.28] QCarCam 结果回显
    private IBinder binder;           // 当前服务 binder
    private String serviceName = "";
    private Object renderApi;         // 模式 A 的 AIDL 代理（null 则用模式 B）
    private boolean useParcel = true;
    private Thread heartThread;
    private volatile boolean alive = false, shown = false;
    private volatile boolean connected = false;

    // ── 生命周期 ──

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        unlockHiddenApi();   // Android 9 解除 hidden API 限制（失败不影响后续）
        buildUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alive = false;
        if (heartThread != null) heartThread.interrupt();
        hideAndRemove();
    }

    // ── UI ──

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);

        // 顶栏：返回 + 标题 + 状态
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("← 返回", v -> finish());
        TextView title = new TextView(this);
        title.setText("  摄像头预览（lp_around）");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        statusView = new TextView(this);
        statusView.setText("  未连接");
        statusView.setTextSize(16);
        statusView.setTextColor(0xFFFFC107);
        head.addView(back);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(statusView);
        root.addView(head);

        // 预览 SurfaceView
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) {
                Log.i(TAG, "surfaceCreated");
                alive = true;
                initRender();
                if (shown) showRender();
                startHeart();
            }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {
                Log.i(TAG, "surfaceChanged " + w + "x" + ht);
                updateArea();
            }
            @Override public void surfaceDestroyed(SurfaceHolder h) {
                Log.i(TAG, "surfaceDestroyed");
                alive = false;
            }
        });
        root.addView(surfaceView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 控制区
        ScrollView sv = new ScrollView(this);
        LinearLayout ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.VERTICAL);

        // 行1：连接服务 / 显示 / 隐藏 / 移除渲染
        LinearLayout row1 = row();
        row1.addView(button("连接", v -> connect()));
        row1.addView(button("显示", v -> { shown = true; showRender(); }));
        row1.addView(button("隐藏", v -> { shown = false; hideRender(); }));
        row1.addView(button("移除", v -> rmRender()));
        ctl.addView(row1);

        // 行2：视角切换（候选值 0-8）
        LinearLayout row2 = row();
        for (int i = 0; i < ANGLES.length; i++) {
            final int angle = i;
            row2.addView(button(ANGLES[i], v -> switchAngle(angle)));
        }
        ctl.addView(row2);

        // 行3：服务名切换（A/B/C）+ 日志说明
        LinearLayout row3 = row();
        for (int i = 0; i < SERVICES.length; i++) {
            final int idx = i;
            row3.addView(button(SERVICES[i], v -> {
                serviceName = SERVICES[idx];
                binder = null;
                connected = false;
                status("服务名 → " + serviceName);
                connect();
            }));
        }
        ctl.addView(row3);

        TextView note = new TextView(this);
        note.setText("提示：连接后点视角按钮看画面；视角数值与鉴权结果见 logcat(CamPreview)，实车标定用。");
        note.setTextSize(14);
        note.setTextColor(0xFF9E9E9E);
        note.setPadding(dp(8), dp(6), dp(8), dp(6));
        ctl.addView(note);

        // [v0.3.28] QCarCam 原生直采（灵眸引擎：cam_enum/cam_probe/xp_capture + libais_client.so）
        ctl.addView(sectionTitle("QCarCam 直采（高通8155 原生，灵眸机制）"));
        qcLogView = new TextView(this);
        qcLogView.setTextSize(12);
        qcLogView.setTextColor(0xFFB0BEC5);
        qcLogView.setText("未运行");
        LinearLayout qcRow1 = row();
        qcRow1.addView(button("枚举input表", v -> bg("cam_enum", () ->
                QCarCamEngine.enumInputs(this))));
        qcRow1.addView(button("逐路开流标定", v -> bg("cam_probe --full（180s，勿急）", () ->
                QCarCamEngine.probeAll(this))));
        ctl.addView(qcRow1);
        LinearLayout qcRow2 = row();
        for (int cam = 0; cam < 4; cam++) {
            final int c = cam;
            qcRow2.addView(button("录cam" + c + " 5s", v -> bg("xp_capture --cam " + c, () -> {
                File f = new File(getFilesDir(), "qccam/out_cam" + c + ".uyvy");
                return QCarCamEngine.capture(this, c, f, 5, 30);
            })));
        }
        ctl.addView(qcRow2);
        TextView qcHint = smallNote("0:Front 1:Right 2:Rear 3:Left（零跑BSP可能重映射，以标定结果为准）；"
                + "UYVY 原始帧落 filesDir/qccam/out_camN.uyvy");
        ctl.addView(qcHint);
        ctl.addView(qcLogView);

        // [v0.3.28] 系统 AVM 控制（lmos.framework.core.cam.AvmCtrlManager 反射）
        ctl.addView(sectionTitle("AVM 控制（lmos 反射，实车标定参数）"));
        final TextView avmLog = new TextView(this);
        avmLog.setTextSize(12);
        avmLog.setTextColor(0xFFB0BEC5);
        avmLog.setText("未调用");
        LinearLayout avmRow1 = row();
        String[] angles = {"角0,0", "角1,0", "角2,0", "角3,0"};
        for (int i = 0; i < angles.length; i++) {
            final int a = i;
            avmRow1.addView(button(angles[i], v -> {
                boolean ok = AvmCtrlReflect.switchAngle(a, 0);
                avmLog.setText("switchAngle(" + a + ",0) = " + ok + "（详情见 logcat AvmCtrl）");
            }));
        }
        ctl.addView(avmRow1);
        LinearLayout avmRow2 = row();
        avmRow2.addView(button("rotate3D(0,0)", v -> {
            boolean ok = AvmCtrlReflect.rotate3D(0, 0);
            avmLog.setText("rotate3D(0,0) = " + ok);
        }));
        avmRow2.addView(button("enableTrack(true)", v -> {
            boolean ok = AvmCtrlReflect.enableTrack(true);
            avmLog.setText("enableTrack(true) = " + ok);
        }));
        avmRow2.addView(button("enableTs(true)", v -> {
            boolean ok = AvmCtrlReflect.enableTs(true);
            avmLog.setText("enableTs(true) = " + ok);
        }));
        ctl.addView(avmRow2);
        TextView avmHint = smallNote("AVM 走系统渲染（含鸟瞰/3D合成），与上方 QCarCam 直采互补；"
                + "参数值域未标定，实车逐个试并看画面/logcat(AvmCtrl)。");
        ctl.addView(avmHint);
        ctl.addView(avmLog);

        sv.addView(ctl);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView sectionTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(16);
        tv.setTextColor(0xFF80DEEA);
        tv.setPadding(dp(8), dp(14), dp(8), dp(4));
        return tv;
    }

    private TextView smallNote(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(12);
        tv.setTextColor(0xFF78909C);
        tv.setPadding(dp(8), dp(2), dp(8), dp(4));
        return tv;
    }

    /** 后台线程执行引擎调用，完成后把输出回显到 qcLogView（QCarCam 区）。 */
    private void bg(final String label, final Callable<QCarCamEngine.Result> task) {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Toast.makeText(this, "开始: " + label, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            QCarCamEngine.Result r;
            try { r = task.call(); }
            catch (Exception e) { r = new QCarCamEngine.Result(false, "", "异常: " + e.getMessage()); }
            final QCarCamEngine.Result rr = r;
            final String out = (rr.ok ? "[完成] " : "[失败] ") + label + "\n" + rr.detail();
            h.post(() -> {
                if (qcLogView != null) qcLogView.setText(out);
                Toast.makeText(this, label + (rr.ok ? " 完成" : " 失败（见结果区）"),
                        Toast.LENGTH_LONG).show();
            });
        }, "qccam-bg").start();
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setOnClickListener(l);
        return b;
    }

    // ── 服务连接与调用（双模式） ──

    /** 依次尝试候选服务名，绑定成功即用。 */
    private void connect() {
        if (serviceName.isEmpty()) serviceName = SERVICES[0];
        IBinder b = getService(serviceName);
        if (b == null) {
            status("服务不可达: " + serviceName);
            Log.e(TAG, "service null: " + serviceName);
            Toast.makeText(this, "服务不可达（见日志）", Toast.LENGTH_SHORT).show();
            return;
        }
        binder = b;
        connected = true;
        try { bindApi(); } catch (Exception e) { Log.w(TAG, "AIDL 反射失败，走 Parcel 模式", e); }
        status("已连接: " + serviceName + (useParcel ? " [Parcel]" : " [AIDL]"));
        Log.i(TAG, "connected " + serviceName + " parcel=" + useParcel + " binder=" + b);
        if (alive) { initRender(); if (shown) showRender(); }
    }

    /** 模式 A：反射 ILpCameraRender.Stub.asInterface(binder)。失败则保持 Parcel 模式。 */
    private void bindApi() throws Exception {
        Class<?> stub = Class.forName("com.leapmotor.camerarender.ILpCameraRender$Stub");
        Method asI = stub.getMethod("asInterface", IBinder.class);
        Object api = asI.invoke(null, binder);
        if (api != null) { renderApi = api; useParcel = false; }
    }

    /** 统一调用入口：优先模式 A（反射 AIDL），失败/无代理走模式 B（Parcel）。 */
    private void invoke(int code, String methodName, int[] ints, float[] floats, IBinder ib) {
        if (binder == null) { status("未连接"); return; }
        if (!useParcel && renderApi != null) {
            try {
                Object[] args = new Object[(ints == null ? 0 : ints.length)
                        + (floats == null ? 0 : floats.length) + (ib != null ? 1 : 0)];
                Class<?>[] types = new Class<?>[args.length];
                int n = 0;
                if (ints != null) for (int v : ints) { types[n] = int.class; args[n++] = v; }
                if (floats != null) for (float v : floats) { types[n] = float.class; args[n++] = v; }
                if (ib != null) { types[n] = IBinder.class; args[n] = ib; }
                renderApi.getClass().getMethod(methodName, types).invoke(renderApi, args);
                Log.i(TAG, "AIDL ok " + methodName);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "AIDL fail " + methodName + " → Parcel 重试", t);
            }
        }
        // 模式 B：Parcel transact（事务码已确认）
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESCRIPTOR);
            if (ints != null) for (int v : ints) d.writeInt(v);
            if (floats != null) for (float v : floats) d.writeFloat(v);
            if (ib != null) d.writeStrongBinder(ib);
            boolean ok = binder.transact(code, d, r, 0);
            r.readException();
            Log.i(TAG, "Parcel " + methodName + " -> " + ok);
        } catch (Throwable t) {
            Log.e(TAG, "Parcel fail " + methodName, t);
            status("调用失败: " + methodName);
        } finally {
            r.recycle();
            d.recycle();
        }
    }

    // ── 渲染调用序列 ──

    private void initRender() {
        Log.i(TAG, "initRender id=" + RENDER_ID);
        invoke(T_addRender, "addRender", new int[]{RENDER_ID, LEVEL}, null, null);
        invoke(T_updateDisplayId, "updateDisplayId", new int[]{RENDER_ID, 0}, null, null);
        invoke(T_updateTransform, "updateTransform", new int[]{RENDER_ID, 0}, null, null);
        updateArea();
        IBinder h = surfaceHandle();
        if (h != null) {
            invoke(T_updateRelativeLayer, "updateRelativeLayer",
                    new int[]{RENDER_ID}, null, h);
        } else {
            Log.w(TAG, "surfaceHandle null（@hide 反射失败或 Surface 未就绪）");
        }
    }

    private void updateArea() {
        if (surfaceView == null) return;
        int w = surfaceView.getMeasuredWidth();
        int h = surfaceView.getMeasuredHeight();
        int[] loc = new int[2];
        surfaceView.getLocationOnScreen(loc);
        Log.i(TAG, "updateArea x=" + loc[0] + " y=" + loc[1] + " w=" + w + " h=" + h);
        invoke(T_updateRenderArea, "updateRenderArea",
                new int[]{RENDER_ID, loc[0], loc[1], w, h}, null, null);
    }

    private void showRender() {
        invoke(T_showRender, "showRender", new int[]{RENDER_ID}, null, null);
    }

    private void hideRender() {
        invoke(T_hideRender, "hideRender", new int[]{RENDER_ID}, null, null);
    }

    private void rmRender() {
        invoke(T_rmRender, "rmRender", new int[]{RENDER_ID}, null, null);
    }

    private void switchAngle(int angle) {
        Log.i(TAG, "switchAngle " + angle + " (" + ANGLES[angle] + ")");
        invoke(T_switchAround2D, "switchAround2D", new int[]{RENDER_ID, angle}, null, null);
    }

    private void startHeart() {
        if (heartThread != null && heartThread.isAlive()) return;
        heartThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);
                    if (alive && connected) {
                        invoke(T_heartRender, "heartRender", new int[]{RENDER_ID}, null, null);
                    }
                } catch (InterruptedException e) { break; }
            }
        }, "cam-heart");
        heartThread.setDaemon(true);
        heartThread.start();
    }

    private void hideAndRemove() {
        if (binder != null) {
            try { hideRender(); rmRender(); } catch (Throwable ignored) {}
        }
    }

    // ── 反射工具 ──

    /** Android 9：解除 hidden API 限制（VMRuntime.setHiddenApiExemptions，失败忽略）。 */
    private static void unlockHiddenApi() {
        try {
            Class<?> vm = Class.forName("dalvik.system.VMRuntime");
            Object rt = vm.getMethod("getRuntime").invoke(null);
            vm.getMethod("setHiddenApiExemptions", String[].class)
                    .invoke(rt, (Object) new String[]{"L"});
            Log.i(TAG, "hidden api unlocked");
        } catch (Throwable t) {
            Log.w(TAG, "hidden api unlock 失败（继续尝试调用）", t);
        }
    }

    /** ServiceManager.getService（@hide，反射）。 */
    private IBinder getService(String name) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method m = sm.getMethod("getService", String.class);
            return (IBinder) m.invoke(null, name);
        } catch (Throwable t) {
            Log.e(TAG, "getService fail: " + name, t);
            return null;
        }
    }

    /** SurfaceView.getSurfaceControl().getHandle()（@hide，反射；需 Surface 已创建）。 */
    private IBinder surfaceHandle() {
        try {
            Method gsc = SurfaceView.class.getMethod("getSurfaceControl");
            Object sc = gsc.invoke(surfaceView);
            Method gh = sc.getClass().getMethod("getHandle");
            IBinder h = (IBinder) gh.invoke(sc);
            Log.i(TAG, "surfaceHandle ok " + h);
            return h;
        } catch (Throwable t) {
            Log.e(TAG, "surfaceHandle fail", t);
            return null;
        }
    }

    private void status(String s) {
        if (statusView != null) statusView.setText("  " + s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
