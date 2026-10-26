$ErrorActionPreference = 'Stop'
$backend = Split-Path $PSScriptRoot -Parent
$target = Join-Path $backend 'config/application-local.yml'
if (Test-Path -LiteralPath $target) { Write-Host 'Local YAML already exists. Leaving it unchanged.'; exit 0 }
$bytes = New-Object byte[] 48
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($bytes)
$random.Dispose()
$secret = [Convert]::ToBase64String($bytes)
$template = Join-Path $backend 'config/application-local.example.yml'
$content = [IO.File]::ReadAllText($template).Replace('secret: "" # local-jwt-secret', "secret: `"$secret`" # local-jwt-secret")
[IO.File]::WriteAllText($target, $content, (New-Object Text.UTF8Encoding($false)))
Write-Host 'Created config/application-local.yml. Set your DB and bootstrap admin credentials before starting.'
