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
`compileSdk` is 37; `minSdk` remains 24 and `targetSdk` remains 30. Raising the
target SDK requires a separate Android behavior migration and device testing,
especially for notification permission and background execution changes.

Simple to use:
ADD->Select app-> keywords -> Done!!!

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
