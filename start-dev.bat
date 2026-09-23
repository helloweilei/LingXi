@echo off
chcp 65001 >nul
title 灵犀创作 - 本地开发启动器
echo ============================================
echo   灵犀创作 · 一键启动（后端 8080 + 前端 5173）
echo ============================================

set JAVA_HOME=D:\Program Files\Java

start "灵犀-后端(8080)" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"
timeout /t 3 >nul
start "灵犀-前端(5173)" cmd /k "cd /d %~dp0frontend && npm run dev"

timeout /t 8 >nul
start http://localhost:5173

echo 已在浏览器打开 http://localhost:5173 （首次启动后端约需 15 秒，若打不开请稍后刷新）
