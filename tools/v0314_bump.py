# -*- coding: utf-8 -*-
import io
p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\build.gradle'
s = io.open(p, encoding='utf-8').read()
assert s.count('versionCode 19') == 1
assert s.count('versionName "0.3.13"') == 1
s = s.replace('versionCode 19', 'versionCode 20')
s = s.replace('versionName "0.3.13"', 'versionName "0.3.14"')
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('gradle -> 0.3.14 / code 20')
