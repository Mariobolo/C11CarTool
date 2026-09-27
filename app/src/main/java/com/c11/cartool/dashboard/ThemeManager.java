package com.c11.cartool.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 主题选择与持久化：
 * <ul>
 *   <li>{@link #THEME_SOLID} 主题 A：纯色深色渐变 + 彩色光晕背景，玻璃模块；</li>
 *   <li>{@link #THEME_BING} 主题 B：必应每日壁纸（一次性高斯模糊）作全局背景，半透明玻璃模块。</li>
 * </ul>
 */
public final class ThemeManager {

    public static final int THEME_SOLID = 0;
    public static final int THEME_BING = 1;

    private static final String PREF = "c11_prefs";
    private static final String KEY_THEME = "theme";

    private final SharedPreferences sp;
    private int theme;

    public ThemeManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        theme = sp.getInt(KEY_THEME, THEME_SOLID);
    }

    public int get() { return theme; }

    public boolean isBing() { return theme == THEME_BING; }

    public void set(int t) {
        theme = t;
        sp.edit().putInt(KEY_THEME, t).apply();
    }
}
