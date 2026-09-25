package com.c11.cartool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

/**
 * 一键诊断（前端直接运行，不依赖工程模式）。
 *
 * <p>一次上机尽量采全：权限级别 / ADB 连接与密钥指纹 / 设备信息 / 多网卡 IP /
 * Web 服务状态与自检 / 端口监听 / SELinux 与近期 avc / shell 通道自测 / 关键服务包。
 * 每条命令独立超时、失败不中断，结果汇总为文本（弹窗 + 可导出）。
 */
public final class DiagnosticRunner {

    public interface Callback { void onDone(String report); }

    private DiagnosticRunner() {}

    public static void run(final Context ctx, final WebServer web, final Callback cb) {
        Sh.submitAsync(new Runnable() {
            @Override public void run() {
                StringBuilder s = new StringBuilder();
                line(s, "# C11 车控 · 一键诊断");
                line(s, "# 时间: " + new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                line(s, "# App: " + AppInfo.TITLE + " (versionCode " + AppInfo.VERSION_CODE + ")");
                gap(s);

                section(s, "1. 权限与 ADB");
                line(s, "权限级别: " + Sh.getPermissionLabel());
                line(s, "ADB 已连接: " + Sh.isAdbConnected());
                line(s, "ADB 密钥指纹: " + Sh.getAdbKeyFingerprint());
                gap(s);

                section(s, "2. 设备信息");
                addCmd(s, "型号", "getprop ro.product.model");
                addCmd(s, "Android", "getprop ro.build.version.release");
                addCmd(s, "构建号", "getprop ro.build.display.id");
                gap(s);

                section(s, "3. 网络（多网卡 IPv4）");
                enumerateIps(s);
                line(s, "对外地址(wlan0): " + WebServer.getDeviceIp());
                gap(s);

                section(s, "4. Web 服务（手机扫码测控）");
                if (web != null) {
                    line(s, "运行: " + web.isRunning() + "，端口: " + web.getPort());
                    line(s, "本机自检: " + (web.isRunning() && web.selfTest()));
                } else {
                    line(s, "WebServer 未初始化");
                }
                gap(s);

                section(s, "5. 端口监听（关注 5555=adbd 与 Web 端口）");
                addCmd(s, "LISTEN 端口", "netstat -an 2>/dev/null | grep LISTEN");
                gap(s);

                section(s, "6. SELinux");
                addCmd(s, "模式", "getenforce");
                addCmd(s, "近期 avc", "logcat -d 2>/dev/null | grep -i avc | tail -n 15");
                gap(s);

                section(s, "7. shell 通道自测（读空调开关，验证 uid2000）");
                addCmd(s, "strCarAirSwitch", "settings get global strCarAirSwitch");
                gap(s);

                section(s, "8. 关键服务包是否存在");
                addCmd(s, "相关包", "pm list packages 2>/dev/null | grep -E 'leapmotor|iflytek|rightware'");
                gap(s);

                final String report = s.toString();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { cb.onDone(report); }
                });
            }
        });
    }

    private static void addCmd(StringBuilder s, String label, String cmd) {
        Sh.Result r = Sh.run(cmd, 8000);
        String out = (r == null || r.out == null) ? "" : r.out.trim();
        String err = (r == null || r.err == null) ? "" : r.err.trim();
        line(s, label + ": " + (out.isEmpty() ? "(空)" : out)
                + (err.isEmpty() ? "" : "  | err=" + err)
                + "  [exit=" + (r == null ? "-" : r.exit) + "]");
    }

    private static void enumerateIps(StringBuilder s) {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni == null || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        line(s, ni.getName() + ": " + a.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            line(s, "枚举网卡失败: " + e.getMessage());
        }
    }

    private static void section(StringBuilder s, String t) {
        line(s, "── " + t + " ──");
    }

    private static void gap(StringBuilder s) { s.append('\n'); }

    private static void line(StringBuilder s, String t) { s.append(t).append('\n'); }
}
