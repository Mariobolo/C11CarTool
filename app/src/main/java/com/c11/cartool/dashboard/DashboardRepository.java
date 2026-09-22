package com.c11.cartool.dashboard;

import com.c11.cartool.Logger;
import com.c11.cartool.Sh;
import com.c11.cartool.vehicle.LogcatVehicleSource;

/**
 * 仪表盘数据采集器（单一后台线程，5s 一轮）。
 *
 * <p>每个周期执行两条 ADB 命令并解析，聚合为 {@link DashboardSnapshot} 回调主线程：
 * <ul>
 *   <li>{@code settings list global}：一次拿全部真机键，Java 端按白名单解析（不在 ADB 流上跑复杂管道）；</li>
 *   <li>{@code logcat -d -v brief -s <核心TAG>}：交给 {@link LogcatVehicleSource#parse} 解析高频实时信号。</li>
 * </ul>
 *
 * <p>ADB 未连接时不采集（快照标记未连接，UI 引导连接），避免无意义命令。
 */
public class DashboardRepository {

    public interface Callback {
        void onSnapshot(DashboardSnapshot snap);
    }

    private static final String TAG = "DashRepo";
    private static final long INTERVAL_MS = 5000;

    private static final String[] LOG_TAGS = {
            "C11CarSomeIp", "C11CarXml", "zza", "LocationDataC23Handler", "C11SmartContrl"
    };

    private static final java.util.regex.Pattern TEMP_RE =
            java.util.regex.Pattern.compile("([\\d.]+)°C");

    private final Callback callback;
    private final android.os.Handler mainHandler;
    private Thread thread;
    private volatile boolean running = false;

    public DashboardRepository(android.os.Handler main, Callback cb) {
        this.mainHandler = main;
        this.callback = cb;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "dashboard-refresh");
        thread.setDaemon(true);
        thread.start();
        Logger.info(TAG, "仪表盘数据采集启动（5s/轮）");
    }

    public synchronized boolean isRunning() { return running; }

    public synchronized void stop() {
        running = false;
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    public void requestRefresh() {
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                long t0 = System.currentTimeMillis();
                collectOnce();
                long cost = System.currentTimeMillis() - t0;
                long sleep = INTERVAL_MS - cost;
                if (sleep > 0) Thread.sleep(sleep);
            } catch (InterruptedException e) {
                // 收到中断 = 触发一次立即采集（车控后主动刷新）
                if (running) {
                    try { collectOnce(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Logger.warn(TAG, "采集异常: " + e.getMessage());
                try { Thread.sleep(INTERVAL_MS); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void collectOnce() {
        DashboardSnapshot snap = new DashboardSnapshot();
        snap.ts = System.currentTimeMillis();

        if (!Sh.isAdbConnected()) {
            snap.adbConnected = false;
            snap.adbUid = Sh.getAdbUid();
            deliver(snap);
            return;
        }
        snap.adbConnected = true;
        snap.adbUid = Sh.getAdbUid();

        // ── settings list global：一次命令，Java 端解析 ──
        Sh.Result s = Sh.run("settings list global", 10000);
        if (s.ok() && s.out != null) {
            parseSettings(s.out, snap);
            snap.settingsOk = true;
        } else {
            Logger.warn(TAG, "settings list global 失败: " + (s.err == null ? "" : s.err));
            snap.settingsOk = false;
        }

        // ── logcat：核心 TAG 一次性拉取解析 ──
        String tags = join(" ", LOG_TAGS);
        Sh.Result l = Sh.run("logcat -d -v brief -s " + tags, 10000);
        if (l.ok() && l.out != null && !l.out.trim().isEmpty()) {
            LogcatVehicleSource.Result pr = LogcatVehicleSource.parse(l.out);
            parseLogcat(pr, snap);
            snap.logcatOk = true;
        } else {
            Logger.debug(TAG, "logcat 无输出或失败（缓冲可能已刷掉）");
            snap.logcatOk = false;
        }

        deliver(snap);
    }

    /** settings list global 输出 "key=value" 逐行解析（白名单） */
    private void parseSettings(String text, DashboardSnapshot snap) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || !t.contains("=")) continue;
            int i = t.indexOf('=');
            String key = t.substring(0, i).trim();
            String val = t.substring(i + 1).trim();
            if (val.isEmpty()) continue;

            int iv;
            try { iv = (int) Math.floor(Double.parseDouble(val)); }
            catch (Exception e) { continue; }

            switch (key) {
                case "strCarAirSwitch":     snap.acSwitch = iv; break;
                case "strCarAirWind":       snap.fanSpeed = iv; break;
                case "strCarAirInner":      snap.innerCycle = iv; break;
                case "strCarFrontDefrost":  snap.frontDefrost = iv; break;
                case "strCarRearDefrost":   snap.rearDefrost = iv; break;
                case "strCar1409":          snap.driverTempHalf = iv; break;
                case "strCar1410":          snap.passengerTempHalf = iv; break;
                case "strCarVehicleLock":   snap.vehicleLock = iv; break;
                case "strCarChildLock":     snap.childLock = iv; break;
                case "strCarWindowForbit":  snap.windowForbit = iv; break;
                case "strCarMirrorHeart":   snap.mirrorHeat = iv; break;
                case "strCarPm25":          snap.pm25 = iv; break;
                case "C11_MUSIC":           snap.musicVol = iv; break;
                case "C11_NAVI":            snap.naviVol = iv; break;
                case "C11_SPEECH":          snap.speechVol = iv; break;
                case "C11_CALL":            snap.callVol = iv; break;
                default: break;
            }
        }
    }

    /** 把 LogcatVehicleSource 解析结果映射进快照 */
    private void parseLogcat(LogcatVehicleSource.Result r, DashboardSnapshot snap) {
        snap.batterySoc = intOf(xml(r, "figure"), intOf(event(r, 3162), -1), -1);
        snap.voltage = floatOf(event(r, 3130), -1);
        snap.current = floatOf(event(r, 3131), -1);
        snap.speedKmh = intOf(xml(r, "speed"), intOf(event(r, 1108), -1), -1);
        snap.outsideTemp = intOf(event(r, 33110), -1);

        String g = xml(r, "gear");
        if (g.isEmpty()) g = meaningOf(r, 1110);
        if (!g.isEmpty()) snap.gear = g;

        snap.rangeStd = intOf(xml(r, "rangeStandard"), -1);
        snap.rangeDyn = intOf(xml(r, "rangeDynamic"), -1);

        if (!r.tireStates.isEmpty()) {
            for (int pos = 0; pos < 4; pos++) {
                LogcatVehicleSource.State st = r.tireStates.get(pos);
                if (st == null) continue;
                try {
                    snap.tirePressKpa[pos] = (float) Math.floor(Double.parseDouble(st.raw));
                } catch (Exception ignored) {}
                // meaning 形如 "2.31bar 24°C"，提取温度
                if (st.meaning != null) {
                    java.util.regex.Matcher mm = TEMP_RE.matcher(st.meaning);
                    if (mm.find()) {
                        try { snap.tireTempC[pos] = (float) Math.floor(Double.parseDouble(mm.group(1))); }
                        catch (Exception ignored) {}
                    }
                }
            }
        }

        snap.doorStates[0] = intOf(event(r, 9123), -1); // 左前
        snap.doorStates[1] = intOf(event(r, 9124), -1); // 右前
        snap.doorStates[2] = intOf(event(r, 9125), -1); // 左后
        snap.doorStates[3] = intOf(event(r, 9126), -1); // 右后
        snap.doorStates[4] = intOf(event(r, 9127), -1); // 后备箱
        snap.doorStates[5] = intOf(event(r, 9128), -1); // 前机盖
    }

    private static String xml(LogcatVehicleSource.Result r, String field) {
        LogcatVehicleSource.State s = r.xmlStates.get(field);
        return s == null || s.raw == null ? "" : s.raw;
    }
    private static String event(LogcatVehicleSource.Result r, int id) {
        LogcatVehicleSource.State s = r.eventStates.get(id);
        return s == null || s.raw == null ? "" : s.raw;
    }
    private static String meaningOf(LogcatVehicleSource.Result r, int id) {
        LogcatVehicleSource.State s = r.eventStates.get(id);
        return s == null || s.meaning == null ? "" : s.meaning;
    }
    private static int intOf(String raw, int dft) {
        if (raw == null || raw.isEmpty()) return dft;
        try { return (int) Math.floor(Double.parseDouble(raw.trim())); } catch (Exception e) { return dft; }
    }
    private static int intOf(String raw, int secondary, int dft) {
        int a = intOf(raw, dft);
        if (a != dft) return a;
        return secondary;
    }
    private static float floatOf(String raw, float dft) {
        if (raw == null || raw.isEmpty()) return dft;
        try { return (float) Double.parseDouble(raw.trim()); } catch (Exception e) { return dft; }
    }

    private static String join(String sep, String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private void deliver(final DashboardSnapshot snap) {
        if (mainHandler != null && callback != null) {
            mainHandler.post(() -> callback.onSnapshot(snap));
        }
    }
}
