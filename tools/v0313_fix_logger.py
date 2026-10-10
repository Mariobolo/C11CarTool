# -*- coding: utf-8 -*-
import io
p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\dashboard\DashboardActivity.java'
s = io.open(p, encoding='utf-8').read()
old = 'Logger.e("打开3D车模失败: " + e);'
new = 'Logger.error("打开3D车模失败: " + e);'
n = s.count(old)
print('match=%d' % n)
if n == 1:
    io.open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new))
    print('fixed')
else:
    # 打印包含 Logger.e 的行便于排查
    for i, line in enumerate(s.splitlines()):
        if 'Logger.e(' in line:
            print('line%d: %s' % (i + 1, line))
