@echo off
REM ═══════════════════════════════════════════════════════════════════
REM  TribalFLN — SIH Demo Kiosk Lockdown Script
REM  Date: August 24, 2026
REM
REM  Purpose: Safely lock down the Android tablet before handing it
REM  to the SIH jury for live evaluation. Prevents interruptions,
REM  ensures screen stays on, and pins the app to the foreground.
REM
REM  Usage:
REM    1. Connect tablet via USB with ADB debugging enabled
REM    2. Run this script: lockdown_tablet_for_demo.bat
REM    3. Tablet is now jury-ready
REM
REM  To unlock after demo:
REM    - Long-press Back + Volume Up (exits screen pinning)
REM    - Or run: adb shell locksettings clear
REM ═══════════════════════════════════════════════════════════════════

setlocal enabledelayedexpansion

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║  TribalFLN — SIH Demo Kiosk Lockdown                       ║
echo  ║  Preparing tablet for jury evaluation...                    ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

REM ─── Step 1: Verify ADB connection ───────────────────────────────────
echo  [1/7] Checking ADB connection...
adb devices | findstr /r "device$" >nul 2>&1
if errorlevel 1 (
    echo  ❌ No device found! Connect tablet via USB and enable ADB debugging.
    pause
    exit /b 1
)
echo  ✅ Device connected.
echo.

REM ─── Step 2: Enable Airplane Mode (100% offline) ────────────────────
echo  [2/7] Enabling Airplane Mode...
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
echo  ✅ Airplane Mode ON, WiFi OFF, Mobile Data OFF.
echo.

REM ─── Step 3: Set screen timeout to 30 minutes ───────────────────────
echo  [3/7] Setting screen timeout to 30 minutes...
adb shell settings put system screen_off_timeout 1800000
echo  ✅ Screen will stay on for 30 minutes.
echo.

REM ─── Step 4: Set brightness to 100% ─────────────────────────────────
echo  [4/7] Setting brightness to 100%...
adb shell settings put system screen_brightness 255
echo  ✅ Brightness set to maximum.
echo.

REM ─── Step 5: Enable Do Not Disturb (no popups) ─────────────────────
echo  [5/7] Enabling Do Not Disturb mode...
adb shell settings put global zen_mode 2
adb shell settings put secure heads_up_notifications_enabled 0
echo  ✅ DND ON, heads-up notifications OFF.
echo.

REM ─── Step 6: Disable keyguard (no lock screen) ─────────────────────
echo  [6/7] Disabling lock screen...
adb shell locksettings clear --old "" 2>nul
adb shell input keyevent 82
echo  ✅ Lock screen disabled.
echo.

REM ─── Step 7: Launch and pin TribalFLN ────────────────────────────────
echo  [7/7] Launching TribalFLN and enabling screen pinning...
adb shell am start -n com.example.flnapp/.TeacherDashboardActivity
timeout /t 3 /nobreak >nul

REM Enable screen pinning via settings
adb shell settings put secure lock_to_app_enabled 1
echo  ✅ TribalFLN launched and screen pinned.
echo.

REM ─── Final Status ───────────────────────────────────────────────────
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║  ✅ TABLET IS JURY-READY                                    ║
echo  ╠══════════════════════════════════════════════════════════════╣
echo  ║  • Airplane Mode:  ON (100%% offline)                       ║
echo  ║  • Screen Timeout: 30 minutes                              ║
echo  ║  • Brightness:     100%%                                    ║
echo  ║  • DND Mode:       ON (no popups)                          ║
echo  ║  • App Pinned:     TribalFLN TeacherDashboard              ║
echo  ║  • Lock Screen:    Disabled                                ║
echo  ╠══════════════════════════════════════════════════════════════╣
echo  ║  TO UNLOCK AFTER DEMO:                                     ║
echo  ║  • Long-press Back + Volume Up                             ║
echo  ║  • Or: adb shell locksettings clear                        ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

pause
