# v0.3.12: MainActivity 系统栏 insets 避让
$ErrorActionPreference = 'Stop'
$p = 'D:\Stu\Android\C11_TEST\projects\C11CarTool\app\src\main\java\com\c11\cartool\MainActivity.java'
$s = [IO.File]::ReadAllText($p)
$nl = if ($s.Contains("`r`n")) { "`r`n" } else { "`n" }

$old = '        setContentView(buildUI());'
$new = '        // [v0.3.12] 系统栏避让（导航栏隐藏失败时内容避开，与 DashboardActivity 同策略）' + $nl +
       '        View rootUi = buildUI();' + $nl +
       '        setContentView(rootUi);' + $nl +
       '        applySystemBarInsets(rootUi);'
$before = ([regex]::Matches($s, [regex]::Escape($old))).Count
$s = $s.Replace($old, $new)

$anchor = '    protected void onDestroy() {'
if (-not $s.Contains($anchor)) { throw 'anchor onDestroy not found' }
$method = '    /** [v0.3.12] 系统栏避让：导航栏隐藏失败时按 insets 让内容避开，保证全部可见 */' + $nl +
          '    private void applySystemBarInsets(final View target) {' + $nl +
          '        if (target == null) return;' + $nl +
          '        target.setOnApplyWindowInsetsListener((v, insets) -> {' + $nl +
          '            int top = insets.getSystemWindowInsetTop();' + $nl +
          '            int bottom = insets.getSystemWindowInsetBottom();' + $nl +
          '            if (top > 0 || bottom > 0) v.setPadding(0, top, 0, bottom);' + $nl +
          '            return insets;' + $nl +
          '        });' + $nl +
          '        target.requestApplyInsets();' + $nl +
          '    }' + $nl + $nl
$s = $s.Replace($anchor, $method + $anchor)

[IO.File]::WriteAllText($p, $s, (New-Object Text.UTF8Encoding $false))
# 验证
$c = [IO.File]::ReadAllText($p)
Write-Output ("replaced setContentView(buildUI()): {0} -> {1}" -f $before, ([regex]::Matches($c, 'setContentView\(buildUI\(\)\);')).Count)
Write-Output ("rootUi declarations: {0}" -f ([regex]::Matches($c, 'View rootUi = buildUI\(\);')).Count)
Write-Output ("applySystemBarInsets method present: {0}" -f $c.Contains('private void applySystemBarInsets(final View target)'))
