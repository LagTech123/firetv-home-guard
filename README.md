# Home Guard

A tiny accessibility service for Fire TV that starts your chosen launcher as soon as Amazon's home screen appears, without the usual delay.

## The problem

On Fire OS you cannot set a default launcher: Amazon's home is a protected package and always handles the HOME key first. Third-party launchers such as Projectivy only get to take over afterwards, and Android holds back app launches for about 5 seconds after HOME (`APP_SWITCH_DELAY_TIME`). The result is several seconds of Amazon's home before your launcher appears.

## How it works

- The service listens for window changes from `com.amazon.tv.launcher` only. It reads no screen content.
- When Amazon's home appears, it sets an alarm about 20 ms in the future. The alarm's `PendingIntent` starts the target launcher.
- A launch sent by `AlarmManager` comes from `system_server` and is not subject to the 5-second hold-back.
- `min_futurity=0` makes Android fire that alarm immediately instead of clamping it a few seconds ahead.

The technique is the one described in the documentation of [dumbtv](https://github.com/humblemuzzu/dumbtv). This project does not use dumbtv's code: it was written from that description.

## Status

Tested on one Fire TV running Fire OS 7 (Android 9) with Projectivy Launcher. Expect to adapt it for other builds. It does not cover Fire OS 6, which refuses third-party accessibility services. It does not hide Amazon's home, so a brief flash of it can show before your launcher appears.

## Build

Needs a JDK and the Android SDK. `build.ps1` calls aapt2, javac, d8, zipalign and apksigner directly (no Gradle) and signs with a debug key it creates on first run. Edit the `$BT` and `$JAR` lines at the top if your SDK has different versions.

    .\build.ps1        # produces build\homeguard.apk

## Choose your launcher

The target launcher is set by constants at the top of `src/dev/local/homeguard/GuardService.java` (`TARGET_PKG` and `TARGET_FALLBACK_ACTIVITY`). It is set to Projectivy (`com.spocky.projengmenu`). Change them and rebuild to use another launcher.

## Install

    adb install -r build\homeguard.apk
    adb shell settings put global alarm_manager_constants min_futurity=0
    adb shell settings put secure enabled_accessibility_services dev.local.homeguard/dev.local.homeguard.GuardService
    adb shell settings put secure accessibility_enabled 1

The third command replaces the whole accessibility list. Read it first with `adb shell settings get secure enabled_accessibility_services` and separate several services with `:`. The second command replaces any other values in `alarm_manager_constants`.

A Fire OS update can reset either setting. Re-run the matching command if the delay comes back.

## Pause and remove

    adb shell settings put global homeguard_pause 1    # pause (use 0 to resume)
    adb uninstall dev.local.homeguard

Also remove `dev.local.homeguard/dev.local.homeguard.GuardService` from `enabled_accessibility_services` if you uninstall.

## License

MIT. See [LICENSE](LICENSE).
