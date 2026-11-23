$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-session-key.ps1')
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('yes7parking-signing-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$assertions=0
function Assert-Local([bool]$Condition,[string]$Description){
 if(!$Condition){throw "Falhou: $Description"}
 $script:assertions++
}
$first=Get-Yes7ParkingLocalSigningKey -StateRoot $testRoot
$again=Get-Yes7ParkingLocalSigningKey -StateRoot $testRoot
Assert-Local ($first -ceq $again) 'reinício reutiliza a chave'
Assert-Local ([Convert]::FromBase64String($first).Length -eq 48) 'material de 48 bytes'
$stateFile=Join-Path $testRoot 'session-signing.dpapi'
$cipher=[IO.File]::ReadAllBytes($stateFile)
Assert-Local ($cipher.Length -gt 48) 'arquivo contém envelope protegido'
Assert-Local ([Text.Encoding]::UTF8.GetString($cipher) -notmatch [regex]::Escape($first)) 'não grava a chave em texto'
$other=Get-Yes7ParkingLocalSigningKey -StateRoot (Join-Path $testRoot 'other-runtime')
Assert-Local ($other -cne $first) 'runtimes isolados têm chaves distintas'
$expectedSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$rules=(Get-Acl -LiteralPath $stateFile).Access
Assert-Local (@($rules | Where-Object {$_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -ne $expectedSid}).Count -eq 0) 'ACL exclusiva do usuário'
[IO.File]::WriteAllBytes($stateFile,[byte[]]@(1,2,3))
$rejected=$false
try {$ignored=Get-Yes7ParkingLocalSigningKey -StateRoot $testRoot} catch {$rejected=$true}
Assert-Local $rejected 'estado corrompido falha sem rotacionar silenciosamente'
Assert-Local ([IO.File]::ReadAllBytes($stateFile).Length -eq 3) 'preserva estado corrompido para diagnóstico'
Write-Output "PASS: $assertions verificações de sessão local; nenhum material de assinatura foi exibido."
