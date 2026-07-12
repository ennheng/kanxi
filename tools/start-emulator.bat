@echo off
chcp 65001 >/dev/null
set ANDROID_SDK_ROOT=C:\Users\Administrator\AppData\Local\Android\Sdk
set ANDROID_AVD_HOME=D:\Backup\Documents\看戏\.android\avd
set PATH=%ANDROID_SDK_ROOT%\emulator;%ANDROID_SDK_ROOT%\platform-tools;%PATH%

emulator -avd kanxi -skin 1080x2400 -no-snapshot-load
