[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $Serial,

    [string] $AdbPath = 'adb',

    [switch] $Install
)

$ErrorActionPreference = 'Stop'
$adb = (Get-Command -Name $AdbPath -CommandType Application).Source
$state = & $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') {
    throw 'Connect the target with ADB and accept the pairing on the device.'
}

$abis = (& $adb -s $Serial shell getprop ro.product.cpu.abilist) -split ','
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the Android device architecture.' }

$packageName = 'app.luxeradio.ma'
$installed = & $adb -s $Serial shell pm path $packageName
if ($installed -match '^package:') {
    Write-Output 'Luxe Radio is already installed. Open it and select Get started if prompted.'
    return
}

$abi = @('arm64-v8a', 'armeabi-v7a') | Where-Object { $_ -in $abis } | Select-Object -First 1
if (-not $abi) { throw "The device does not advertise a supported ARM ABI: $($abis -join ', ')." }

Write-Output "Device ${Serial}, Luxe Radio 3.9.42, $abi"
if (-not $Install) {
    Write-Output 'Connection checked. Add -Install to install on this device.'
    return
}

# Each digest covers the original archive whose three APK signatures were checked.
$variants = @{
    'arm64-v8a' = @{
        Sha256 = '1f3e47f638c4a0dca0a102ee35d981c68b3c3ccf21b6e54727bd3f001c554ab4'
        Splits = @('config.arm64_v8a.apk', 'config.xxhdpi.apk')
    }
    'armeabi-v7a' = @{
        Sha256 = '0754587dfc43010c75684f77d99816fdce8b8009da8146d41f574f8ccb828909'
        Splits = @('config.armeabi_v7a.apk', 'config.tvdpi.apk')
    }
}
$variant = $variants[$abi]
$mirror = "https://d.apkpure.net/b/XAPK/${packageName}?versionCode=3090042&nc=${abi}&sv=24"
$referer = 'https://apkpure.net/fr/luxe-radio/app.luxeradio.ma/download'
$temp = New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) ("LuxeRadio-" + [guid]::NewGuid()))
$archive = Join-Path $temp.FullName 'luxe-radio.zip'
$files = @("$packageName.apk") + $variant.Splits
$paths = $files | ForEach-Object { Join-Path $temp.FullName $_ }

try {
    $curl = (Get-Command -Name 'curl.exe' -CommandType Application).Source
    $curlArgs = @('--fail', '--silent', '--show-error', '--location', '--proto', '=https', '--proto-redir', '=https',
        '--max-time', '120', '--user-agent', 'Mozilla/5.0', '--referer', $referer, '--output', $archive, $mirror)
    & $curl @curlArgs
    if ($LASTEXITCODE -ne 0) { throw 'Luxe Radio download failed.' }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $variant.Sha256) {
        throw 'Archive differs from the copy verified for this installation.'
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($archive)
    try {
        foreach ($name in $files) {
            $entry = $zip.GetEntry($name)
            if (-not $entry) { throw "Required APK missing: $name" }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $temp.FullName $name))
        }
    } finally {
        $zip.Dispose()
    }

    & $adb -s $Serial install-multiple @paths
    if ($LASTEXITCODE -ne 0) { throw 'Android did not install Luxe Radio.' }
    Write-Output 'Open Luxe Radio and select Get started to listen as a guest.'
} finally {
    foreach ($path in @($archive) + $paths) {
        if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path }
    }
    Remove-Item -LiteralPath $temp.FullName
}
