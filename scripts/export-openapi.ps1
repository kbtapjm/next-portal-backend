param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateSet('admin', 'user')][string]$Application = 'admin'
)
$ErrorActionPreference = 'Stop'
$backend = Split-Path $PSScriptRoot -Parent
$document = Invoke-RestMethod "$($BaseUrl.TrimEnd('/'))/v3/api-docs"
if (!$document.openapi -or !$document.paths) { throw 'The server did not return an OpenAPI document.' }
$fileName = if ($Application -eq 'admin') { 'openapi.json' } else { 'openapi-user.json' }
$target = Join-Path $backend "docs/$fileName"
$json = ($document | ConvertTo-Json -Depth 100).Replace("`r`n", "`n")
[IO.File]::WriteAllText($target, $json + "`n", [Text.UTF8Encoding]::new($false))
Write-Host "Exported generated OpenAPI document to docs/$fileName."
