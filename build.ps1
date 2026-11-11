$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$SDK = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$BT  = "$SDK\build-tools\36.0.0"
$JAR = "$SDK\platforms\android-37.0\android.jar"
if (Test-Path "build") { Get-ChildItem -Path "build" | Remove-Item -Recurse -Force }
New-Item -ItemType Directory -Force -Path "build\gen","build\classes","build\dex" | Out-Null

& "$BT\aapt2.exe" compile --dir res -o build\res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& "$BT\aapt2.exe" link -o build\unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build\gen --min-sdk-version 24 --target-sdk-version 28 --version-code 1 --version-name "1.0" build\res.zip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$javaFiles = Get-ChildItem -Recurse -Path src, build\gen -Filter "*.java" | Select-Object -ExpandProperty FullName
$javaFiles | Out-File -Encoding ascii build\sources.txt
& javac -encoding UTF-8 --release 11 -classpath "$JAR" -d build\classes "@build\sources.txt"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
& jar cf build\classes.jar -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
& "$BT\d8.bat" --release --min-api 24 --lib "$JAR" --output build\dex build\classes.jar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open((Resolve-Path "build\unsigned.apk").Path, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Resolve-Path "build\dex\classes.dex").Path, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $archive.Dispose() }

& "$BT\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }
$KS = "debug.keystore"
if (-not (Test-Path $KS)) {
    & keytool -genkeypair -keystore $KS -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=homeguard debug" | Out-Null
}
& "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:android --key-pass pass:android --out build\homeguard.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
Write-Host "Built build\homeguard.apk"