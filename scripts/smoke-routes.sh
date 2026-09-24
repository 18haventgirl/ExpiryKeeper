#!/usr/bin/env bash
# 全路由冒烟：逐个进入每个界面并确认进程存活。
# 存在的理由：一次负 padding（Compose 禁止）让设置页与添加页一点就闪退，
# 而单测和 lint 全都抓不到 —— 只有真的把每条路走一遍才会暴露。
# 依赖：Studio 里已启动的模拟器（1080x2424 机型，坐标按此写死）。
set -uo pipefail

ADB="${ADB:-/d/Files/AndroidSDK/platform-tools/adb.exe}"
PKG=com.expirykeeper
SERIAL="${SERIAL:-emulator-5554}"
export MSYS_NO_PATHCONV=1

fail=0
alive() {
  local pid
  pid=$("$ADB" -s "$SERIAL" shell pidof "$PKG" | tr -d '\r')
  if [ -n "$pid" ]; then
    echo "  OK    $1 (pid $pid)"
  else
    echo "  DEAD  $1"
    fail=1
  fi
}

tap() { "$ADB" -s "$SERIAL" shell input tap "$1" "$2"; }
back() { "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_BACK; }

"$ADB" -s "$SERIAL" shell am force-stop "$PKG"
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 5
echo "全路由冒烟 $SERIAL"
alive "今日（冷启动）"

tap 976 309; sleep 3; alive "设置页（今日 → 设置入口）"
back; sleep 2

tap 814 2298; sleep 2; alive "清单页（切 tab）"
tap 965 2036; sleep 3; alive "添加页（FAB）"
back; sleep 2

tap 540 684; sleep 3; alive "详情浮层（点条目）"
back; sleep 2; alive "浮层关闭后回到清单"

fatal=$("$ADB" -s "$SERIAL" logcat -d 2>&1 | grep -c "FATAL EXCEPTION")
echo "FATAL EXCEPTION 条数: $fatal"
[ "$fatal" -gt 0 ] && fail=1

if [ "$fail" -eq 0 ]; then echo "冒烟通过"; else echo "冒烟失败"; fi
exit "$fail"
