# -*- coding: utf-8 -*-
# v0.3.14: DashboardActivity 状态栏加「🎙 唤起」chip + doInvokeVoice()
import io

p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\dashboard\DashboardActivity.java'
s = io.open(p, encoding='utf-8').read()

# 1) 状态栏 chip：在「🎤 释放」后加「🎙 唤起」
old1 = '        TextView voiceChip = chip("🎤 释放", true, v -> doReleaseVoice());\n'
new1 = old1 + '        TextView invokeChip = chip("🎙 唤起", true, v -> doInvokeVoice());\n'
assert s.count(old1) == 1, 'chip anchor'
s = s.replace(old1, new1)
print('chip added')

# 2) doReleaseVoice 后加 doInvokeVoice
old2 = '''    private void doReleaseVoice() {
        boolean ok = vc.releaseVoice();
        Toast.makeText(this, ok ? "已发送语音释放" : "语音释放失败（见日志）",
                Toast.LENGTH_SHORT).show();
    }
'''
new2 = old2 + '''
    private void doInvokeVoice() {
        boolean ok = vc.invokeVoice();
        Toast.makeText(this, ok ? "已唤起小零" : "唤起失败（见日志）",
                Toast.LENGTH_SHORT).show();
    }
'''
assert s.count(old2) == 1, 'method anchor'
s = s.replace(old2, new2)
print('method added')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DashboardActivity done')
