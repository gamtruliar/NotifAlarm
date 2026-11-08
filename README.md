# NotifAlarm
Make alarm for any notifications with filter
No more missing live, missing message, chances......

Please feel free to build and use,
**But Please do not publish to store without auth**

## APK builds

GitHub Actions builds a debug APK on every push and pull request. You can also
start a build from **Actions > Build APK > Run workflow** after the workflow is
on the default branch.

To download it, open a successful **Build APK** run and download
`NotifAlarm-debug-<run number>` from **Artifacts**. Unzip it to get `app-debug.apk`.
Artifacts are kept for 30 days. Sign in to GitHub to download them.

The APK is signed with the build runner's debug key; no repository secrets are
required. It is intended for testing, not store publishing. The key can change
between builds, so you may need to uninstall an existing installation first
(which removes its app data), including an installation from Google Play.

## GitHub Releases

Pushing a tag starting with `v` builds and tests the app, then publishes a
[GitHub Release](https://github.com/gamtruliar/NotifAlarm/releases) containing
`NotifAlarm-debug.apk` and `SHA256SUMS.txt`. The Release is published only after
the build and unit tests succeed. Branch pushes and pull requests keep their
APKs in Actions artifacts.

After committing the workflow and pushing the branch, publish the current app
version with:

```sh
git push origin main
git tag v1.0.5
git push origin v1.0.5
```

The tag must point to a commit containing this workflow. Use a new version tag
for each release, and update `versionCode`/`versionName` in `app/build.gradle`
when changing the app version. You can also run **Build APK** manually against
an existing `v*` tag. Re-running a tag build replaces the APK and checksum
assets on that tag's existing Release.

Publishing uses GitHub's automatic `GITHUB_TOKEN`; no extra secrets are needed.
These downloadable APKs use the debug signing described above.

## Build locally

The build uses JDK 27 (see `.java-version`), Gradle 9.9.0, Android Gradle Plugin
9.5.1, Kotlin/serialization compiler plugin 2.4.21, Android SDK 37 and Build Tools
36.0.0. These versions were selected from the stable releases on 2026-11-08.
Gradle is pinned by the included wrapper and its distribution SHA-256 checksum.
Kotlin compilation uses AGP's built-in Kotlin support.

Install JDK 27 and set `JAVA_HOME` to it. In Android Studio, select the same JDK
under **Settings > Build, Execution, Deployment > Build Tools > Gradle**.
Install SDK Platform 37 and Build Tools 36.0.0 through the SDK Manager, then set
`sdk.dir` in `local.properties` to your own SDK directory. The checked-in file
contains an old machine-specific path; CI replaces it with the runner's SDK path.

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` on macOS/Linux (or
`bash ./gradlew` if the script is not executable), or
`gradlew.bat :app:assembleDebug :app:testDebugUnitTest` on Windows.
The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The build runs on JDK 27 and emits Java/Kotlin 17 bytecode for Android.
Version 1.0.5 (version code 7) uses `compileSdk` and `targetSdk` 37 (Android 17),
with `minSdk` 24. On Android 13+, allow notification permission when first
opening the app; notification-listener access is a separate system setting.
You can reopen notification settings from **Notification permissions** in the
main menu. Alarms are paused while app notifications or the alarm channel are
disabled, so an alarm always has a visible stop control.

If Android blocks notification-listener access for a sideloaded APK, choose
**Notification access → Access blocked? → Open App info** in the main menu.
The app explains how to use **⋮ → Allow restricted settings** on the system
App info page. On return, it checks notification access and offers the access
settings again if still needed. Android requires the user to unlock this
setting; the app only opens the appropriate page. See the
[Android restricted settings instructions](https://support.google.com/android/answer/12623953).

Internal broadcast receivers are private to the app, notification pending
intents are immutable, and the layout handles system bars and keyboard insets.
The higher target SDK addresses the outdated-Android-target warning; it does
not guarantee approval by Play Protect or remove sideloading/restricted-settings
checks. Notification listening and background alarms still require device testing.

Simple to use:
ADD->Select app-> keywords -> Done!!!

## Volume and conditional responses

Open a filter's detail page to set its default sound, vibration pattern and
ringtone volume (0–100%). Volume is relative to the device's current alarm volume;
0% mutes the ringtone and does not disable vibration.

Under **Conditional response rules**, add rules for a local time range,
weekdays, connected Wi-Fi SSIDs and charging state. Each rule chooses its own
sound/vibration combination, vibration pattern and volume. Rules can be enabled,
edited, deleted and moved up or down.

- All specified conditions in a rule must match. Any one of its listed SSIDs
  is sufficient; names match exactly, including case and spaces (one per line).
  Use **Use current Wi-Fi** to add the connected network without typing its
  name, or enter other networks manually.
- The first matching enabled rule wins. If none match, the filter's default
  sound, vibration and volume apply. Global switches and ban periods still apply.
- Time ranges include the start and exclude the end. They can cross midnight;
  equal endpoints mean all day. Weekdays refer to the current local day, even
  after midnight. No selected weekdays means every day.
- An empty SSID list imposes no Wi-Fi condition, including when disconnected.
  Unavailable Wi-Fi or battery information cannot satisfy a condition that
  requires it. Rules are evaluated when a notification arrives; an ongoing
  alarm does not change when device conditions change.
- Wi-Fi rules require location access and the system Location switch. For
  background notification matching on Android 10+, allow location **all the
  time** (and precise location where offered). Saving a Wi-Fi rule guides you
  through the optional permission flow. If Android hides the SSID, that rule
  will not match. See Android's [background location permission guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/background).

Choose **Continuous (default)**, **Short pulse**, **Long pulse**, **Double pulse**,
**Triple pulse** or **SOS** under **Vibration pattern**, either for the filter's
default response or for each conditional rule. **Once** plays one complete
pattern; other durations repeat the rhythm and its pauses until the alarm stops.
If multiple alarms overlap, the newest vibration takes priority. When it ends,
any earlier repeating alarm resumes; stopping another alarm leaves it playing.

The **Test** button uses current device conditions and the same response rules,
volume and vibration pattern settings. Existing saved filters and rules retain
the original continuous vibration until their pattern is changed.

## Discord forwarding

In **Settings > Discord forwarding**, save an incoming Discord webhook URL and
enable forwarding. Matched notifications are posted to that channel separately
from the local sound/vibration response. **Send test** checks delivery without
triggering a recording. Forwarding is off by default; enabling it shares matched
notification content with Discord. The webhook is excluded from Android backups.

[Secure and safe]
This is a open source project in 
https://github.com/gamtruliar/NotifAlarm
feel free to help me fixing bug wwwwww

[Many filters]
You can make many filters, many keywords for one app
or
One keyword for many apps

[One click shutdown]
easy to stop all function 

[Ban period]
setup when you don't want to be disturb
by week

[Ringing type]
Once, x Minus -> forever..

[One Filter one sound]
specify you alarm


miss左saya個突發練歌回 (╯°Д°)╯ ┻━┻, 全速作左隻app出黎wwwww

Store Link:
https://play.google.com/store/apps/details?id=com.gamtruliar.notialarm
