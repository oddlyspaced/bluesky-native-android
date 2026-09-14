# Native showcase (Bluesky slice): Android (Kotlin + Jetpack Compose)

A native Android port of a slice of the Bluesky social app (React Native, MIT, pinned commit `2bfb94d`).
It is a public showcase of the Nativestarter Migration Pack. The app talks to a real AT Protocol server
(the local Bluesky dev network). It contains no hard-coded posts.

- applicationId, namespace and Kotlin package root: `io.nativestarter.showcase.bluesky`. The app does not use Bluesky's reverse domain or name as its identity (copycat and trademark risk; ASSETS.md section 3). Launcher label: "Native showcase".
- Design mode: keep-design. The UI is built from the RN app's ALF tokens, not from Material 3 defaults.

## What is ported

| Checkpoint | Scope | RN source |
|---|---|---|
| CP-00 | ALF design tokens (palette, spacing, radii, type scale), Inter font, XRPC client, session (login, refresh on `ExpiredToken`), bottom tab shell with unread badge, compose FAB | `src/alf`, `@bsky.app/alf`, `src/state/session`, `src/view/shell/bottom-bar` |
| CP-01 | Home: header, Following / Feeds ✨ tabs, composer prompt, "Following" timeline with thread slices, Discover fallback notice, end-of-feed block, pull to refresh | `src/view/screens/Home.tsx`, `src/view/com/home`, `src/view/com/posts` |
| CP-02 | Post thread: anchor post (full timestamp, "Everybody can reply", engagement counts), replies, reply prompt | `src/screens/PostThread` |
| CP-03 | Own profile: banner, avatar, Edit Profile, share, more, metrics, bio, tabs (Posts active), author feed | `src/view/screens/Profile.tsx`, `src/screens/Profile` |

Interactions: pull to refresh, like and unlike, repost and remove repost (bottom sheet "Repost" / "Remove repost"), tap a post to open its thread, back, and switch tabs.
Like and repost are optimistic and shared across screens through `PostInteractions`, the native counterpart of RN `usePostShadow`. A failed write reverts the change.

Android platform branches in the RN code are ported as logic:

- `PostMeta`: on Android the relative time is right-aligned with no "·" (`IS_ANDROID && a.flex_grow`).
- `Layout.Header.TitleText`: the title is centred only on iOS, so on Android it is left-aligned.
- Profile tabs: the scrollable row includes "Starter Packs".

## What is not ported

- Search, Chat, Notifications, the drawer menu, the Feeds screen, compose, sign-in form, create account, Edit Profile, share and post menus. Each opens a screen or text labelled "Not part of this showcase".
- The Replies, Media, Videos, Likes, Feeds and Starter Packs profile tabs (labelled inline).
- Remote avatar and banner images. The seeded accounts have none, so every account shows the fallback avatar.
- Rich text facets (links, mentions, tags), embeds, quote posts, moderation, labels, translations, analytics, deep links, dark and dim themes.
- The Discover feed content under the fallback notice. The dev network Discover feed is empty.
- Secure token storage. The session uses private SharedPreferences, because the showcase signs in only seeded test accounts. A production port would use Keystore-backed storage (see `migration-pack/skills/replace-expo-module`).

## Structure

```
app/src/main/java/io/nativestarter/showcase/bluesky/
  core/network/     XrpcClient (OkHttp), lexicon models, BskyRepository, PostInteractions
  core/session/     Session, SessionStore (interface + prefs/in-memory), SessionManager
  designsystem/     Tokens (ALF), TimeFormat (useTimeAgo, niceDate), components, post/, ui/TabBar
  feature/feed/     FeedViewModel, FeedSlices, HomeScreen
  feature/thread/   ThreadViewModel, ThreadScreen
  feature/profile/  ProfileViewModel, ProfileScreen
  shell/            navigation, bottom bar, signed-out screen, e2e controls
  AppContainer.kt   manual dependency injection
```

ViewModels depend only on `BskyRepository` and `PostInteractions`. The tests use `FakeBskyRepository`.

### Why OkHttp, not Ktor

XRPC here is a handful of GET queries and JSON POST procedures with two headers. OkHttp is one small dependency with no engine selection. `MockWebServer` lets JVM tests assert the exact headers: `atproto-proxy` is set on app.bsky reads and absent on com.atproto writes. Ktor would add a client engine, content negotiation plugins and a larger dependency graph and gain nothing in this slice. kotlinx.serialization handles JSON either way.

## Server and e2e sign-in

- PDS: `http://localhost:3000`. The emulator reaches it through `adb reverse tcp:3000 tcp:3000`.
- `app.bsky.*` reads send `atproto-proxy: <E2E_APPVIEW_DID>#bsky_appview`. The DID comes from RN `dev-env/constants.ts`; RN sets it in `TestCtrls.e2e.tsx`. The build default is `did:plc:bw7ad3erl7btq6qwf66yqiov`. The dev network derives the DID from the AppView port, so if the network comes up with a different DID, override it at build time instead of editing code:
  ```
  ./gradlew --no-daemon -PappviewDid=did:plc:<new-did> :app:installDebug
  ```
- Cleartext HTTP is allowed only in debug builds and only for `localhost` and `127.0.0.1` (`app/src/debug/res/xml/network_security_config.xml`).
- Debug builds put 1x1 dp `e2eSignInAlice` and `e2eSignInBob` controls on the signed-out screen, as RN `TestCtrls` does.
- Every `Modifier.testTag` reuses the RN `testID` (`feedItem-by-<handle>`, `postThreadItem-by-<handle>`, `likeBtn`, `repostBtn`, `bottomBarProfileBtn`, `profileView`, ...). `testTagsAsResourceId` is set at the root, so one Maestro flow drives both apps.

Seed (this resets all data): `curl -X POST "http://localhost:1986/?users&follows&posts&feeds&thread"`

## Build, run, test

Requirements: JDK 17 and Android SDK platform 36. The wrapper downloads Gradle 8.14.3. Memory is shared with other builds, so `gradle.properties` sets `-Xmx3g` and disables the daemon.

1. Build and install:
   ```
   cd android
   ./gradlew --no-daemon :app:installDebug
   ```
2. Run on the emulator:
   ```
   adb -s emulator-5554 reverse tcp:3000 tcp:3000
   adb -s emulator-5554 shell am start -n io.nativestarter.showcase.bluesky/io.nativestarter.showcase.bluesky.MainActivity
   ```
3. Run the JVM unit tests (fixture decoding, time-ago, feed slices, optimistic like and repost, ViewModel states, XRPC headers and refresh):
   ```
   ./gradlew --no-daemon :app:testDebugUnitTest
   ```
4. Run the Compose UI tests on emulator-5554:
   ```
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon connectedDebugAndroidTest
   ```

Fixtures in `app/src/test/resources/fixtures` were captured with curl from the seeded server. Access and refresh tokens are redacted. `getTimelinePage2.json` and `getAuthorFeedPage2.json` are the real empty second pages; they drive the end-of-feed state.

Last measured results (emulator-5554, Android 16):

| Suite | Command | Result |
|---|---|---|
| JVM unit tests | `./gradlew --no-daemon :app:testDebugUnitTest` | 34 tests, 0 failures |
| Compose UI tests | `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest` | 4 tests, 0 failures |

The instrumented tests need Espresso 3.7.0 and the matching AndroidX Test libraries. The Espresso version brought in by `ui-test-junit4` calls `InputManager.getInstance`, which Android 16 removed, and every test failed with `NoSuchMethodException`.

## Capture parity

1. Seed the server and check `adb reverse --list`.
2. Run the flow from `android/parity`:
   ```
   cd android/parity
   maestro --device emulator-5554 test -e PLATFORM=android capture-native.yml
   ```
3. Screenshots are written to `android/parity/shots/native-android-{home,thread,profile}.png`. Measurements and commands are in [PARITY.md](PARITY.md).

## Licensing

- Source code in this directory is a port of MIT-licensed code from bluesky-social-app.
- **Icons: Lucide** (ISC), `lucide-static@1.46.0`. The iOS app uses the same set. Licence: `licenses/Lucide-LICENSE.txt`. Conversion script: `tools/lucide2vd.py`. Glyphs: `menu`, `hash`, `message-square`, `repeat-2`, `heart`, `bookmark`, `forward`, `ellipsis`, `house`, `search`, `message-circle-more`, `bell`, `square-pen`, `camera`, `image`, `arrow-left`, `sliders-vertical`, `earth`, `info`, `chevron-right`, `sparkles`. The upstream icons in `assets/icons/` are the Central icon system, licensed to Bluesky only (ASSETS.md section 2), so they are not used.
- **Logo and default avatar:** Bluesky marks are trademarks (ASSETS.md section 3). The header shows a neutral placeholder mark (`ShowcaseMark`). The fallback avatar is a generic silhouette drawn in code. The launcher label is "Native showcase" and the applicationId is `io.nativestarter.showcase.bluesky`.
- **Font: Inter** (SIL OFL 1.1), `app/src/main/res/font/inter_*.otf`. Licence: `licenses/Inter-OFL.txt`.
