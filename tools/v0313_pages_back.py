# -*- coding: utf-8 -*-
# v0.3.13: ControlListPage / SignalListPage 增加界面内返回 header
import io

def patch(path, repls):
    s = io.open(path, encoding='utf-8').read()
    for old, new, tag in repls:
        n = s.count(old)
        if n != 1:
            raise SystemExit('FAIL[%s %s]: match=%d' % (path.split(chr(92))[-1], tag, n))
        s = s.replace(old, new)
        print('OK[%s:%s]' % (path.split(chr(92))[-1], tag))
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

header_block = '''        // [v0.3.13] 界面内返回（不依赖硬件返回键）
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(DashboardTheme.SURFACE);
        TextView back = new TextView(ctx);
        back.setText("← 返回");
        back.setTextColor(DashboardTheme.BLUE);
        back.setTextSize(15);
        back.setPadding(dp(14), dp(10), dp(14), dp(10));
        back.setOnClickListener(v -> { if (onBack != null) onBack.run(); });
        TextView title = new TextView(ctx);
        title.setText("%s");
        title.setTextColor(DashboardTheme.TEXT);
        title.setTextSize(17);
        title.setPadding(dp(4), 0, 0, 0);
        header.addView(back);
        header.addView(title);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

'''

ctrl = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\dashboard\ControlListPage.java'
patch(ctrl, [
    ('public ControlListPage(Context ctx, VehicleController vc, CommandRunner runner) {',
     'public ControlListPage(Context ctx, VehicleController vc, CommandRunner runner, final Runnable onBack) {',
     'ctor'),
    ('        TextView hint = new TextView(ctx);',
     (header_block % '\u5168\u90e8\u8f66\u63a7') + '        TextView hint = new TextView(ctx);',
     'header'),
])

sig = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\dashboard\SignalListPage.java'
patch(sig, [
    ('public SignalListPage(Context ctx) {',
     'public SignalListPage(Context ctx, final Runnable onBack) {',
     'ctor'),
    ('        searchBox = new EditText(ctx);',
     (header_block % '\u5168\u8f66\u4fe1\u53f7') + '        searchBox = new EditText(ctx);',
     'header'),
])
print('ALL_DONE')
