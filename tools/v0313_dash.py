# -*- coding: utf-8 -*-
# v0.3.13: DashboardActivity — 悬浮3D入口 + 左滑手势 + 快照转发 + 二级菜单返回回调
import io

p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\dashboard\DashboardActivity.java'
s = io.open(p, encoding='utf-8').read()
orig = s

def rep(old, new, tag):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit('FAIL[%s]: match=%d' % (tag, n))
    s = s.replace(old, new)
    print('OK[%s]' % tag)

# 1) imports: android.*
rep('import android.app.AlertDialog;\nimport android.content.res.Configuration;',
    'import android.app.AlertDialog;\nimport android.content.Intent;\nimport android.content.res.Configuration;', 'imp1')
rep('import android.view.Gravity;\nimport android.view.View;',
    'import android.view.GestureDetector;\nimport android.view.Gravity;\nimport android.view.MotionEvent;\nimport android.view.View;', 'imp2')
rep('import android.widget.LinearLayout;\nimport android.widget.ScrollView;',
    'import android.widget.FrameLayout;\nimport android.widget.LinearLayout;\nimport android.widget.ScrollView;', 'imp3')
# 2) imports: com.c11.cartool.*
rep('import com.c11.cartool.AppInfo;\nimport com.c11.cartool.CrashHandler;',
    'import com.c11.cartool.AppInfo;\nimport com.c11.cartool.Car3DActivity;\nimport com.c11.cartool.CrashHandler;', 'imp4')

# 3) buildUi: root -> content + FrameLayout host + 悬浮3D按钮 + 二级菜单返回回调
old_build = '''    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        rootView = root;
        applyThemeBackground();   // [FIX-20260928] 按主题 A/B 应用背景（壁纸失败自动降级主题 A）

        root.addView(buildStatusBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        pageSwitcher = new ViewFlipper(this);
        pageSwitcher.setAutoStart(false);
        pageSwitcher.setInAnimation(this, android.R.anim.fade_in);
        pageSwitcher.setOutAnimation(this, android.R.anim.fade_out);
        pageSwitcher.addView(buildGridPage());

        signalPage = new SignalListPage(this);
        pageSwitcher.addView(signalPage);

        controlPage = new ControlListPage(this, vc, new ControlListPage.CommandRunner() {
            @Override public void run(String label, Callable<Boolean> task) {
                runCommand(label, task);
            }
        });
        pageSwitcher.addView(controlPage);

        root.addView(pageSwitcher, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildDock(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        // [v0.3.12] 系统栏避让：车机导航栏隐藏失败时按 insets 让内容避开，保证全部可见
        applySystemBarInsets(root);
    }'''
new_build = '''    private void buildUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        rootView = content;
        applyThemeBackground();   // [FIX-20260928] 按主题 A/B 应用背景（壁纸失败自动降级主题 A）

        content.addView(buildStatusBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        pageSwitcher = new ViewFlipper(this);
        pageSwitcher.setAutoStart(false);
        pageSwitcher.setInAnimation(this, android.R.anim.fade_in);
        pageSwitcher.setOutAnimation(this, android.R.anim.fade_out);
        pageSwitcher.addView(buildGridPage());

        signalPage = new SignalListPage(this, () -> showPage(0));
        pageSwitcher.addView(signalPage);

        controlPage = new ControlListPage(this, vc, new ControlListPage.CommandRunner() {
            @Override public void run(String label, Callable<Boolean> task) {
                runCommand(label, task);
            }
        }, () -> showPage(0));
        pageSwitcher.addView(controlPage);

        content.addView(pageSwitcher, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(buildDock(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        // [v0.3.13] 外层 FrameLayout：右侧悬浮「3D」入口（车模第二界面）
        FrameLayout host = new FrameLayout(this);
        host.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView fab3d = new TextView(this);
        fab3d.setText("3D");
        fab3d.setTextColor(0xFFE4E4E7);
        fab3d.setTextSize(18);
        fab3d.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(0xCC1A1D27);
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(1), 0x66FFFFFF);
        fab3d.setBackground(gd);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.END | Gravity.CENTER_VERTICAL);
        flp.rightMargin = dp(12);
        host.addView(fab3d, flp);
        fab3d.setOnClickListener(v -> openCar3D());

        setContentView(host);
        // [v0.3.12] 系统栏避让：车机导航栏隐藏失败时按 insets 让内容避开，保证全部可见
        applySystemBarInsets(host);
    }'''
rep(old_build, new_build, 'buildUi')

# 4) onSnapshot: 转发快照给 3D 界面
rep('''    @Override public void onSnapshot(DashboardSnapshot s) {
        latest = s;
        applySnapshot(s);
    }''',
    '''    @Override public void onSnapshot(DashboardSnapshot s) {
        latest = s;
        // [v0.3.13] 3D 车模界面共用同一份快照（静态转发，不重复采集）
        Car3DActivity.latest = s;
        applySnapshot(s);
    }''', 'onSnapshot')

# 5) 左滑手势 + 打开 3D（插在 applySystemBarInsets 方法之后、onWindowFocusChanged 之前）
old_anchor = '''        target.requestApplyInsets();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {'''
new_anchor = '''        target.requestApplyInsets();
    }

    // [v0.3.13] 左滑手势：主界面向左快速滑动 → 打开 3D 车模第二界面
    private GestureDetector swipeDetector;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeDetector == null) {
            swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    float dy = Math.abs(e2.getY() - e1.getY());
                    if (Math.abs(dx) > dp(90) && Math.abs(dx) > dy * 1.5f && vx < -dp(40)) {
                        openCar3D();
                        return true;
                    }
                    return false;
                }
            });
        }
        swipeDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void openCar3D() {
        try {
            startActivity(new Intent(this, Car3DActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Logger.e("打开3D车模失败: " + e);
            Toast.makeText(this, "打开 3D 车模失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {'''
rep(old_anchor, new_anchor, 'swipe+open3d')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('ALL_DONE changed=%s' % (s != orig))
