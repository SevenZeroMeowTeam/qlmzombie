# QLM Zombie build46 deploy script (Posh-SSH)
# Usage: powershell -ExecutionPolicy Bypass -File deploy-build46.ps1
Import-Module Posh-SSH -Force -ErrorAction Stop

$ErrorActionPreference = 'Stop'
$HostIP = '154.222.28.103'
$HostPort = 4066
$HostUser = 'root'
$JarLocal = 'D:\mcmod\build\libs\qlmzombie-3.0.0.beta.build46.jar'
$JarRemoteDir = '/www/wwwroot/build/libs/'
$SeverAdminDir = '/www/wwwroot/minecraftsc/'

$FilesToUpload = @(
    @{ Local = 'D:\mcmod\SeverAdmin\deploy.sh';               Remote = '/www/wwwroot/minecraftsc/deploy.sh' },
    @{ Local = 'D:\mcmod\SeverAdmin\mc\entrypoint-wrapper.sh'; Remote = '/www/wwwroot/minecraftsc/mc/entrypoint-wrapper.sh' },
    @{ Local = 'D:\mcmod\SeverAdmin\mc\libs-list.txt';        Remote = '/www/wwwroot/minecraftsc/mc/libs-list.txt' }
)
# FTB Quests 任务配置目录（config/ftbquests/quests → 服务端 /data/config，游戏内任务数据源）
$QuestConfigLocal = 'D:\mcmod\SeverAdmin\mc\config\ftbquests\quests'
$QuestConfigRemote = '/www/wwwroot/minecraftsc/mc/config/ftbquests/quests'

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " QLM Zombie build46 deploy" -ForegroundColor Cyan
Write-Host (" Target: {0}:{1} ({2})" -f $HostIP, $HostPort, $HostUser) -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$cred = $null
$keyFile = $null

# 1) 优先尝试密钥认证（用户指定私钥）
$candidateKey = 'D:\mcmod\SeverAdmin\154.222.28.103_id_ed25519'
if (Test-Path $candidateKey) {
    Write-Host "Trying SSH key auth ($candidateKey) ..." -ForegroundColor Cyan
    $session = $null
    try {
        $session = New-SSHSession -ComputerName $HostIP -Port $HostPort -Credential (New-Object System.Management.Automation.PSCredential($HostUser, (New-Object System.Security.SecureString))) -KeyFile $candidateKey -AcceptKey -ConnectionTimeout 20 -ErrorAction Stop
    } catch {
        $session = $null
    }
    if ($session) {
        $keyFile = $candidateKey
        Write-Host ("SSH connected via key (Session {0})" -f $session.SessionId) -ForegroundColor Green
    } else {
        Write-Host "  key auth failed, falling back to password" -ForegroundColor Yellow
    }
}
else {
    Write-Host "Specified key file not found: $candidateKey" -ForegroundColor Red
}

# 2) 密钥失败则用密码
if (-not $session) {
    Write-Host ("Enter SSH password for root@{0}:" -f $HostIP) -ForegroundColor Yellow -NoNewline
    $securePass = Read-Host -AsSecureString
    if ($null -eq $securePass -or $securePass.Length -eq 0) {
        Write-Host "Empty password, abort" -ForegroundColor Red
        exit 1
    }
    $cred = New-Object System.Management.Automation.PSCredential($HostUser, $securePass)
    Write-Host "Connecting SSH (password) ..." -ForegroundColor Cyan
    $session = New-SSHSession -ComputerName $HostIP -Port $HostPort -Credential $cred -AcceptKey -ConnectionTimeout 30
    if (-not $session) { Write-Error "SSH connection failed"; exit 1 }
    Write-Host ("SSH connected via password (Session {0})" -f $session.SessionId) -ForegroundColor Green
}

try {
    Write-Host "[0/5] Ensuring remote directories ..." -ForegroundColor Cyan
    $mkdirCmd = @(
        "mkdir -p /www/wwwroot/build/libs",
        "mkdir -p /www/wwwroot/minecraftsc",
        "mkdir -p /www/wwwroot/minecraftsc/mc",
        "mkdir -p /www/wwwroot/minecraftsc/mc/config/ftbquests/quests"
    ) -join ' && '
    Invoke-SSHCommand -SessionId $session.SessionId -Command $mkdirCmd -TimeOut 60 | Out-Null
    Write-Host "Remote directories ready" -ForegroundColor Green

    Write-Host "[1/5] Uploading main jar (409 MB, ~20 min) ..." -ForegroundColor Cyan
    if ($keyFile) {
        Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential (New-Object System.Management.Automation.PSCredential($HostUser, (New-Object System.Security.SecureString))) -KeyFile $keyFile -AcceptKey -Path $JarLocal -Destination $JarRemoteDir -Force -ErrorAction Continue
    } else {
        Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential $cred -AcceptKey -Path $JarLocal -Destination $JarRemoteDir -Force -ErrorAction Continue
    }
    Write-Host "Main jar uploaded" -ForegroundColor Green

    Write-Host "[2/5] Uploading SeverAdmin scripts ..." -ForegroundColor Cyan
    foreach ($f in $FilesToUpload) {
        Write-Host "  $($f.Local) -> $($f.Remote)" -ForegroundColor Cyan
        $remoteDir = ($f.Remote -replace '/[^/]+$','')
        if ($keyFile) {
            Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential (New-Object System.Management.Automation.PSCredential($HostUser, (New-Object System.Security.SecureString))) -KeyFile $keyFile -AcceptKey -Path $f.Local -Destination $remoteDir -Force -ErrorAction Continue
        } else {
            Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential $cred -AcceptKey -Path $f.Local -Destination $remoteDir -Force -ErrorAction Continue
        }
    }
    Write-Host "Scripts uploaded" -ForegroundColor Green

    Write-Host "[3/5] Uploading FTB Quests config ..." -ForegroundColor Cyan
    if (Test-Path $QuestConfigLocal) {
        $questFiles = Get-ChildItem $QuestConfigLocal -Recurse -File
        foreach ($qf in $questFiles) {
            $rel = $qf.FullName.Substring($QuestConfigLocal.Length).TrimStart('\', '/')
            $remotePath = "$QuestConfigRemote/$($rel -replace '\\','/')"
            $remoteDir = ($remotePath -replace '/[^/]+$','')
            $mkQuestDirCmd = "mkdir -p '$remoteDir'"
            Invoke-SSHCommand -SessionId $session.SessionId -Command $mkQuestDirCmd -TimeOut 30 | Out-Null
            if ($keyFile) {
                Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential (New-Object System.Management.Automation.PSCredential($HostUser, (New-Object System.Security.SecureString))) -KeyFile $keyFile -AcceptKey -Path $qf.FullName -Destination $remoteDir -Force -ErrorAction Continue
            } else {
                Set-SCPItem -ComputerName $HostIP -Port $HostPort -Credential $cred -AcceptKey -Path $qf.FullName -Destination $remoteDir -Force -ErrorAction Continue
            }
        }
        Write-Host "  Quest config uploaded ($($questFiles.Count) files)" -ForegroundColor Green
    } else {
        Write-Host "  WARNING: quest config not found: $QuestConfigLocal" -ForegroundColor Red
    }

    Write-Host "[4/5] Verifying remote jar ..." -ForegroundColor Cyan
    $verifyCmd = "cd $JarRemoteDir && python3 -m zipfile -t qlmzombie-3.0.0.beta.build46.jar > /dev/null 2>&1 && echo JAR_OK || echo JAR_BAD"
    $r = Invoke-SSHCommand -SessionId $session.SessionId -Command $verifyCmd -TimeOut 180
    Write-Host "  Remote check: $($r.Output -join ' ')" -ForegroundColor Green
    if (($r.Output -join ' ') -notmatch 'JAR_OK') {
        Write-Host "  WARNING: jar verification failed, still deploying" -ForegroundColor Red
    }

    Write-Host "[5/5] Running remote deploy.sh docker ..." -ForegroundColor Cyan
    $deployCmd = "cd $SeverAdminDir && nohup ./deploy.sh docker > /tmp/build46_deploy.log 2>&1 & echo DEPLOY_STARTED"
    $r2 = Invoke-SSHCommand -SessionId $session.SessionId -Command $deployCmd -TimeOut 30
    Write-Host "  $($r2.Output -join ' ')" -ForegroundColor Green

    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Deploy started. Check: docker logs -f qlm-minecraft" -ForegroundColor Cyan
    Write-Host "Deploy log: tail -f /tmp/build46_deploy.log" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}
finally {
    Remove-SSHSession -SessionId $session.SessionId | Out-Null
    Write-Host "SSH session closed" -ForegroundColor Cyan
}
