# Carga las variables de .env (formato "export KEY=value") y levanta la API.
$root = Split-Path $PSScriptRoot -Parent
Get-Content (Join-Path $root '.env') | ForEach-Object {
  if ($_ -match '^export\s+([^=]+)=(.*)$') {
    Set-Item -Path "env:$($matches[1])" -Value $matches[2].Trim('"')
  }
}
Set-Location $root
mvn spring-boot:run
