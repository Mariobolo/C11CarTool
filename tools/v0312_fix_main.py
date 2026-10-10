# -*- coding: utf-8 -*-
# v0.3.12 fix: 修复 MainActivity 中 PowerShell 插入产生的乱码注释
import io, re, sys

p = r'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\MainActivity.java'
s = io.open(p, encoding='utf-8').read()

JAVADOC = '    /** [v0.3.12] \u7cfb\u7edf\u680f\u907f\u8ba9\uff1a\u5bfc\u822a\u680f\u9690\u85cf\u5931\u8d25\u65f6\u6309 insets \u8ba9\u5185\u5bb9\u907f\u5f00\uff0c\u4fdd\u8bc1\u5168\u90e8\u53ef\u89c1 */'
LINECMT = '// [v0.3.12] \u7cfb\u7edf\u680f\u907f\u8ba9\uff08\u5bfc\u822a\u680f\u9690\u85cf\u5931\u8d25\u65f6\u5185\u5bb9\u907f\u5f00\uff0c\u4e0e DashboardActivity \u540c\u7b56\u7565\uff09'

s2 = re.sub(r'(?m)^    /\*\* \[v0\.3\.12\][^\r\n]*\*/$', JAVADOC, s)
s2 = re.sub(r'(?m)// \[v0\.3\.12\][^\r\n]*$', LINECMT, s2)
s2 = re.sub(r'(?m)^    @Override\r?\n(?=    /\*\* \[v0\.3\.12\])', '', s2)

io.open(p, 'w', encoding='utf-8', newline='').write(s2)

c = io.open(p, encoding='utf-8').read()
bad = len(re.findall(r'\[v0\.3\.12\][^\r\n]{0,6}[\u896f\u7ecf\u953d\u95fd\u96c5]', c))
print('bad_remains=%d' % bad)
print('javadoc_ok=%s' % ('\u7cfb\u7edf\u680f\u907f\u8ba9\uff1a' in c))
print('linecmt_ok=%s' % ('DashboardActivity \u540c\u7b56\u7565' in c))
print('orphan_override=%s' % bool(re.search(r'(?m)^    @Override\r?\n    /\*\* \[v0\.3\.12\]', c)))
print('method_ok=%s' % ('applySystemBarInsets(final View target)' in c))
