@echo off
chcp 65001 >nul
title QLM Zombie - 启动游戏
echo ============================================
echo    QLM Zombie Mod - 启动 Minecraft 客户端
echo    版本: 2.10.0.rewrite.beta.build.22.0
echo ============================================
echo.

cd /d "%~dp0"

echo [1/2] 清理旧的日志文件...
if exist "run\logs\latest.log" del "run\logs\latest.log"

echo [2/2] 启动游戏...
echo.
call gradlew.bat runClient --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo [错误] 游戏启动失败！
    echo 请检查 run\logs\latest.log 查看详细错误信息。
    echo.
    pause
) else (
    echo.
    echo 游戏已关闭。
)
