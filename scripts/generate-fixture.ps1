$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$fixtureRoot = Join-Path $PSScriptRoot '../testdata'
New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
$bitmap = New-Object System.Drawing.Bitmap(2400, 1800)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::White)
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$font = New-Object System.Drawing.Font('Arial', 19)
$headerFont = New-Object System.Drawing.Font('Arial', 20, [System.Drawing.FontStyle]::Bold)
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::Gray, 1)
$brush = [System.Drawing.Brushes]::Black
$left = 400
$column = 278
$days = @('LUNEDI','MARTEDI','MERCOLEDI','GIOVEDI','VENERDI','SABATO','DOMENICA')
for ($day = 0; $day -lt 7; $day++) {
    $x = $left + $day * $column
    $graphics.DrawString($days[$day], $headerFont, $brush, $x + 10, 45)
    $graphics.DrawString("$($day + 5)-ott-2026", $font, $brush, $x + 10, 85)
    $graphics.DrawLine($pen, $x, 30, $x, 1720)
}
$graphics.DrawLine($pen, 2346, 30, 2346, 1720)
$target = @('19-01','X','23:45-5:45','FE','11:30-17:30','6:30-12:30','A.G.')
for ($row = 0; $row -lt 30; $row++) {
    $y = 135 + $row * 52
    $name = if ($row -eq 12) { 'BALDEDDU LUCA' } else { "DIPENDENTE $($row + 1)" }
    $graphics.DrawString($name, $font, $brush, 35, $y + 7)
    $graphics.DrawLine($pen, 25, $y, 2346, $y)
    for ($day = 0; $day -lt 7; $day++) {
        $text = if ($row -eq 12) { $target[$day] } elseif (($row + $day) % 3 -eq 0) { 'X' } else { '12-18' }
        $graphics.DrawString($text, $font, $brush, $left + $day * $column + 10, $y + 7)
        $graphics.DrawString('6', $font, $brush, $left + ($day + 1) * $column - 28, $y + 7)
    }
}
$bitmap.Save((Join-Path $fixtureRoot 'synthetic-roster.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose(); $bitmap.Dispose(); $font.Dispose(); $headerFont.Dispose(); $pen.Dispose()
Write-Output 'testdata/synthetic-roster.png generated (2400x1800, fictional employees).'
