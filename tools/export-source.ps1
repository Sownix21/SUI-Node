# Creates a source-only archive without machine state, signing keys or device captures.
# Run from any directory: powershell -File tools/export-source.ps1
$ErrorActionPreference = 'Stop'
$nodeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$nodeFiles = @('README.md', 'LICENSE', 'NOTICE', 'CONTRIBUTING.md', 'SECURITY.md',
    '.gitignore', '.gitattributes', '.env.example', 'build.gradle.kts',
    'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
    'app/build.gradle.kts', 'app/proguard-rules.pro', 'app/.gitignore')
$nodeFolders = @('app/src', 'gradle', 'docs', 'design', 'tools', '.github')
$nodeEntries = @()
foreach ($nodeRelative in $nodeFiles) {
    $nodePath = Join-Path $nodeRoot $nodeRelative
    if (-not (Test-Path -LiteralPath $nodePath -PathType Leaf)) { throw "Missing source file: $nodeRelative" }
    $nodeEntries += Get-Item -LiteralPath $nodePath
}
foreach ($nodeFolder in $nodeFolders) {
    $nodeEntries += Get-ChildItem -LiteralPath (Join-Path $nodeRoot $nodeFolder) -File -Recurse -Force |
        Where-Object { $_.FullName -notmatch '[\\/]__pycache__[\\/]' -and $_.Extension -notin @('.pyc', '.pyo') }
}
foreach ($nodeEntry in $nodeEntries) {
    $nodeRelative = $nodeEntry.FullName.Substring($nodeRoot.Length + 1).Replace('\', '/')
    if (-not $nodeEntry.FullName.StartsWith($nodeRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        ($nodeEntry.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        $nodeRelative -match '(^|/)(build|\.gradle|\.git|\.idea|\.kotlin)/' -or
        $nodeRelative -match '\.(keystore|jks|p12|pfx|pem|key|db|sqlite|sqlite3|apk|aab|log)$' -or
        $nodeEntry.Name -in @('local.properties', 'keystore.properties', 'signing.properties') -or
        ($nodeEntry.Name.StartsWith('.env') -and $nodeEntry.Name -ne '.env.example')) {
        throw "Refusing non-source or sensitive artifact: $nodeRelative"
    }
}
$nodeOutputFolder = Join-Path $nodeRoot 'build'
New-Item -ItemType Directory -Path $nodeOutputFolder -Force | Out-Null
$nodeOutput = Join-Path $nodeOutputFolder ('s-ui-node-source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nodeArchive = [IO.Compression.ZipFile]::Open($nodeOutput, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($nodeEntry in ($nodeEntries | Sort-Object FullName -Unique)) {
        $nodeRelative = $nodeEntry.FullName.Substring($nodeRoot.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($nodeArchive, $nodeEntry.FullName, $nodeRelative) | Out-Null
    }
} finally { $nodeArchive.Dispose() }
Write-Output "Source archive: $nodeOutput"
Write-Output 'Review its contents before publishing. Filename exclusions cannot detect every embedded secret.'
