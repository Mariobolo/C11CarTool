package com.c11.cartool.assist;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 功能辅助配置（持久化）。
 * 单一职责：只读写“功能辅助页”的开关与选择，不含任何执行逻辑。
 */
public final class AssistConfig {

    private static final String FILE = "c11_assist";

    /** display 编号（真机三联屏，已确认：0=中控、1=副驾、2=仪表）。 */
    public static final int DISPLAY_SUB = 1;
    public static final int DISPLAY_DASH = 2;

    private final SharedPreferences sp;

    public AssistConfig(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ── 开机自动化 ──
    public boolean adbAuto()           { return sp.getBoolean("adb_auto", false); }
    public void setAdbAuto(boolean v)  { sp.edit().putBoolean("adb_auto", v).apply(); }

    public boolean shizuku()           { return sp.getBoolean("shizuku", false); }
    public void setShizuku(boolean v)  { sp.edit().putBoolean("shizuku", v).apply(); }

    // ── 多屏开机启动：副屏 ──
    public boolean subScreen()         { return sp.getBoolean("sub_screen", false); }
    public void setSubScreen(boolean v){ sp.edit().putBoolean("sub_screen", v).apply(); }
    public String subPkg()             { return sp.getString("sub_pkg", ""); }
    public String subAct()             { return sp.getString("sub_act", ""); }
    public void setSubTarget(String pkg, String act) {
        sp.edit().putString("sub_pkg", pkg).putString("sub_act", act).apply();
    }

    // ── 多屏开机启动：仪表 ──
    public boolean dashScreen()         { return sp.getBoolean("dash_screen", false); }
    public void setDashScreen(boolean v){ sp.edit().putBoolean("dash_screen", v).apply(); }
    public String dashPkg()             { return sp.getString("dash_pkg", ""); }
    public String dashAct()             { return sp.getString("dash_act", ""); }
    public void setDashTarget(String pkg, String act) {
        sp.edit().putString("dash_pkg", pkg).putString("dash_act", act).apply();
    }

    // ── 限制解除（开机执行）──
    public boolean pedestrianOff()       { return sp.getBoolean("ped_off", false); }
    public void setPedestrianOff(boolean v){ sp.edit().putBoolean("ped_off", v).apply(); }

    public boolean videoEnable()         { return sp.getBoolean("video_enable", false); }
    public void setVideoEnable(boolean v){ sp.edit().putBoolean("video_enable", v).apply(); }

    public boolean camera360AnySpeed()   { return sp.getBoolean("cam360_any", false); }
    public void setCamera360AnySpeed(boolean v){ sp.edit().putBoolean("cam360_any", v).apply(); }

    // ── 转向灯 360 视觉辅助 ──
    public boolean turnSignal360()       { return sp.getBoolean("turn360", false); }
    public void setTurnSignal360(boolean v){ sp.edit().putBoolean("turn360", v).apply(); }

    // ── ADB 授权弹窗自动确认（无障碍点击“始终允许”，车机重启后不再手动点）──
    public boolean adbAuthAuto()         { return sp.getBoolean("adb_auth_auto", false); }
    public void setAdbAuthAuto(boolean v){ sp.edit().putBoolean("adb_auth_auto", v).apply(); }
}
