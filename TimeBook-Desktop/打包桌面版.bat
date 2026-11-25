@echo off
chcp 65001 >nul
cd /d %~dp0
echo 正在打包 TimeBook 桌面版（便携 exe）...
set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
call npx electron-builder --win portable --x64
if errorlevel 1 (
  echo.
  echo 打包失败，请把上面的报错发我。
) else (
  echo.
  echo 打包完成：..\dist-desktop\
)
pause
