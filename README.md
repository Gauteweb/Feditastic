# Feditastic

*English · [Norsk](README_no.md)*

This is an Android client for the Fediverse. It works with platforms such as Pleroma Social,
Akkoma, GoToSocial and Mastodon. The app has a Norwegian and an English user interface.

## Screenshots

Here are some screenshots showing what the app looks like. It is currently only available for Android.

![Examples](Feditastic_screenshots.jpg)

## Features

**Reading**
- Login with OAuth on the instance's own page – your password never passes through the app.
- Multiple accounts, also on different instances. Switch with "Switch account" on the profile page.
- Timelines: Home, local feed, federated posts and your own lists (choose in "Home ▾").
- Threads, profiles with profile fields and verified links, and follow/unfollow.
- Profiles show the user's role on the instance (e.g. admin or moderator) and mark automated accounts (bots).
  Choose between **All posts** and **Media only** (the user's own posts with images or video).
- Link previews, number of replies/boosts/favourites and "via <app>".
- Custom emoji from the instance, polls, and notifications with a filter for **All** or **Replies**.
  Replies and mentions are shown in full in the notifications.
- Posts that reply to or mention you stand out with a green background (blue in the Green theme).
- Search for users (`@name@domain`) and hashtags. Tap #hashtags and @mentions to open them in the app.

**Writing and interacting**
- Reply, boost, favourite, bookmark, copy link, and delete or edit your own posts.
- Visibility, content warning, up to 4 images with alt text, Markdown, emoji picker and language selection (nn, nb, en).
- The character limit is fetched from the instance (5000 if it doesn't specify one).
- Add users to lists from their profile.

**Images and video**
- Images are shown without cropping. Tap for full screen with alt text, zoom and swiping between images.
- Videos play directly in the feed. Animated GIFs play in posts, emoji and profile pictures.
- The "Media" tab shows the same feeds as "Home", but only posts with images or video – with
  the image first and the text below, like in Pixelfed and Instagram. Multiple images are swiped sideways.

**Navigation**
- Bottom bar: Home · Media · Alerts · Search · Write · Profile. Long-press a button to hide the labels.
- Swipe between tabs. Double-tap a tab to refresh, or the top bar to scroll to the top.

**Chat and more**
- The chat icon on "Home" opens "Matrix": [chat.gaute.space](https://chat.gaute.space) in the app – the Matrix client
  [Cinny](https://cinny.in) in the app's colours. The connection is kept alive when you switch tabs.
- The "About" button in the local feed shows the instance's "About" page, terms of service or server rules.
- Five colour themes in the same style – Dark (default), Green, Blue, Purple and Light – chosen with the palette button on "Profile".
- ⓘ on "Profile" shows information about the app and a link to the source code.

### Not included (yet)

Full-text search in posts, Pleroma chat, sending emoji reactions, push notifications, streaming and profile editing.

## Installing

1. Download `Feditastic.apk` from the releases page of this repository.
2. Allow installing from unknown sources for the app you open the file with.
3. Open **Feditastic**, enter your instance (e.g. `social.gaute.space`) and tap
   **Log in**. Approve the app in the browser – you are sent back to the app automatically.

Requires Android 8.0 (API 26) or newer.

## Building it yourself

You need JDK 17 or newer and the Android SDK (platform 35). The easiest way is to open the folder in
Android Studio. From the command line:

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease` gives the small, optimised APK (about 2 MB, not "debuggable").
It is unsigned by default, because F-Droid builds and signs the app itself. To install it
directly on your phone, add `signReleaseWithDebugKey=true` to `local.properties` and it will
be signed with the debug key. To update an existing installation, the new APK must be signed
with the same key as the old one, otherwise you have to uninstall first.

## Technical

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3 with a custom theme) |
| Networking | OkHttp + kotlinx.serialization |
| Images | Coil |
| Navigation | Navigation Compose |
| Chat | Android WebView with [Cinny](https://cinny.in) from chat.gaute.space |
| API | Mastodon-compatible API (`/api/v1/...`), supported by Pleroma, Akkoma, GoToSocial and Mastodon |
| Redirect scheme | `feditastic://oauth` |
| OAuth scopes | `read write follow push` |
| Package name | `app.feditastic` (Kotlin package: `app.pleromastisk`) |

The source code is in `app/src/main/java/app/pleromastisk/`:

- `data/` – data models, API client and login storage
- `vm/` – ViewModels (timelines, thread, profile, notifications, lists, login)
- `ui/` – screens, theme (`Theme.kt` has the colours from the Pleroma theme) and components

## Privacy

The app has no tracking or analytics. It only talks to the instance you choose, and loads
images and emoji from the addresses the instance provides. The access token is stored only
locally on the device, and "Log out" deletes it for the account you are logged in as.

If you open the chat, Cinny is loaded from chat.gaute.space, and it talks to the Matrix server
you log in to there (norge.chat by default). The chat login is stored in the app's own
browser storage on the device and is independent of the Fediverse login.

## License

Copyright © 2026 Gaute Holmin

Feditastic is free software, licensed under the
[**GNU General Public License version 3 or later (GPL-3.0-or-later)**](https://www.gnu.org/licenses/gpl-3.0.html).
The full text is in [`LICENSE`](LICENSE).

In practice, this means:

- ✅ You may use, copy, modify and share the app and the code, also commercially.
- ✅ If you share the app or a modified version of it, you must make the **source code available
  under the same license** (GPL-3.0-or-later) and keep the copyright notices.
- ❌ You may **not** make a closed-source version of the app or the code and distribute it.

The program comes **without any warranty**, see sections 15 and 16 of the license.

**The background image** (`app/src/main/res/drawable-nodpi/bakgrunn.jpg`) was taken by
Gaute Holmin and is licensed under the same license as the rest of the app (GPL-3.0-or-later).

**The chat** is [Cinny](https://cinny.in) ([source code](https://github.com/cinnyapp/cinny)),
a Matrix client made by Ajay Bura and the contributors to the Cinny project, licensed under
AGPL-3.0. Cinny is not part of the app: it runs on chat.gaute.space and is shown in the app,
which applies its own colours.

Third-party libraries (Jetpack Compose, OkHttp, Coil, kotlinx.serialization and others) are
Apache-2.0 licensed and follow their own terms.
