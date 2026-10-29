@echo off
chcp 65001 >nul
rem ============================================================
rem  启动本机 Ollama（可选步骤）
rem  AI 服务选「本机 Ollama」时才需要；选「云端 API」或「本地规则」不需要。
rem
rem  前提：已安装 Ollama 并已拉取模型：
rem      ollama pull qwen3:8b        （文字任务，默认）
rem      ollama pull qwen2.5vl:7b    （识图任务，默认）
rem  模型名如需修改，改这里的变量，或直接到网站「设置 -> AI 服务」里改。
rem
rem  自定义模型存放目录：先设置环境变量 OLLAMA_MODELS，再运行本脚本。
rem ============================================================
set TEXT_MODEL=qwen3:8b
set VISION_MODEL=qwen2.5vl:7b

where ollama >nul 2>nul
if errorlevel 1 (
    echo [ERR] 未找到 ollama 命令。
    echo       请先安装 Ollama: https://ollama.com/download
    echo       安装后确认 `ollama -v` 在命令行可用。
    pause
    exit /b 1
)

netstat -ano | findstr ":11434" | findstr LISTENING >nul
if %errorlevel%==0 (
    echo [OK] Ollama 已在 127.0.0.1:11434 运行
    goto :check
)

echo [..] 正在启动 Ollama 服务...
start "Ollama Serve" /min ollama serve
timeout /t 8 /nobreak >nul

:check
echo.
echo [..] 当前已安装模型：
ollama list
echo.
echo 本项目需要（缺哪个就 pull 哪个）：
echo   文字：%TEXT_MODEL%
echo   识图：%VISION_MODEL%
echo.
echo 启动 Web 应用后访问： http://localhost:8080/ai-study/
