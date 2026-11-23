# Local Windows preview only. The application receives the material in memory;
# the model, console and logs never receive the protected file contents or key.
function Get-Yes7ParkingLocalSigningKey {
 param([Parameter(Mandatory)][string]$StateRoot)
 $ErrorActionPreference='Stop'
 if(![OperatingSystem]::IsWindows()){throw 'O runtime local exige proteção Windows CurrentUser.'}
 Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
 $directory=[IO.Path]::GetFullPath($StateRoot)
 [IO.Directory]::CreateDirectory($directory) | Out-Null
 $file=Join-Path $directory 'session-signing.dpapi'
 $scope=[Security.Cryptography.DataProtectionScope]::CurrentUser
 $entropy=[Text.Encoding]::UTF8.GetBytes('yes7parking-local-preview-session-v1')
 if(![IO.File]::Exists($file)){
  $raw=[Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
  try {$protected=[Security.Cryptography.ProtectedData]::Protect($raw,$entropy,$scope)}
  finally {[Security.Cryptography.CryptographicOperations]::ZeroMemory($raw)}
  try {
   $stream=[IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
   try {$stream.Write($protected,0,$protected.Length); $stream.Flush($true)} finally {$stream.Dispose()}
   $acl=[Security.AccessControl.FileSecurity]::new()
   $acl.SetAccessRuleProtection($true,$false)
   $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
   $rule=[Security.AccessControl.FileSystemAccessRule]::new($sid,[Security.AccessControl.FileSystemRights]::FullControl,[Security.AccessControl.AccessControlType]::Allow)
   $acl.AddAccessRule($rule)
   Set-Acl -LiteralPath $file -AclObject $acl
  } catch [IO.IOException] {
   # Another launcher may have won CreateNew. Never overwrite an existing key.
   if(![IO.File]::Exists($file)){throw 'Não foi possível criar o estado de sessão local.'}
  }
 }
 try {
  $raw=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($file),$entropy,$scope)
  try {
   if($raw.Length -ne 48){throw 'Tamanho de material local inválido.'}
   return [Convert]::ToBase64String($raw)
  } finally {[Security.Cryptography.CryptographicOperations]::ZeroMemory($raw)}
 } catch {
  throw 'Estado de sessão local indisponível ou corrompido. Inicialização recusada; nenhuma chave foi substituída.'
 }
}
