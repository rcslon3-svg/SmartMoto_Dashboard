param([Parameter(Mandatory=$true)][ValidatePattern('^COM[0-9]+$')][string]$Port)
$ErrorActionPreference='Stop'
$demoPython=Join-Path $env:USERPROFILE '.platformio\penv\Scripts\python.exe'
$demoEsptool=Join-Path $env:USERPROFILE '.platformio\packages\tool-esptoolpy\esptool.py'
$demoImage=Join-Path $PSScriptRoot 'releases\cyd-demo-merged.bin'
if (!(Test-Path -LiteralPath $demoImage)) { throw 'Firmware image is missing.' }
Write-Host "Writing CYD ESP32-2432S028R demo to $Port. Existing firmware will be replaced."
& $demoPython $demoEsptool --chip esp32 --port $Port --baud 460800 write_flash --flash_mode dio --flash_freq 40m --flash_size 4MB 0x0 $demoImage
if ($LASTEXITCODE -ne 0) { throw 'Flashing failed.' }
