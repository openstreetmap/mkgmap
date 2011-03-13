; INSERT_DEFINES_HERE

SetCompressor /SOLID lzma

; Includes
!include "MUI2.nsh"

; Installer pages
!define MUI_CUSTOMFUNCTION_GUIINIT myGuiInit
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE ${MAPNAME}_license.txt
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!define MUI_UNPAGE_INSTFILES

; Language files
!define MUI_LANGDLL_ALLLANGUAGES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "French"

LangString AlreadyInstalled ${LANG_ENGLISH} "${INSTALLER_NAME} is already installed. $\n$\nClick `OK` to remove the previous version or `Cancel` to cancel this upgrade."
LangString AlreadyInstalled ${LANG_FRENCH} "${INSTALLER_NAME} est déjà installé. $\n$\nAppuyez sur `OK` pour retirer la version précédente ou sur `Annuler` pour annuler cette mise à jour."


; Reservefiles
!insertmacro MUI_RESERVEFILE_LANGDLL ;Language selection dialog


Name "${INSTALLER_DESCRIPTION}"
OutFile "${INSTALLER_NAME}.exe"
InstallDir "${DEFAULT_DIR}"

Function .onInit
  !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Function myGUIInit
  ; Uninstall before installing (code from http://nsis.sourceforge.net/Auto-uninstall_old_before_installing_new )
  ReadRegStr $R0 HKLM \
  "Software\Microsoft\Windows\CurrentVersion\Uninstall\${REG_KEY}" "UninstallString"
  StrCmp $R0 "" done
 
  IfSilent silent
  MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION "$(AlreadyInstalled)" IDOK uninst
  Abort

  ;Run the uninstaller
  uninst:
  ClearErrors
  ExecWait '"$R0" _?=$INSTDIR' ;Do not copy the uninstaller to a temp file
 
  IfErrors no_remove_uninstaller done
    ;You can either use Delete /REBOOTOK in the uninstaller or add some code
    ;here to remove the uninstaller. Use a registry key to check
    ;whether the user has chosen to uninstall. If you are using an uninstaller
    ;components page, make sure all sections are uninstalled.
  no_remove_uninstaller:
  
  Goto done
 
  silent:
  ExecWait '"$R0" /S _?=$INSTDIR' ;Do not copy the uninstaller to a temp file
 
  done:
 
FunctionEnd

Function un.onInit
!insertmacro MUI_UNGETLANGUAGE
FunctionEnd

Section "MainSection" SectionMain
; Files to be installed
  SetOutPath "$INSTDIR"
; INSERT_ADDED_FILES_HERE

; Create MapSource registry keys
; INSERT_REGBIN_HERE
!ifdef INDEX  
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "IDX" "$INSTDIR\${MAPNAME}.mdx"
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "MDR" "$INSTDIR\${MAPNAME}_mdr.img"
!endif
!ifdef TYPNAME  
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "TYP" "$INSTDIR\${TYPNAME}"
!endif
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "BMAP" "$INSTDIR\${MAPNAME}.img"
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "LOC" "$INSTDIR"
  WriteRegStr HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "TDB" "$INSTDIR\${MAPNAME}.tdb"
  
; Write uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

; Create uninstaller registry keys
  WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${REG_KEY}" "DisplayName" "$(^Name)"
  WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${REG_KEY}" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${REG_KEY}" "NoModify" 1
  
SectionEnd

Section "Uninstall"
; Files to be uninstalled
; INSERT_REMOVED_FILES_HERE

  RmDir "$INSTDIR"

; Registry cleanup
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "ID"
!ifdef INDEX  
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "IDX"
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "MDR"
!endif
!ifdef TYPNAME  
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}" "TYP"
!endif
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "BMAP"
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "LOC"
  DeleteRegValue HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}" "TDB"
  DeleteRegKey /IfEmpty HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}\${PRODUCT_ID}"
  DeleteRegKey /IfEmpty HKLM "SOFTWARE\Garmin\MapSource\Families\${REG_KEY}"
  
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${REG_KEY}"

SectionEnd
