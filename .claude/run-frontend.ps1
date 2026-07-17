# Levanta el dev server del frontend (Nuxt) que vive en el repo hermano.
Set-Location (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'labflow_frontend')
pnpm dev
