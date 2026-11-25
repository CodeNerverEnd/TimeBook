@echo off
chcp 65001 >nul
setlocal
rem ============================================================
rem  TimeBook 一键打包脚本
rem  用法：双击本文件，或在命令行执行  打包APK.bat
rem  产物：dist\TimeBook-1.2.apk
rem ============================================================

set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%..\.tools\jdk17\zulu17.54.21-ca-jdk17.0.13-win_x64"
set "ANDROID_HOME=%ROOT%..\.tools\android-sdk"
set "GRADLE=%ROOT%..\.tools\gradle\gradle-8.9\bin\gradle.bat"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] 找不到 JDK 17: %JAVA_HOME%
    echo         请确认 .tools 目录没有被删除。
    pause
    exit /b 1
)
if not exist "%ANDROID_HOME%\platforms\android-34" (
    echo [ERROR] 找不到 Android SDK: %ANDROID_HOME%
    pause
    exit /b 1
)
if not exist "%GRADLE%" (
    echo [ERROR] 找不到 Gradle: %GRADLE%
    pause
    exit /b 1
)

cd /d "%ROOT%"
echo SDK 路径写入 local.properties ...
> local.properties echo sdk.dir=%ANDROID_HOME:\=/%

echo 开始构建 release APK ...
call "%GRADLE%" assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [FAILED] 构建失败，请把上面的报错发给助手。
    pause
    exit /b 1
)

if not exist dist mkdir dist
copy /y "app\build\outputs\apk\release\app-release.apk" "dist\TimeBook-1.2.apk" >nul
echo.
echo [OK] 打包完成: %ROOT%dist\TimeBook-1.2.apk
echo      把这个文件发到手机上安装即可（需允许"安装未知来源应用"）。
pause
