# v0.3.12 fix: MainActivity 修复孤立 @Override 与乱码中文注释
# 注意：本脚本必须带 UTF-8 BOM 执行（PowerShell 5.1 无 BOM 按 GBK 读会乱码），
# 调用方先转换 BOM 再执行。
$ErrorActionPreference = 'Stop'
$p = 'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\MainActivity.java'
$s = [IO.File]::ReadAllText($p)
$nl = if ($s.Contains("`r`n")) { "`r`n" } else { "`n" }

# 1) 删除悬空的 @Override（紧跟 [v0.3.12] javadoc 之前的那一行）
$s = [regex]::Replace($s, '(?m)^    @Override\r?\n(?=    /\*\* \[v0\.3\.12\])', '')
# 2) 修复 javadoc 注释（乱码整行替换为正确中文）
$s = [regex]::Replace($s, '(?m)^    /\*\* \[v0\.3\.12\][^\r\n]*\*/$',
    '    /** [v0.3.12] 系统栏避让：导航栏隐藏失败时按 insets 让内容避开，保证全部可见 */')
# 3) 修复单行注释（setContentView 三处前的注释）
$s = [regex]::Replace($s, '(?m)// \[v0\.3\.12\][^\r\n]*$',
    '// [v0.3.12] 系统栏避让（导航栏隐藏失败时内容避开，与 DashboardActivity 同策略）')

[IO.File]::WriteAllText($p, $s, (New-Object Text.UTF8Encoding $false))

# 验证
$c = [IO.File]::ReadAllText($p)
$lines = [IO.File]::ReadAllLines($p)
$idx = -1
for ($i = 0; $i -lt $lines.Length; $i++) { if ($lines[$i].Contains('applySystemBarInsets(final View target)')) { $idx = $i; break } }
if ($idx -ge 0) {
    for ($i = $idx - 2; $i -le $idx; $i++) { Write-Output ("{0}: {1}" -f ($i + 1), $lines[$i]) }
}
Write-Output ("orphan @Override remains: {0}" -f ([regex]::IsMatch($c, '(?m)^    @Override\r?\n    /\*\* \[v0\.3\.12\]')))
Write-Output ("bad comment remains: {0}" -f ([regex]::Matches($c, '\[v0\.3\.12\][^\r\n]{0,6}(褰|绯|鐨|閫|闅)')).Count)
