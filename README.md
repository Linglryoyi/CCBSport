# CCBSport

CCBSport is a native Android workout tracker for outdoor runs, indoor runs, fitness sessions, and walks. It records GPS routes outdoors and can estimate distance from steps when GPS is unavailable.

## Features

- Offline Mapsforge maps for supported cities in China, plus manual import of Mapsforge `.map` files.
- Local workout history and JSON import and export, including saved GPS tracks.
- Light, dark, and system appearance modes.
- Simplified Chinese, English, Japanese, Korean, and Russian interfaces.

The first launch asks for a language and offers optional offline map setup. You can add or remove offline maps later in Settings. Workout data stays on the device unless you choose to export it.

## Install

Download the signed APK from [GitHub Releases](https://github.com/Linglryoyi/CCBSport/releases). CCBSport supports Android 8.0 (API 26) and later.

## Build from source

Use JDK 17 and the Android SDK with Android 36 and Build Tools 35.0.0 installed. Set `ANDROID_HOME` to your SDK directory or configure it in Android Studio.

```sh
./gradlew :app:assembleDebug
```

On Windows, run `gradlew.bat :app:assembleDebug`. The output is in `app/build/outputs/apk/debug/`. Public source builds use their own signing keys.

## License and credits

CCBSport source code is licensed under the [Apache License 2.0](LICENSE). Third-party components retain their own licenses. See [bundled notices](app/src/main/assets/licenses/) and **Settings → About** for Mapsforge, OpenStreetMap, Material Symbols, and other credits.

Developed by Linglryoyi.
