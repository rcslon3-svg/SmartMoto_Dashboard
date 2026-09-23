$ErrorActionPreference='Stop'
$javaBin='C:\Program Files\Android\Android Studio\jbr\bin'
$output=Join-Path $PSScriptRoot 'tests/classes'
New-Item -ItemType Directory -Force $output | Out-Null
& "$javaBin/javac.exe" -d $output "$PSScriptRoot/android/app/src/main/java/demo/cyd/companion/Protocol.java" "$PSScriptRoot/tests/ProtocolTest.java" "$PSScriptRoot/android/shared/src/main/java/demo/cyd/companion/BluetoothAddress.java" "$PSScriptRoot/tests/BluetoothAddressTest.java"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
& "$javaBin/java.exe" -cp $output demo.cyd.companion.ProtocolTest
if($LASTEXITCODE -ne 0){throw 'Protocol checks failed'}
& "$javaBin/java.exe" -cp $output demo.cyd.companion.BluetoothAddressTest
if($LASTEXITCODE -ne 0){throw 'Bluetooth address checks failed'}
node --test "$PSScriptRoot/tests/server.test.mjs" "$PSScriptRoot/tests/observer-control.test.mjs" "$PSScriptRoot/../ble-internet-demo/server/test.mjs"
if($LASTEXITCODE -ne 0){throw 'Server checks failed'}
