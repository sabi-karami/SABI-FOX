; SABI-FOX for Windows installer (Inno Setup 6)
; Built by .github/workflows/windows-ci.yml:  ISCC /DAppVersion=1.2.3 /DSourceDir=<app image> sabifox.iss

#ifndef AppVersion
  #define AppVersion "1.1.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\SABI-FOX"
#endif
#ifndef OutputDir
  #define OutputDir "..\build\installer"
#endif

[Setup]
AppId={{B6E0C2F4-5A0E-4E59-9D59-5ABF0F0C5A11}
AppName=SABI-FOX
AppVersion={#AppVersion}
AppVerName=SABI-FOX {#AppVersion}
AppPublisher=SABI-FOX
AppPublisherURL=https://github.com/sabi-karami/SABI-FOX
AppSupportURL=https://github.com/sabi-karami/SABI-FOX/issues
AppUpdatesURL=https://github.com/sabi-karami/SABI-FOX/releases
VersionInfoVersion={#AppVersion}
; Per-user install: no UAC prompt for installing or for silent self-updates.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\SABI-FOX
DefaultGroupName=SABI-FOX
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\SABI-FOX.exe
UninstallDisplayName=SABI-FOX
OutputDir={#OutputDir}
OutputBaseFilename=SABI-FOX-Windows-Setup-x64
SetupIconFile=sabifox.ico
WizardImageFile=wizard-side.bmp
WizardSmallImageFile=wizard-small.bmp
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
CloseApplications=force
RestartApplications=no
ShowLanguageDialog=auto
LicenseFile=..\LICENSE

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
#ifexist "Persian.isl"
Name: "persian"; MessagesFile: "Persian.isl"
#endif

[CustomMessages]
english.LaunchApp=Start SABI-FOX
english.DesktopIcon=Create a desktop shortcut
english.StartupIcon=Start SABI-FOX when I sign in to Windows
#ifexist "Persian.isl"
persian.LaunchApp=اجرای SABI-FOX
persian.DesktopIcon=ایجاد میان‌بر روی دسکتاپ
persian.StartupIcon=اجرای SABI-FOX هنگام ورود به ویندوز
#endif

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopIcon}"; Flags: unchecked
Name: "startup"; Description: "{cm:StartupIcon}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\SABI-FOX"; Filename: "{app}\SABI-FOX.exe"
Name: "{autodesktop}\SABI-FOX"; Filename: "{app}\SABI-FOX.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "SABI-FOX"; ValueData: """{app}\SABI-FOX.exe"" --minimized"; Tasks: startup
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "SABI-FOX"; Flags: uninsdeletevalue dontcreatekey
Root: HKCU; Subkey: "Software\Microsoft\Windows NT\CurrentVersion\AppCompatFlags\Layers"; ValueType: none; ValueName: "{app}\SABI-FOX.exe"; Flags: uninsdeletevalue dontcreatekey

[Run]
Filename: "{app}\SABI-FOX.exe"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent
; Silent self-update: start the new version again (it reconnects if it was connected).
Filename: "{app}\SABI-FOX.exe"; Flags: nowait; Check: WizardSilent

[UninstallRun]
Filename: "{sys}\schtasks.exe"; Parameters: "/Delete /TN SABI-FOX /F"; Flags: runhidden; RunOnceId: "RemoveStartupTask"

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\SABI-FOX\cache"
Type: filesandordirs; Name: "{localappdata}\SABI-FOX\runtime"
