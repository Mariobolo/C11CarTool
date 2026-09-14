package com.c11.cartool;

/**
 * 设备信息检测
 */
public final class DeviceInfo {

    public final String model;
    public final String android;
    public final int sdk;
    public final String tags;
    public final String platform;
    public final String kernel;
    public final String seLinux;
    public final int uid;
    public final String uidStr;
    public final String patch;

    private DeviceInfo() {
        model    = prop("ro.product.model");
        android  = prop("ro.build.version.release");
        sdk      = intProp("ro.build.version.sdk");
        tags     = prop("ro.build.tags");
        platform = prop("ro.board.platform");
        kernel   = Sh.out("uname -r");
        seLinux  = Sh.out("getenforce");
        uid      = Sh.uid();
        uidStr   = Sh.whoami();
        patch    = prop("ro.build.version.security_patch");
    }

    public static DeviceInfo detect() { return new DeviceInfo(); }

    private static String prop(String key) {
        return Sh.out("getprop " + key);
    }

    private static int intProp(String key) {
        try { return Integer.parseInt(prop(key)); }
        catch (Exception e) { return -1; }
    }
}
