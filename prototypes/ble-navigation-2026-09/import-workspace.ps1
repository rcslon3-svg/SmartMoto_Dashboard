param(
    [string]$WorkspaceRoot = (Join-Path $PSScriptRoot '..\..\..')
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$destination = Join-Path $PSScriptRoot 'source'

function Copy-SourceFile([string]$relativePath) {
    $sourcePath = Join-Path $workspace $relativePath
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Missing source file: $sourcePath"
    }
    $targetPath = Join-Path $destination $relativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $targetPath) -Force | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination $targetPath -Force
}

function Copy-SourceTree([string]$relativePath) {
    $sourcePath = Join-Path $workspace $relativePath
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Container)) {
        throw "Missing source directory: $sourcePath"
    }
    foreach ($file in Get-ChildItem -LiteralPath $sourcePath -Recurse -File) {
        $suffix = $file.FullName.Substring($workspace.Length + 1)
        $targetPath = Join-Path $destination $suffix
        New-Item -ItemType Directory -Path (Split-Path -Parent $targetPath) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $targetPath -Force
    }
}

@(
    'cyd-companion-v2/README.md',
    'cyd-companion-v2/HANDOFF.md',
    'cyd-companion-v2/ARCHITECTURE.md',
    'cyd-companion-v2/VALIDATION.md',
    'cyd-companion-v2/test-local.ps1',
    'cyd-companion-v2/android/build.gradle',
    'cyd-companion-v2/android/settings.gradle',
    'cyd-companion-v2/android/gradle.properties',
    'cyd-companion-v2/android/build-local.bat',
    'cyd-companion-v2/android/gradlew',
    'cyd-companion-v2/android/gradlew.bat',
    'cyd-companion-v2/android/app/build.gradle',
    'cyd-companion-v2/android/diagnostics/build.gradle',
    'cyd-companion-v2/android/navprobe/build.gradle',
    'cyd-companion-v2/android/navprobe/README.md',
    'cyd-companion-v2/server/telemetry.mjs',
    'cyd-companion-v2/server/observer-control.mjs',
    'cyd-companion-v2/server/dashboard.html',
    'ble-internet-demo/README.md',
    'ble-internet-demo/.gitignore',
    'ble-internet-demo/flash-esp32.ps1',
    'ble-internet-demo/serial-check.py',
    'ble-internet-demo/start-server.cmd',
    'ble-internet-demo/android/build.gradle',
    'ble-internet-demo/android/settings.gradle',
    'ble-internet-demo/android/gradle.properties',
    'ble-internet-demo/android/build-local.bat',
    'ble-internet-demo/android/gradlew',
    'ble-internet-demo/android/gradlew.bat',
    'ble-internet-demo/android/app/build.gradle',
    'ble-internet-demo/firmware/platformio.ini',
    'ble-internet-demo/server/server.mjs',
    'ble-internet-demo/server/index.html',
    'ble-internet-demo/server/package.json',
    'ble-internet-demo/server/test.mjs',
    'waveshare-round-display/README.md',
    'waveshare-round-display/platformio.ini',
    'nano-round-display/README.md',
    'nano-round-display/platformio.ini'
) | ForEach-Object { Copy-SourceFile $_ }

@(
    'cyd-companion-v2/android/gradle/wrapper',
    'cyd-companion-v2/android/app/src',
    'cyd-companion-v2/android/diagnostics/src',
    'cyd-companion-v2/android/navprobe/src',
    'cyd-companion-v2/android/shared/src',
    'ble-internet-demo/android/gradle/wrapper',
    'ble-internet-demo/android/app/src',
    'ble-internet-demo/firmware/src',
    'waveshare-round-display/src',
    'nano-round-display/src'
) | ForEach-Object { Copy-SourceTree $_ }

@(
    'cyd-companion-v2/tests/ProtocolTest.java',
    'cyd-companion-v2/tests/BluetoothAddressTest.java',
    'cyd-companion-v2/tests/server.test.mjs',
    'cyd-companion-v2/tests/observer-control.test.mjs'
) | ForEach-Object { Copy-SourceFile $_ }

# The manifest covers every copied file and makes omissions visible on review.
$rows = Get-ChildItem -LiteralPath $destination -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($destination.Length + 1).Replace('\', '/')
    "$(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256 | Select-Object -ExpandProperty Hash)  $relative"
}
Set-Content -LiteralPath (Join-Path $PSScriptRoot 'SOURCE_SHA256.txt') -Value $rows -Encoding utf8
Write-Output "Copied $($rows.Count) source files to $destination"
