# Native showcase (Bluesky slice): Android (Kotlin + Jetpack Compose)

A native Android port of a slice of the Bluesky social app (React Native, MIT, pinned commit `2bfb94d`).
It is a public showcase of the Nativestarter Migration Pack. The app talks to a real AT Protocol server
(the local Bluesky dev network). It contains no hard-coded posts.

- applicationId, namespace and Kotlin package root: `io.nativestarter.showcase.bluesky`. The app does not use Bluesky's reverse domain or name as its identity (copycat and trademark risk; ASSETS.md section 3). Launcher label: "Native showcase".
- Design mode: keep-design. The UI is built from the RN app's ALF tokens, not from Material 3 defaults.
- Parity evidence, noise floor, masks, event diff and accepted differences: [PARITY.md](PARITY.md).

## What is ported

| Checkpoint | Scope | RN source |
|---|---|---|
| CP-00 | ALF tokens and palettes; light, dark and dim themes through a CompositionLocal (system dark maps to dim, the RN default); Inter; XRPC client with the AppView proxy header; session with single-flight refresh, terminal sign-out and server session deletion; Keystore-backed session store; account-scoped dependencies; bottom tab shell with unread polling and badge; soft reset; toasts; haptics; analytics events | `src/alf`, `src/state/session`, `src/view/shell/bottom-bar`, `src/state/queries/notifications/unread.tsx`, `src/lib/haptics.ts` |
| CP-01 | Home Following feed: FeedTuner and slice rules, `PostFeed` rows (composer prompt, thread slices, "View full thread", Discover fallback marker), pagination with auto-fill and a generation guard, loading, first-load error, empty, load-more retry and end-of-feed states, header that hides on scroll, pull to refresh, rich text facets, "Show more" for long posts, post controls with guards and haptics, repost sheet, like and repost mutation queues with `via` | `src/view/screens/Home.tsx`, `src/view/com/posts`, `src/lib/api/feed-manip.ts`, `src/state/queries/post-feed.ts`, `src/state/queries/post.ts` |
| CP-02 | Post thread through `getPostThreadV2` with the preferred sort: parents, anchor, replies, "Continue thread" and "Read N more replies" rows, not-found and blocked tombstones, moderated replies behind "Show more replies" with `getPostThreadOtherV2`, cached anchor with reply skeletons while loading, `ThreadError` with Retry, fixed "Write your reply" prompt | `src/state/queries/usePostThread`, `src/screens/PostThread` |
| CP-03 | Own profile: header loading placeholder, `ErrorScreen`, header (banner, avatar, Edit Profile, share, more, metrics, bio), section tabs, Posts feed with the author API filter and the shared `PostFeed` rows, "No posts yet" with "Write a post", "Posts hidden" for blocks, end of feed | `src/view/screens/Profile.tsx`, `src/screens/Profile` |

Android platform branches in the RN code are ported as logic, for example: the relative time in `PostMeta` is right-aligned with no "·"; the header title is left-aligned (it is centred only on iOS); the profile tabs scroll and include "Starter Packs"; RN Android text line boxes and hairline strokes are reproduced in the design system (`alfText`, `Hairline`).

### Analytics

Every event keeps the RN name and props (`src/analytics/metrics/types.ts`). Debug builds append one `{"name", "props"}` JSON object per line to `files/events.jsonl`; release builds send nothing. Read the log with:

```
adb -s emulator-5554 shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl
```

Launching with `clearState` (the parity flows do) deletes the log.

## What is not ported

- Search, Chat, Notifications, the drawer menu, the Feeds screen, compose, sign-in form, create account, Edit Profile, share and post menus, thread sort and view options. Each opens a screen labelled "Not part of this showcase".
- The Replies, Media, Videos, Likes, Feeds and Starter Packs profile tabs (labelled inline).
- Embeds, quote posts, label-based moderation, translations, deep links and the tree thread view.
- Remote avatar and banner images. The seeded accounts have none, so every account shows the fallback avatar.
- The Discover feed content under the fallback notice. RN development builds do not fetch it either.
- Link and mention detection in the profile description (RN runs `detectFacets` client-side).

## Structure

```
app/src/main/java/io/nativestarter/showcase/bluesky/
  core/analytics/   Analytics, JSONL event log, clout
  core/common/      error copy (cleanError), cancellation-safe runCatching
  core/network/     XrpcClient (OkHttp), lexicon models, BskyRepository, PostInteractions, ToggleQueue, PostCache
  core/platform/    Haptics
  core/session/     SessionManager, Keystore + DataStore SessionStore
  designsystem/     tokens, themes, Txt, Hairline, AlfButton, toasts, time and count formats, rich text, feed states, post item, tab bar
  feature/feed/     FeedTuner, FeedPager, FeedViewModel, PostFeedList, HomeScreen
  feature/thread/   ThreadTraversal, ThreadViewModel, ThreadScreen
  feature/profile/  ProfileViewModel, ProfileScreen
  shell/            navigation, bottom bar, unread polling, signed-out screen, e2e controls
  AppContainer.kt   manual dependency injection: AppContainer (process) and AccountScope (signed-in account)
```

View models depend on `BskyRepository`, `PostInteractions` and `Analytics`. The JVM tests use `FakeBskyRepository` with fixtures.

### Why OkHttp, not Ktor

XRPC here is a handful of GET queries and JSON POST procedures with two headers. OkHttp is one small dependency with no engine selection. `MockWebServer` lets JVM tests assert the exact headers: `atproto-proxy` is set on app.bsky reads and absent on com.atproto calls. kotlinx.serialization handles JSON either way.

## Server and e2e sign-in

- PDS: `http://localhost:3000`. The emulator reaches it through `adb reverse tcp:3000 tcp:3000`.
- `app.bsky.*` reads send `atproto-proxy: <E2E_APPVIEW_DID>#bsky_appview`. The build default is `did:plc:bw7ad3erl7btq6qwf66yqiov`. The dev network derives the DID from the AppView port, so if the network comes up with a different DID, override it at build time:
  ```
  ./gradlew --no-daemon -PappviewDid=did:plc:<new-did> :app:installDebug
  ```
- Cleartext HTTP is allowed only in debug builds and only for `localhost` and `127.0.0.1` (`app/src/debug/res/xml/network_security_config.xml`). The release configuration allows no cleartext.
- Debug builds put 1x1 dp `e2eSignInAlice` and `e2eSignInBob` controls on the signed-out screen, as RN `TestCtrls` does. Release builds do not have them.
- Every `Modifier.testTag` reuses the RN `testID`. `testTagsAsResourceId` is set at the root, so one Maestro flow drives both apps.

Seed (this resets all data): `curl -X POST "http://localhost:1986/?users&follows&posts&feeds&thread"`

## Build, run, test

Requirements: JDK 17 and Android SDK platform 36. The wrapper downloads Gradle 8.14.3. Memory is shared with other builds, so use `--no-daemon` (`gradle.properties` sets `-Xmx3g`).

1. Build and install the debug build:
   ```
   cd android
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:installDebug
   ```
2. Run on the emulator:
   ```
   adb -s emulator-5554 reverse tcp:3000 tcp:3000
   adb -s emulator-5554 shell am start -n io.nativestarter.showcase.bluesky/io.nativestarter.showcase.bluesky.MainActivity
   ```
3. Run the JVM tests (tuners, rows, pager, rich text, thread traversal, view models and events, post interactions and rollback, XRPC headers, refresh and sign-out, fixture decoding):
   ```
   ./gradlew --no-daemon :app:testDebugUnitTest
   ```
4. Run the Compose UI and Keystore tests on emulator-5554:
   ```
   ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest
   ```
5. Release smoke build (signed with the local debug key so it installs; not a store configuration):
   ```
   ./gradlew --no-daemon :app:assembleRelease
   adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
   ```
   Launch it and check that the signed-out screen shows "Create account" and "Sign in" and no `e2eSignIn*` controls. Reinstall the debug build afterwards.

Fixtures in `app/src/test/resources/fixtures` were captured with curl from the seeded server. Access and refresh tokens are redacted.

Last measured results (emulator-5554, Android 16):

| Suite | Result |
|---|---|
| JVM tests (code `5368546`) | 109 tests, 0 failures |
| Compose UI and Keystore tests (code `1862ea3`) | 10 tests, 0 failures |
| Release smoke | `assembleRelease` passed; installed and launched: "Create account" and "Sign in" present, `e2eSignInAlice` and `e2eSignInBob` absent, not debuggable |

The instrumented tests need Espresso 3.7.0 and the matching AndroidX Test libraries. The Espresso version brought in by `ui-test-junit4` calls `InputManager.getInstance`, which Android 16 removed.

## Capture parity

From `android/parity`, with the server running:

1. Default screens: `maestro --device emulator-5554 test -e PLATFORM=android capture-native.yml`, then `./compare.sh 5%`.
2. Extra states (interactions, dark, font scale 2.0, empty, error): `./capture-states.sh io.nativestarter.showcase.bluesky native-android`, then `STATE=<state> ./compare.sh 5%`.

Each flow reseeds the server under `/tmp/bsky-seed.lock`. Commands, masks and results are in [PARITY.md](PARITY.md).

## Licensing

- Source code in this directory is a port of MIT-licensed code from bluesky-social-app.
- **Icons: Lucide** (ISC), `lucide-static@1.46.0`. The iOS app uses the same set. Licence: `licenses/Lucide-LICENSE.txt`. Conversion script: `tools/lucide2vd.py`. Glyphs: `arrow-left`, `bell`, `bookmark`, `camera`, `chevron-right`, `circle-arrow-up`, `circle-plus`, `corner-down-right`, `earth`, `ellipsis`, `eye-off`, `forward`, `hash`, `heart`, `house`, `image`, `info`, `menu`, `message-circle-more`, `message-square`, `pin`, `repeat-2`, `rotate-ccw`, `search`, `sliders-vertical`, `sparkles`, `square-pen`, `trash-2`, `triangle-alert`, `user-x`. The upstream icons in `assets/icons/` are the Central icon system, licensed to Bluesky only (ASSETS.md section 2), so they are not used.
- **Logo and default avatar:** Bluesky marks are trademarks (ASSETS.md section 3). The header shows a neutral placeholder mark (`ShowcaseMark`). The fallback avatar is drawn in code. The launcher icon is an original mark.
- **Font: Inter** (SIL OFL 1.1), `app/src/main/res/font/inter_*.otf`. Licence: `licenses/Inter-OFL.txt`.
