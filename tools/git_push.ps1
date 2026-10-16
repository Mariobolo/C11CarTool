# ==========================================================================
# C11CarTool one-click push helper
# Usage (from project root or anywhere; paths are self-contained):
#   SSH   (recommended after you added the key at https://github.com/settings/ssh/new):
#     powershell -ExecutionPolicy Bypass -File tools\git_push.ps1 ssh
#   HTTPS (use this on a different network, e.g. phone hotspot):
#     powershell -ExecutionPolicy Bypass -File tools\git_push.ps1 https
# ==========================================================================
param([ValidateSet('ssh','https')][string]$Mode = 'ssh')

$git  = 'C:\Mario\PortableGit\bin\git.exe'
$proj = Split-Path -Parent $PSScriptRoot          # .../C11CarTool
$env:HOME = 'C:\Mario\sshhome'
$env:GIT_TERMINAL_PROMPT = '0'

if (-not (Test-Path $git)) { throw "git not found at $git" }

if ($Mode -eq 'ssh') {
    $ssh = 'C:/Mario/PortableGit/usr/bin/ssh.exe'
    $env:GIT_SSH_COMMAND = '"' + $ssh + '" -i C:/Mario/sshhome/.ssh/id_ed25519 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=C:/Mario/sshhome/.ssh/known_hosts'
    & $git -C $proj push 'ssh://git@ssh.github.com:443/Mariobolo/C11CarTool.git' main:main
}
else {
    $sec = Read-Host 'Paste GitHub PAT (hidden input)' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try { $tok = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    & $git -C $proj push ("https://x-access-token:" + $tok + "@github.com/Mariobolo/C11CarTool.git") main:main
}

if ($LASTEXITCODE -eq 0) { Write-Host 'PUSH OK -> https://github.com/Mariobolo/C11CarTool' -ForegroundColor Green }
else { Write-Host 'PUSH FAILED. SSH: confirm the public key was added. HTTPS: switch network and retry.' -ForegroundColor Red }
