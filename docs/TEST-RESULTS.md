# AutoDrive validation

## Version 1.11.1 — confirmed, bounded Maps restoration

Validated 6 November 2026, `com.autodrive.launcher`, version code 28. [Display memory implementation and limits](MAPS-DISPLAY-MEMORY.md).

- **38 JVM tests passed**; debug and instrumentation APKs assembled. Android lint completed with **0 errors, 54 warnings and 1 hint**.
- **34 instrumented tests passed together in 120.122 seconds**, AOSP Android 13 / 800 × 480 / density 160. This includes the previous display-memory, radio, app drawer, phone, updater and retained-session checks, plus delayed provider handlers: initial display/location requests are ignored by the local fixture, later retries activate its real checkbox state, then requests stop after confirmation. Those fixture controls provide no map imagery or real GPS.
- A release-variant candidate assembled successfully and uses the established update certificate. Unlike the initial one-click implementation, it confirms the provider's checked state, bounds retries to three, and cancels restoration on manual interaction. Location retries are spaced five seconds apart after an initial delay; all restoration expires after thirty seconds.
- Earlier fixture attempts failed with a native `data:` URL or a Chrome error document on the live www.google.com origin. The corrected fixture uses test-only public request interception on the separately allowed maps.google.com origin with no-store, delegates the real BrowserPanel lifecycle, and waits for the initial blank document. Production origin restrictions remain enabled. Service-worker interference is a possible explanation for the www-origin conflict, not an established root cause.

- Before publication, the **non-debuggable release candidate / code 28** was installed in place only on the asserted disposable emulator. Three independent force-stop/restart runs passed strict assertions against the **actual Google Maps page**: Traffic and Satellite checked true, the real current-location control checked true. Runs one and three also cleared the ordinary HTTP cache and reloaded (no cookies or account storage were cleared). The three live test durations were 17.545 seconds, 9.690 seconds and 19.564 seconds. Provider location was supplied by explicit emulator GPS test fixes at Casablanca, not a real vehicle measurement. No test set the provider checkbox state or injected a fabricated navigation result.

The live provider audit is opt-in and separate from the 34-test combined suite. Google account login and Incarlink GPS remain unverified. Display restoration does not embed the native Maps Android application or Android Auto, nor restore account, sharing or trip state. No owner device was installed or changed.

## Version 1.11.0 — initial display memory; prerelease after signed cold-start audit

Validated 6 November 2026, `com.autodrive.launcher`, version code 27. [Display memory audit and limits](MAPS-DISPLAY-MEMORY.md).

- **38 JVM tests passed**, debug/test APK assembly passed, and Android lint completed with **0 errors, 54 warnings and 1 hint**. Six new JVM checks cover origin restrictions, public viewport sanitization, dropping tracking/authentication query parameters, extracting only a viewport from place/route URLs, supported map launch parameters, missing/corrupt input and starting with current provider location rather than a stale coordinate.
- **33 instrumented tests passed in the final combined run** on AOSP Android 13 / 800 × 480 / density 160 (**103.891 seconds**): the previous 30 checks and three display-memory checks. Real injected touches on local control fixtures exercise selection, persistence, new-document restoration, single replay, reset, untouched startup state, and excluding accounts/location sharing. Fixtures supply no real map or location and are never production assets.
- Initial fixture runs failed because `loadDataWithBaseURL` exposed a `data:` native URL despite its synthetic HTTPS DOM origin. The strict native-origin guard was retained. Test-only public request interception now serves those control documents while delegating the actual BrowserPanel lifecycle callbacks. A separate assertion was corrected to expect WebView's JSON-encoded `typeof` string. The corrected combined suite passed; no production origin checks were disabled.
- Separate denied-permission run: **one test passed** after actual emulator location permission revocation. The request callback waited for a result; denial and cancellation each completed it only once without retaining a grant. Android coarse/fine permission was restored afterward. Another regular check verifies the retained-grant flags for exact trusted origins and refusal of account origins.
- Separate write/read runs, with a force-stop between them, each passed **one process-boundary test**. Different PIDs and native private preferences verify that display choices survive another process, independent of web-document memory. The original display preferences were restored afterward.
- A live Google Maps audit first confirmed its actual French checkbox labels/ARIA states and traffic URL behavior. Real injected touches then enabled **Traffic, Satellite and Your location** on the actual provider page. The native saved preferences contained the selections and zoom, without writing the followed GPS coordinates. After force-stop, a new process restored **actual Google checkbox states** for Traffic/Satellite and the actual current-location control was checked true. Its restore logic attempted each option once. These are observations of the real provider controls, not the synthetic fixtures.
- The first live UI driver encountered repeated UiAutomator exits 137 while traversing Google's accessibility tree and a transient menu during reload. Live verification therefore locates provider controls using their DOM geometry and injects actual Android touch events through instrumentation. No Accessibility service or production automation permission is added. The opt-in audit class is not included in the normal 33-test suite.
- [Actual restored dashboard](../deliverables/maps-restored-1.11.0-800x480.png) was visually inspected. Satellite imagery, provider traffic colors, Google attribution and the active blue position control remain provider-rendered. The disposable emulator used explicit GPS test fixes at Casablanca; this is **not validation of the owner's real GPS, traffic accuracy or physical vehicle**. No turn-by-turn navigation, ETA or account login was fabricated or claimed.

The signed public code-27 APK passed the cross-update preference read, and Traffic/Satellite were restored. However, the stricter live current-location assertion failed on two cold starts before passing on a third, without changing source. Consequently v1.11.0 was marked **prerelease** and removed from stable latest discovery. This intermittent failure motivated 1.11.1: confirming provider activation and bounded retries instead of assuming one click completed the operation. The earlier debug success does not establish reliable signed cold-start recentering.

No Android permission or SDK dependency was added. Google account login and owner-device GPS behavior remain unverified. Unsupported provider controls are not replayed, and sharing, account, consent and automatic-trip actions are excluded. No owner box or phone was installed or changed.

## Version 1.10.0 — Quran radios, official SNRT access and app icon grid

Validated 6 November 2026, `com.autodrive.launcher`, version code 26. [Catalogue sources and explicit SNRT limitation](RADIO-CATALOGUE.md).

- **32 JVM tests passed**, debug/test APK assembly passed, and Android lint completed with **0 errors, 54 warnings and 1 hint**. Four new JVM checks cover the curated catalogue, Arabic/Latin search, readable names/personal labels and category-aware transport queues. No Android permission was added.
- **30 instrumented tests passed in the final combined run** on AOSP Android 13 / 800 × 480 / density 160 (**94.419 seconds**), comprising the previous 27 and two catalogue checks plus one app-grid check.
- All **twelve** MP3Quran/Qurango reciter streams independently reached ExoPlayer's actual playing state. A separate HTTPS probe read MPEG bytes from each source. These observations establish decoding/network access in this emulator session, not speaker quality on the Incarlink or perpetual stream availability. No recording was saved. The player identifies itself as AutoDrive, without browser impersonation or Origin/Referer overrides.
- The native catalogue UI checked four SNRT official-player entries, thirteen Quran-filter entries (twelve reciters plus Mohammed VI), Arabic names, Latin/Arabic search, category-aware next/previous, empty favorites and disabled skip for an empty queue. A defensive playback check ensures SNRT web pages are not passed to ExoPlayer or reported as playing native audio.
- An initial combined run failed two checks. One test tried to click a newly filtered card before GridView's layout completed; it now waits for that actual row. The other exposed a real queue-order bug: inserting the last selected station before curated entries changed their order after each selection. Known catalogue and saved entries now retain their order; an otherwise missing last station is appended afterward. A complete 20-entry wrap and rapid previous/next changes passed afterward.
- Visual inspection found overlapping app cards when initial four-column measurements were reused in a six-column layout. Initial columns now reflect the display, and an actual column-count change invalidates recycled cell measurements. The final app-grid test checks every visible cell's column width, horizontal bounds and non-overlap, alongside actual installed package metadata, icon views, search, empty results and reset.
- SNRT source audit: four official pages and logos returned HTTP 200. Legacy institutional HLS URLs returned HTTP 400; the current public player's unsigned manifest was rejected and a normal public token request returned HTTP 500. AutoDrive therefore provides **explicit official-player handoff**, not working native SNRT audio. No token extractor, signature fabrication, copied token or authentication bypass was added. The Quran/other native stations retain their actual playback controls.
- Final actual-touch checks passed for Quran playback, Latin search, retained Quran filter/bundled entries after an offline cold restart, and launching Android Settings from its real icon followed by Back to the Apps grid. A SNRT row opened the exact official Nationale URL in the installed browser; Back returned to Radio. Browser handoff was verified, **SNRT audio was not**.
- Actual captures were inspected at 800 × 480: [Quran](../deliverables/radio-quran-1.10.0-800x480.png), [SNRT official access](../deliverables/radio-snrt-1.10.0-800x480.png), [app icons](../deliverables/apps-grid-1.10.0-800x480.png), [Quran density 213](../deliverables/radio-quran-1.10.0-density213.png), [app icons density 213](../deliverables/apps-grid-1.10.0-density213.png). Transport/category targets at density 213 were completely visible and at least 63 physical pixels high (48 dp). Network settings and density were restored. The studio source card identifies the broadcaster/provider when no guide is integrated, rather than displaying an oversized missing-EPG message.
- Public [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42241854245) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42241873805) both succeeded for source `f7a1785`. Anonymous latest-release discovery resolves `v1.10.0`; the **11,448,797-byte / code 26** APK matches SHA-256 `53a713679857fb6a5dbc11ebb32ba82cde15d153add5668d547c8debfc7c8a36` and the established signer `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`.
- That exact public APK installed in place on the disposable emulator. An offline cold restart retained the selected العفاسي reciter and Quran filter without autoplay. After reconnecting, a real touch on Play reached actual stream playback, and the Apps page displayed its native icon cards. Settings were restored and the emulator was stopped. These checks did not install anything on an owner device.

Owner-device audio output, SNRT audio in its official player, all Morocco-directory streams, physical key forwarding and on-box layout remain unverified. The emulator's generated icons/app list are not a claim about the owner's installed applications. No owner box or phone was installed or changed.

## Version 1.9.1 — one-confirmation update workflow

Validated 6 November 2026, `com.autodrive.launcher`, version code 25. [Update workflow](ONLINE-UPDATES.md).

- **28 JVM tests passed**, debug/test APK assembly passed, and Android lint completed with **0 errors, 51 warnings and 1 hint**. Existing APK validation of package, increasing version, Android compatibility, size, hash and matching signing certificates remains in the real download/installer path.
- **27 instrumented tests passed together** on AOSP Android 13 / 800 × 480 (65.191 seconds): the previous 25 checks plus two controlled update-flow checks. They verify no download merely from finding a release, one offer per available version/session, duplicate-start protection, a retained ready-to-install request without a foreground consumer, single consumption to prevent installer loops, transport error without an install request, and successful explicit retry. Those controlled transport fixtures do not claim to validate APK authenticity.
- A separate actual-UI test used this implementation with an intentionally lower **1.8.0 / code 23 emulator-only build**. Its update source remained the real public GitHub repository, with the genuine signed **1.9.0 / code 24** APK as the newer candidate. This test build was not published as 1.8.0. The initial automation attempt used title-case dialog buttons; AOSP exposes them uppercase. Case-insensitive button lookup fixed the test driver, without changing production behavior.
- Actual touches selected **Plus tard**, reopened the offer through Settings, then pressed **OK**. The real APK downloaded and passed production checks, and Android's unknown-source authorization opened without a second AutoDrive Install tap. After enabling **Allow from this source** and returning, the actual system package installer opened automatically and offered to update AutoDrive. Its final installation confirmation was cancelled.
- Returning after cancellation did not reopen the installer. **Reprendre l’installation** explicitly reopened it, and a second cancellation again did not loop. Captures were inspected: [offer](../deliverables/update-confirmation-1.9.1-800x480.png), [actual installer](../deliverables/update-installer-1.9.1-800x480.png). The implementation's background/foreground guard and retained pending request are covered by code review and the controlled state check; no real network download was deliberately completed while an external app stayed foregrounded in this run.
- Public [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42236363661) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42236403805) succeeded for source `98562ae`. The anonymous **1.9.1 / code 25** download is **11,421,901 bytes**, SHA-256 `99d1a751e7db7a72929c5ba666aa08787112a12a27cb074756a61faf2e42c4d3`, matching metadata and the established certificate `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`. Anonymous latest-release discovery resolves `v1.9.1`.
- The lower-version test build then found the genuine public **1.9.1** release. A real **OK** downloaded it and opened the system installer automatically. The final system **Update** confirmation was accepted **only on the asserted disposable emulator**. Android reported installation success; reopening showed installed code 25 and retained Aswat selection and weekly guide. This completed installation used AutoDrive's updater and Android's installer, not an `adb install` of the public APK. Emulator settings were restored and it was stopped afterward.

No owner device was installed or changed. Android's final confirmation remains required. Process-kill recovery of an unfinished download, the Incarlink-specific permission/installer screens, and owner-device update behavior remain untested.

## Version 1.9.0 — radio studio and published radio programmes

Validated 6 November 2026, `com.autodrive.launcher`, version code 24. [Source audit, coverage and guide semantics](RADIO-EPG.md).

- **28 JVM tests passed**, debug and instrumentation APKs assembled, Android lint completed with **0 errors, 51 warnings and 1 hint**.
- **25 instrumented tests passed in the final combined run** on AOSP Android 13 / 800 × 480 / density 160 (59.362 seconds), including the existing radio transport, actual HTTPS playback, MediaSession, phone, retained-panel, Maps/YouTube preferences and synthetic session checks.
- The actual public Aswat HTTPS page returned **53 weekly slots across seven days**. Instrumentation verified parsing, private cache round-trip, native Programme rows, day changes/reset, disabling unsupported refresh, clearing the previous station's guide, and no automatic playback. These are published planning entries, **not confirmation of the programme actually broadcasting**.
- Nine new JVM checks cover source matching, Arabic/French/English day labels, valid/invalid clock values, scoped parsing, deduplication, overnight and week transitions, gaps, overlaps, next programme, planned progress and stale/future data. A guide older than 24 hours remains readable but cannot supply a current/next prediction. Corrupt cache data is ignored.
- Official Moroccan GMT rules effective 20 September 2026 were checked against the government communiqué linked in the audit. Initial calendar expectations using the previous GMT+1 rule were corrected. The implementation has an explicit current-rule fallback for old Android timezone databases; JVM and Android checks verify the same instant. It does not modify system time or settings.
- An initial cache round-trip check exposed nanosecond precision lost by JSON epoch-millisecond serialization; fetched timestamps now use that same precision before storage. The final round-trip check passed.
- Real keyboard events verified closing station search with Enter and reopening it; changing to Programme also closes the IME and restores transport controls. A known generic WordPress favicon is rejected by its exact byte hash, allowing station initials instead of unrelated branding; future replacement image bytes remain eligible.
- Manual actual-touch checks selected Aswat from search, tuned its HTTPS stream and displayed the published guide. A force-stop/restart with Wi-Fi and mobile data disabled retained readable recorded programme entries. At density 213, previous/next, day navigation and Source remained completely within the 800 × 480 display, with at least 63 physical pixels of height (48 dp). Network settings and density were restored. Captures were inspected: [studio](../deliverables/radio-studio-1.9.0-800x480.png), [programme](../deliverables/radio-programme-1.9.0-800x480.png), [density 213](../deliverables/radio-programme-1.9.0-density213.png). Offline availability applies to the saved guide, not live audio.
- Public [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42234252975) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42234268035) both succeeded for source `725f957`. Anonymous latest-release discovery resolves `v1.9.0`; its **11,417,873-byte / code 24** APK matches SHA-256 `aebab0b75eaf7c83777a15bbdf27f4ded8153333716687cafcf9156cd5cf772a` and the established certificate `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`.
- That exact public APK installed in place on the disposable emulator. Actual UI verification after an offline cold restart retained Aswat selection and the recorded weekly guide, without autoplay. Network/density/keyboard settings were restored and the emulator was stopped afterward.

The guide is currently integrated for **Aswat only**. Other stations explicitly report unavailable programme integration. No Google account was used; owner account persistence remains unverified. Incarlink audio quality, physical media keys, actual broadcaster schedule accuracy, Bluetooth calls and owner-device behavior remain untested. No installation was performed on an owner device.


## Version 1.8.0 — radio controls/catalogue and retained web documents

Validated 6 November 2026, `com.autodrive.launcher`, version code 23. [Radio audit](RADIO-AUDIT.md), [session audit](WEB-SESSIONS.md).

- **19 JVM tests passed**, debug and test APKs assembled, Android lint completed with **0 errors, 46 warnings and 1 hint**.
- **23 instrumented tests passed in the final combined run** on AOSP Android 13 / 800 × 480 / density 160: the previous 19 checks plus two radio and two retained-panel checks.
- A real 200-entry saved-library fixture no longer prevents selecting a new station. Actual injected touches exercised next, previous, play/pause, grid row selection and favorite filtering. The library remains at 200 entries while the last selected station persists separately. Single-favorite mode correctly disables skipping when it has only one entry.
- Public Android media-key dispatch selected the previous station through the active platform MediaSession. Controller pause/play callbacks, actions, state and metadata were verified against actual player events. This does **not** establish that the Toyota/Incarlink hardware keys are forwarded to Android as media keys.
- Real HTTPS streams for **HIT RADIO, Radio Mars and Radio Aswat** reached ExoPlayer playback independently of directory data. The first Aswat attempt failed: its published alias returns HTTP 302 to an HTTP address. A separately verified canonical HTTPS endpoint is now used only for that known alias; it played in the final run. Arbitrary HTTP downgrades remain disabled.
- Synthetic `.example` documents verified the **same WebView instances and sessionStorage** after Settings/Back, Radio/YouTube tab changes and an actual external Android Settings Activity round trip. A synthetic silent HTML audio fixture was playing before leaving, paused while hidden and remained paused on return. No Google credentials or accounts were used.
- Redirect fixtures verify that ordinary first-party Google website sign-in stays in its current WebView profile, while OAuth authorization routes show an explicit browser alternative. The browser button opens a separately identified browser profile; no authentication state is copied back. No real Google sign-in was attempted.
- An earlier combined run exposed two existing Maps fixture/startup timing failures. Those tests now isolate the actual BrowserPanel map client/theme on an initial blank document before installing their local controls/contrast fixtures, avoiding races against unrelated live Google startup redirects. Their account-control, branding, web-choice and pixel-contrast assertions remain. The final combined run passed.

- Actual UI checks with the directory cache removed and emulator Wi-Fi/data disabled confirmed that next changes the selected real station and previous returns to it using the bundled start list. The cache and network settings were restored. This verifies selection while offline, **not audio without Internet**.
- Real keyboard input `hit` filtered the expanded grid. Actual captures show the current 48-entry cached directory and available broadcaster logos: [dashboard](../deliverables/radio-1.8.0-800x480.png), [grid](../deliverables/radio-grid-1.8.0-800x480.png), [density 213](../deliverables/radio-grid-1.8.0-density213.png). At density 213 the transport/filter targets are fully visible with at least 63 physical pixels of height, corresponding to 48 dp. An initial UI hierarchy tool exited 137 during the density transition; a bounded retry after the new window settled completed the check. Density was restored afterward.

- Public release: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42223423072) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42223423904) succeeded for source `76467de`. The anonymous **1.8.0 / code 23** download is **10,920,157 bytes**, SHA-256 `0396938fe083703e7a9cdb5b0687d889067766d0891e89c488558dfdf19d5c0d`; its certificate matches the established AutoDrive signer `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`. Anonymous latest-release discovery returns `v1.8.0`.
- Persistent synthetic cookies were written before installing that exact signed public APK in place. A separate read-phase instrumentation run passed in the new process after the update, verifying both `.example` origins. This checks cookie durability across a package update, **not Google authentication**. Actual UI verification of the public APK retained Radio Mars selection and exercised next/previous by touch successfully. The disposable emulator was stopped afterward.

Unverified: the owner's real account persistence and whether Google accepts that device's WebView; live Google service revocation/expiration; all directory stations/logos; the Incarlink network and audio output; steering-wheel/head-unit key forwarding. No owner device was installed or changed, and no cookie/credential export, root, firmware modification or hidden API was used.


## Version 1.7.0 — Incarlink phone audit and contact access

Validated 6 November 2026, `com.autodrive.launcher`, version code 22. [Phone audit, manufacturer evidence and limitations](PHONE-INCARLINK-AUDIT.md).

- **19 JVM tests passed**, debug/test APK assemblies passed, and Android lint completed with **0 errors, 38 warnings and 1 hint**. Only `READ_CONTACTS` and visibility of the audited Bluetooth-phone package were added to the manifest; no `CALL_PHONE`, call-log, privileged Bluetooth or default-dialer access was added.
- **19 instrumented tests passed in the final combined run** on AOSP Android 13 at 800 × 480, density 160. Four new phone audit tests run with the 15 existing Radio/Phone/dashboard/YouTube/Maps/session tests. The final run was repeated after enforcing the user's requirement to avoid an automatic local/SIM-dialer fallback.
- The phone checks cover all twelve keypad keys, cursor positions, reversed selection, replacement near the length limit, missing cursor selection, backspace, long-press plus/clear, persistent typed draft and actual keyboard events. A real injected touch pressed the `5` key; another touch on the number field opened the Android IME. The emulator's hardware-keyboard IME display was enabled for this check.
- Real standard Contacts Provider fixtures with a clearly marked synthetic name and multiple phone numbers were inserted through instrumentation-only shell permission. They verify normalization, duplicate removal, search by name/formatted number, row selection into the keypad and automatic refresh after an actual provider update. All inserted raw-contact fixtures were deleted afterward. Production does not request `WRITE_CONTACTS`, seed contacts or ship this test data.
- Denied, empty and unavailable contact states were checked. The denied repository fixture proves that no query is attempted without permission. Explicit application choice, missing choice and automatic preference for the manufacturer's known component were checked as routing policy. **The OEM APK was neither installed nor invoked on the emulator.** Explicitly choosing the AOSP dialer verifies a standard `tel:` handoff, not Bluetooth calling.

- Additional actual-UI checks passed with emulator Wi-Fi and mobile data disabled: permission revoked without an automatic prompt, touch entry of `06555`, draft retention after force-stop/restart, and an honest empty-provider message after regranting Contacts. Network settings and density were restored afterward.
- Visual inspection at density 213 initially found the bottom phone action partly clipped. A compact landscape arrangement now removes helper text and gaps while keeping each key/action at 48 dp. The final 800 × 480 / density 213 check verifies at least 63 physical pixels of visible height for the tested primary targets, and the actual capture shows the full action. No target was shrunk to fit.
- Actual captures: [contact fixtures at density 160](../deliverables/phone-contacts-1.7.0-800x480.png), [empty-provider state at density 213](../deliverables/phone-1.7.0-density213.png). The emulator has no BT Phone installed, so it correctly asks to select a Bluetooth-phone application. These captures are not evidence of the owner's Bluetooth phonebook.

- Public release verification: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42216996346) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42216997567) both passed for source `ca92bb1`. The anonymous **1.7.0 / code 22** APK download is **10,902,345 bytes**, SHA-256 `5e220e306b06682031bf89e9120526f124c6ee53564231b08e6cdf31d9da78e1`, with the established signing certificate `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`. Anonymous latest-release discovery returns `v1.7.0`.
- That exact public APK installed in place on the emulator and retained the previously typed `06555` draft. The actual Phone page showed the absent-BT-Phone selection action and honest empty-provider state. An initial UI-tree read returned a null root; after waking the emulator display, bounded UI inspection passed. The emulator was stopped after verification. No real call was placed.

The real Google account session remains unverified as documented for 1.6.2. Bluetooth connection, PBAP import, the OEM phonebook's exposure through Android Contacts, clipboard paste in BT Phone, remote-phone dialing, incoming calls, microphone, audio routing and steering-wheel buttons remain **untested on the owner's box**. No real call was placed and no installation was performed on the owner's Incarlink or Samsung.

## Version 1.6.2 — web cookie persistence

Validated 6 November 2026, package `com.autodrive.launcher`, version code 21, on the local Android 13 emulator. [Session audit and important authentication boundaries](WEB-SESSIONS.md).

- Debug and Android test APK assembly, lint and **19 JVM tests** passed. Local lint: **0 errors, 39 warnings, 1 hint** before the final test-only process-evidence additions. CI checks the committed source again.
- **15 instrumented tests passed in one combined run**: two new web-session tests plus the 13 existing Radio/Phone/dashboard/YouTube/Maps checks. They verify cookie configuration and retained cookies/localStorage across destruction/recreation of both panel types. All session fixtures use reserved `.example` domains and synthetic values, without a real Google account.
- Separate write/read instrumentation calls, with `am force-stop` between them, verified persistent Secure/HttpOnly cookies on both origins across different application PIDs (2762 → 2909). The write and read phases each passed. The read phase observed retained DOM localStorage for only one of the two origins after an immediate hard stop. An earlier test requiring both DOM writes to survive failed; the process-boundary test now asserts cookie durability and reports DOM durability separately. This is a known limit, not a Google authentication success claim.
- Initial policy test invocation called a WebView-associated getter from the instrumentation thread; it failed and was corrected to execute that assertion on the UI thread. The production configuration already runs on the UI thread. The corrected suite passed.
- No cookie values, account IDs, passwords or actual Google authentication tokens are read or logged by production code. There is no cookie deletion, expiry extension, browser-cookie import, User-Agent spoofing, account credential capture or Google OAuth bypass in this change. No Android permissions were added.
- [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42212743584) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42212744098) completed successfully for source `25762a4`. The anonymous public download of **1.6.2 / code 21**, 10,876,601 bytes, matched SHA-256 `f490a55aa1dfb4f7c709ad906b563da05b30814ae23482aa0d31211a700067ac` and the existing AutoDrive signing certificate. Synthetic persistent cookies were seeded before installing that exact public APK; the separate read-phase test passed after the in-place update in a new process. Latest-release discovery returned `v1.6.2`. This checks cookie retention during an update, not Google account authentication.

Not verified: the owner's real Google sign-in, the reported logout on the Incarlink/Samsung, Google's acceptance of that WebView authentication flow, or persistent sign-in following Chrome/native-app authentication. The cookie/profile fix is implemented and tested; the user's complete Google account session remains an on-device validation item. No installation was performed on a device belonging to the owner.

## Version 1.6.1 — radio audit, artwork and station switching

Validated 6 November 2026, `com.autodrive.launcher`, version code 20. [Radio audit and causes](RADIO-AUDIT.md).

- **19 JVM tests passed**, including three queue regression tests. Debug APK and Android test APK assembled; lint completed with **0 errors, 39 warnings and 1 hint** before the final test-fixture synchronization changes. CI runs lint again on the published source.
- **13 instrumented tests passed in the final combined run** on the AOSP Android 13 emulator, 800 × 480 at density 160. Four new audit checks cover a one-station saved library versus a three-station catalogue, real HIT/Mars playback, both directions and wrap-around, rapid changes, empty/two-station favorites, buffering cancellation, search, bounded real-logo decoding/failure, and old-cache/deduplication metadata. The existing radio/phone, dashboard, YouTube and Maps checks also passed.
- Earlier combined runs exposed asynchronous test-page/window timing failures in existing YouTube/Maps tests. Their fixture helpers now wait for DOM readiness and rendering, and the contrast assertion waits for the actual foreground window pixels within a bounded deadline. The final combined run passed. No production Google Maps/YouTube page behavior was rewritten for those test waits.
- Actual touch input on the dashboard changed Radio Mars → Radio MFM → Radio Mars. MFM reached real ExoPlayer playback. The displayed list now contains image views and actual available artwork; a missing/refused logo uses initials. The public HIT logo loaded successfully; the Radio Mars artwork URL returned HTTP 403 during research. Not all catalogue logos or streams were validated.
- Manual full-list search `hit` with the Android keyboard visible showed HIT RADIO as the first result after the scroll-reset fix. At **800 × 480 / density 213**, the compact radio view displayed the station list, enabled previous/next controls and an accessible full-list button. A compact view can hide the separate station header and filters to preserve list space.
- A cold restart with emulator Wi-Fi and mobile data disabled loaded the cached 48-station list without autoplay. Network settings and density were restored after these checks. The existing 1.6.0 installation updated to the debug 1.6.1 APK with the same certificate; station/phone preferences were retained.
- Public release verification: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42210020826) and [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42210021511) both passed for source `74d4f19`. The anonymous download of **1.6.1 / code 20**, 10,874,905 bytes, matched SHA-256 `e5da093eaf3972e6287674a19954fd652b9b9587dbe288f07638ae1c8b681e38`. Signature verification matched the established AutoDrive certificate. The exact public APK installed in place on the emulator, retained Radio Mars selection, displayed the cached list and did not autoplay. Anonymous latest-release discovery returned `v1.6.1`.

Actual captures: [dashboard radio](../deliverables/radio-1.6.1-800x480.png), [expanded list](../deliverables/radio-list-1.6.1-800x480.png), [search with keyboard](../deliverables/radio-search-1.6.1-800x480.png), [density 213](../deliverables/radio-1.6.1-density213.png). These are emulator captures, not installed-box evidence. Audio output quality, on-box networking, background playback and steering-wheel controls remain unverified. No installation was performed on the owner's box or phone.

## Version 1.6.0 — Internet radio and phone

Validated 6 November 2026 on the local AOSP Android 13 emulator, 800 × 480. Package `com.autodrive.launcher`, version code 19. Only the explicitly selected emulator was modified; no installation or call was performed on the owner's Incarlink or Samsung.

- Debug assembly, Android test APK assembly, JVM tests and Android lint passed. **16 JVM tests**, **0 lint errors, 33 warnings and 1 hint**. No new contacts, call-log or calling permissions were added; the only manifest change is visibility of `ACTION_DIAL` handlers.
- Four radio/phone instrumentation checks cover station/favorite persistence and deletion, phone keypad and unavailable selected apps, actual HTTPS radio playback/pause/release and a real DNS error, and country/URL filtering plus a real directory fetch/cache comparison. HIT RADIO reached ExoPlayer's actual playing state; listening hardware/audio quality was not verified because the emulator runs without audio output.
- Final combined instrumentation run: **9 tests passed** (the four new checks plus five existing dashboard, YouTube and Maps preference checks). Manual catalogue selection of Radio Mars also reached the actual playing state and produced the published radio screenshot.
- The directory response produced **48 unique compatible Morocco URLs** at this test time after filtering. This is an observed directory snapshot, not a claim of all Moroccan broadcasters or a playback test of all 48 streams. Radio Browser is a community directory; HTTP-only and unusable URLs are not included.
- With emulator Wi-Fi and mobile data disabled, the Morocco catalogue opened from its local cache. The previous network settings were restored. Force-stopping/restarting AutoDrive preserved Radio Mars as the selected station and did not start playback automatically.
- Manual real UI check forwarded `123` from the keypad into the installed Android dialer with `ACTION_DIAL`; the number appeared in that dialer. No call was placed. External dialer Back behavior follows that application's stack; this test did not establish a return-to-AutoDrive guarantee for all dialers. AutoDrive's internal Back returned from the Phone page to the dashboard in instrumentation.
- Initial regression automation encountered the emulator's unrelated Intent Filter Verification Service crash dialog, interfering with injected input and screenshot sampling. The service was force-stopped on this emulator only and the existing five dashboard/YouTube/Maps checks then passed. A later combined run exposed a startup-page/fixture timing failure in the Maps preference test; it passed in isolation. The test now stops the real page before loading its local fixture and permits an eight-second bounded wait. No production Maps or YouTube behavior was changed for that test.
- The 1.5.1 → 1.6.0 debug package update succeeded with the same signing certificate. Saved media associations retain their existing keys; radio stations, last selection, typed number and phone favorites use private preferences. Startup does not automatically play radio. Backgrounding AutoDrive releases its audio player; internal Phone and Settings pages keep radio available.
- GitHub [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42198930144) and [signed release build](https://github.com/wacayoub/AutoDrive/actions/runs/42198958152) both completed successfully for source commit `b4717f2`. Public release metadata reports 1.6.0 / code 19, size 10,834,233 bytes and APK SHA-256 `bcf5bc79ce4f3f795c700ff9c46f8aee1446a3a000e2103befc9b6618dcf4860`. The anonymous public APK download matched its size/hash and its signature matched the established AutoDrive certificate. That exact downloaded APK then installed over the local test build on the emulator, preserving Radio Mars selection without autoplay. Anonymous GitHub latest-release discovery returned `v1.6.0`.

Actual screenshots: [radio](../deliverables/radio-1.6.0-800x480.png), [phone](../deliverables/phone-1.6.0-800x480.png), [Morocco catalogue](../deliverables/morocco-radio-1.6.0-800x480.png). Manual search for `mars` filtered the list to Radio Mars while the Android keyboard was visible. [Integration behavior and limits](RADIO-PHONE.md).

Not verified on the box: Bluetooth calling, microphone/audio routing, incoming calls, steering-wheel controls, tuner access and exact installed phone application support. Native Google Maps embedding remains unresolved and was not changed by this release. HLS remains supported by the existing Media3 dependency, but the new live-radio checks exercised MP3; do not treat every listed codec/stream as tested.

## Version 1.5.1 — restore interactive Maps on the home page

Validated 5 November 2026. Package `com.autodrive.launcher`, version code 18. The owner reported that 1.5.0's external application entry did not provide Maps on the home page and requested the former web behavior. The 1.4.2 Maps/YouTube dashboard, browser preferences and night display adaptation are restored. The native launch card and associated helpers/tests are removed. Version code increases so this installs over 1.5.0 without a downgrade or data clearing.

- Debug and Android test APK assembly, all **14 JVM tests**, and Android lint passed: **0 errors, 28 warnings, 1 hint**.
- Android 13 AOSP / **800 × 480 / 160 dpi**, day mode: **OK (5 tests)** covering two actual provider home URLs, panel layout/lifecycle, media tabs, map web preference, day/night display contrast and installed YouTube association. Night mode: **OK (3 tests)** for dashboard and Maps behavior. **600 × 1376 / 240 dpi portrait**: **OK (1 test)** for dashboard layout and lifecycle. The compact-icon assertion keeps the fractional-density rounding tolerance introduced in 1.5.0.
- Actual Maps rendering was visually inspected. A drag moved the map and a double tap zoomed it while YouTube remained beside it; Android still reported AutoDrive in the foreground. Real captures: [home](../deliverables/dashboard-1.5.1-800x480.png), [night](../deliverables/dashboard-1.5.1-night-800x480.png). The captures use real provider pages, not generated maps. The GPS header reflects the emulator location feed, not vehicle measurements.
- After the actual search field received focus and the keyboard appeared, entering **Mosquee Hassan II** and submitting returned Google's real map results and pins inside the left panel, with YouTube still alongside. [Actual search result](../deliverables/maps-search-1.5.1-800x480.png). The initial automated tap had occurred before the page was interactive; the search was repeated after confirming visible input focus. This is a place-search check, not a turn-by-turn guidance claim.
- An initial test run was obstructed by a system crash dialog from the emulator's **Intent Filter Verification Service** (`com.android.statementservice`). The unrelated service was force-stopped, foreground location permission was granted to the test app and the affected checks were rerun successfully. No app code was changed to suppress system failures. Display geometry and night setting were restored after tests.
- This restores the former website integration, not Android Auto or embedded native Maps guidance. Full voice guidance, live driving ETA/traffic, exact Incarlink WebView behavior and the owner's native media applications remain untested. No software was installed on the owner's hardware. [Integration boundaries](WEB-DASHBOARD-AUDIT.md).
- Both released-code workflows succeeded: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42119995026), [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42119995847). The public APK was downloaded anonymously; size, SHA-256, package, version code 18/name 1.5.1, minimum API 33 and original signing certificate were verified. Temporary foreground-location test permissions were revoked before stopping the emulator.

Published APK SHA-256: `d81f9722ebb3a4834a285fe82cfbf1977bc87eed1a087642883f88ea9eb9ae03`. [Release 1.5.1](https://github.com/wacayoub/AutoDrive/releases/tag/v1.5.1).

## Version 1.5.0 — replace Maps website with Google Maps Android

Validated 5 November 2026. Package `com.autodrive.launcher`, version code 17. The owner explicitly requested switching from browser Maps to the installed Google Maps Android application.

- Debug and Android test APK assembly, all **14 JVM tests**, and Android lint passed. Lint: **0 errors, 28 warnings, 1 hint**. The Maps WebView, its page script, color filter and obsolete web-preference tests were removed. YouTube remains a web panel with its installed-app association.
- Android 13 AOSP / **800 × 480 / 160 dpi**, Google Maps absent: **OK (4 tests)** for dashboard layout/lifecycle, explicit missing Maps state and both YouTube association tests. The dashboard contains one YouTube WebView and no Maps WebView; Radio/IPTV switch without recreating a Maps website. Missing Maps returns unavailable and exposes its installation action. No map website fallback is used.
- Installed the previously downloaded Google Maps **11.129.0103** APK on the disposable emulator. APK signature verification reported Google Inc. certificate SHA-1 `38918a453d07199354f8b19af05ec6562ced5788`. Native handoff instrumentation returned **OK (1 test)**, and another combined dashboard/handoff run returned **OK (2 tests)**. The actual Maps package reached the foreground. Its first-use sign-in screen was observed; no Google account was entered. This validates launching, **not destination selection, route guidance, GPS or Google Play services compatibility** on this AOSP image.
- Portrait **600 × 1376 / 240 dpi** dashboard test: **OK (1 test)**. Landscape **800 × 480 / 213 dpi** and **320 dpi**: **OK (1 test)** each. The native Maps action is checked for full visible height and an untruncated label. Dense layouts hide the explanatory paragraph, and very short layouts keep the action only. The icon-surface assertion allows half a dp for fractional-density pixel rounding.
- Real captures: [Maps installed](../deliverables/dashboard-1.5.0-800x480.png), [Maps missing](../deliverables/dashboard-1.5.0-maps-missing-800x480.png). They show native application entry states, not a simulated or embedded map.
- An emulator Home-role smoke check temporarily selected AutoDrive as Home, launched native Maps, pressed Android Home and verified AutoDrive was resumed. The original Home component was restored. The temporary Maps test installation was removed after testing; the emulator resolution/density were restored.
- Google Maps owns its native theme, navigation, permissions and account preferences. AutoDrive supplies no Android Auto surface or split-screen embedding. The Media Hub is not visible while Google Maps occupies the screen. Native Back behavior beyond the observed onboarding screen, actual Incarlink firmware, native navigation and the owner's media applications remain untested. No software was installed on the owner's hardware.
- The released code passed both GitHub workflows: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42117938640), [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42117939620). The actual public APK was downloaded anonymously; its metadata, size, SHA-256, package/version/minimum Android API and original signing certificate were verified.

Published APK SHA-256: `adabfad0619aab0cf2ce13d823e4726b52d1175b4a0cc231e6b40e9d7ca3ca42`. [Release 1.5.0](https://github.com/wacayoub/AutoDrive/releases/tag/v1.5.0).

## Version 1.4.2 — stronger navy theme, compact colored icons and day/night Maps display

Validated 5 November 2026. Package `com.autodrive.launcher`, version code 16. Native navy surfaces are more visible, with centered speed/GPS, actual connectivity indicators and colored shortcuts. Media tabs use centered 40 × 32 dp visual surfaces inside touch targets of at least 48 × 48 dp; the media chrome uses less vertical padding.

- Debug and instrumentation APK assembly, all **14 JVM tests**, and Android lint passed. Lint: **0 errors, 28 warnings, 1 hint**.
- Android 13 AOSP / 800 × 480 / 160 dpi, device day mode: **OK (5 tests)**. The dashboard verifies colored Phone icon, compact media surfaces, 48 dp touch targets, source selection, layout and lifecycle. Maps tests verify English/French launch invitations are hidden while branding/consent controls remain, that account controls are untouched, and that display contrast follows device mode. The existing two YouTube association tests also passed.
- The same emulator in device night mode returned **OK (3 tests)** for dashboard and Maps behavior. A controlled test-only contrast pattern verifies bright backgrounds become dark and dark foregrounds become bright in the rendered WebView. The test sets a known initial page scale and waits for a visual frame before sampling pixels; the fixture is not a production map.
- **600 × 1376 / 240 dpi portrait**: dashboard instrumentation returned **OK (1 test)**. The emulator was restored to 800 × 480 / 160 dpi and its original day setting afterward.
- Real website captures show the removed launch invitation and actual Maps rendering: [day](../deliverables/dashboard-1.4.2-day-800x480.png), [night](../deliverables/dashboard-1.4.2-night-800x480.png), [portrait](../deliverables/dashboard-1.4.2-portrait.png). Night adapts the complete map display through a native color matrix, including tiles and labels. It is not Google's native dark map style. Initial algorithmic WebView darkening left light tiles and was replaced before release. [Display integration details](WEB-DASHBOARD-AUDIT.md#142-display-preferences).
- No software was installed on the owner's Incarlink. Actual hardware rendering/performance, vendor theme signaling, GPS, YouTube Advanced and authorized IPTV playback remain hardware checks. Existing browser navigation and licensing limits remain in effect.
- Both release-code GitHub workflows succeeded: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42109177833), [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42109179215). The public release APK was downloaded anonymously and checked against update metadata: size, SHA-256, package, version code 16/name 1.4.2, Android 13 minimum and the original signing certificate.

Published APK SHA-256: `af2d2879fb1088f1a227ea4212acdba955215101effc05ba7466d6bd19e5774a`. [Release 1.4.2](https://github.com/wacayoub/AutoDrive/releases/tag/v1.4.2).

## Version 1.4.1 — Bleu profond and icon-only media tabs

Validated 5 November 2026. Package `com.autodrive.launcher`, version code 15. The owner selected design 3 (Bleu profond). The native interface now uses navy surfaces, rounded map/media frames, blue selection and icon-only Radio/YouTube/IPTV tabs. Provider website content is unchanged.

- Debug assembly and Android test APK assembly passed. All **14 JVM tests** passed. Android lint reported **0 errors, 28 warnings, 1 hint**.
- Android 13 AOSP emulator, **800 × 480 / 160 dpi**: instrumentation returned **OK (4 tests)** for dashboard layout/lifecycle, Maps web preference and both installed YouTube association tests. The dashboard test checks each icon-only tab has its accessible name and tooltip, a minimum 48 × 48 dp target and the correct selected state. It switches Radio → IPTV → YouTube, checks panel recreation, four unique bottom shortcuts, and no clipped shortcut labels.
- The same dashboard instrumentation returned **OK (1 test)** at **600 × 1376 / 240 dpi portrait**. This also checks stacked full-width panels and visible shortcut labels. The emulator was restored to 800 × 480 / 160 dpi afterward.
- Real captures: [800 × 480 landscape](../deliverables/dashboard-1.4.1-800x480.png), [phone portrait](../deliverables/dashboard-1.4.1-portrait.png). These are app captures rather than generated proposals. Hardware-specific WebView behavior, real GPS, the owner's YouTube Advanced app and IPTV subscription remain untested. No software was installed on the owner's Incarlink.
- Both GitHub workflows succeeded for the released code: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42105858319), [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42105859050). The actual public APK was downloaded anonymously and its size, SHA-256, package, version and original signing certificate were verified.

Published APK SHA-256: `a93ef145e36ed13ac5d8137db4ce4f3054f199d556ca208563dc68a101bf3cae`. [Release 1.4.1](https://github.com/wacayoub/AutoDrive/releases/tag/v1.4.1).

## Version 1.4.0 — public GitHub releases and in-app updates

Validated 5 November 2026. The public repository is [wacayoub/AutoDrive](https://github.com/wacayoub/AutoDrive), with a published [1.4.0 release](https://github.com/wacayoub/AutoDrive/releases/tag/v1.4.0). Package `com.autodrive.launcher`, version code 14.

- Local release assembly, **14 JVM unit tests**, and Android lint passed: **0 errors, 28 warnings, 1 hint**. Both GitHub workflows also succeeded: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42100805260) and [Signed Android release](https://github.com/wacayoub/AutoDrive/actions/runs/42100864446). The latter built, signed and published the actual public APK and update metadata.
- On the Android 13 / 800 × 480 emulator, instrumentation returned **OK (5 tests)**. These covered the existing dashboard, map web preference and YouTube association behavior, plus APK verification accepting the correctly signed candidate and rejecting a different signing key, an incorrect hash and inconsistent version metadata.
- For the update journey, a dedicated version-code-13 test build containing the updater was installed first. Its Settings page discovered the live public GitHub release, downloaded the real release APK and verified it. Android requested source authorization and then displayed its normal update confirmation. After confirmation, package inspection reported version code 14. The chosen application association survived the update, and the installed 1.4.0 release reported **AutoDrive est à jour**. [Actual installed-release screenshot](../deliverables/updates-1.4.0-800x480.png)
- Earlier distributed versions do not contain this updater. Users must install 1.4.0 or later once; subsequent releases can be checked, downloaded and offered to Android from Settings. No silent installation is claimed. The original signing identity was preserved; private signing material is excluded from the public source.
- The end-to-end installation used only the emulator. The Incarlink's installer policy, network and firmware behavior remain untested. No software was installed on the owner's hardware. This release does not change the documented Maps/YouTube web integration boundaries.

Published APK SHA-256: `20a095a405b1cd9139ef4d20c9c85147b7dbaf8c11f5daeade233c0f20d75dbf`. Signing certificate SHA-256: `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`.

## Version 1.3.2-web-preview — choose the installed YouTube app

Debug assembly, all 11 existing unit tests and lint passed: **0 errors, 25 warnings, 1 hint**. The Android 13 / 800 × 480 emulator instrumentation returned **OK (3 tests)**: the existing dashboard layout/lifecycle test plus the two new YouTube selection tests.

- The actual Settings picker selected the emulator's launchable **Clock** app as a fixture. Its label updated and the selection survived force-stop/relaunch. **Ouvrir l’application YouTube** then opened that selected fixture. The dialog's **Effacer le choix** action cleared the preference. The screenshot shows the reset state. [Settings capture](../deliverables/settings-1.3.2-800x480.png)
- A synthetic YouTube-origin test page supplied an explicit Open App intent. A real injected test touch triggered the production browser handler and the selected Clock app reached the foreground. Normal Maps/YouTube page URLs and the two-panel dashboard lifecycle remained covered by the dashboard instrumentation test. The synthetic page belongs only to the test APK and is not used by the shipped launcher.
- The selection test verifies a new AppLinks instance reads the saved component, missing selections remain saved and return unavailable, clearing removes the override, and account/lookalike-host URLs are rejected for native YouTube handoff. An app that cannot handle a supported YouTube URL falls back to its normal launcher activity.
- The exact YouTube Advanced/ReVanced build on the owner's box is **not installed in the emulator and remains untested**. Tests establish the generic installed-app selection/launch mechanism. The dashboard continues displaying the official YouTube website; no native app screen embedding or account transfer is claimed. No software was installed on the owner's hardware.

Package `com.autodrive.launcher`, version code 13. APK SHA-256: `cb360c42289b431d1d2cae8a9febb1c7e95c41888dd1a55999156e6b34984e7b`.

## Version 1.3.1-web-preview — responsive display and web preference

Debug assembly, all 11 existing unit tests and lint passed: 0 lint errors, 25 warnings, 1 hint. Instrumentation passed **2 tests on 600 × 1376 / 240 dpi portrait** and **2 on 800 × 480 / 160 dpi landscape**. Checks include native label ellipsis, 48 dp shortcut height, stacked/side-by-side panel geometry, panel lifecycle, delayed Maps web-choice selection in English/French and no action on a Google account page. Real map startup and portrait YouTube search/keyboard layout were visually checked. The exact owner's device remains untested. See [detailed changes, captures and limitations](DISPLAY-FIXES-1.3.1.md).

APK SHA-256: `b7aefc0d37c5137125de2f82bb94c7b1f1e07222e85c89d1b5dbe57621a96491`.

## Version 1.3.0-web-preview — actual website panels

Validated 5 November 2026 on the 800 × 480 / 160 dpi AOSP Android 13 emulator, Android System WebView 101.0.4951.61. No Google Maps app, YouTube app or Google Play services were needed for the observed website rendering. No software was installed on the owner's Incarlink.

- Final debug build and **11 existing unit tests passed**. Android lint: **0 errors, 25 warnings, 1 hint**. APK package `com.autodrive.launcher`, version code 11; existing debug signing certificate verified. The exported HOME/launcher entry is now `WebDashboardActivity`.
- The new `WebDashboardTest` passed on the emulator. It verifies two browser panels at the actual Maps/YouTube home URLs, no Add Video button, exactly four bottom shortcuts with 48 dp minimum height, Radio/YouTube switching, browser release when opening Settings, and browser recreation after Back. This test validates panel/lifecycle behavior rather than remote content availability.
- Live Google Maps rendered its actual map and attributions. A touch drag changed the visible map area and a double tap zoomed in, with YouTube video still visible beside it. Searching **Mosquee Hassan II** returned real results and opening a result showed the **Mosquée Hassan-II** place details with Google's own controls. [Real map/video capture](../deliverables/dashboard-1.3.0-web-live-800x480.png)
- Live YouTube Home rendered inside the right panel. The website initially showed a search prompt in the anonymous session. Searching **Android Developers**, scrolling and selecting a video opened the watch page in the same panel. The video rendered changing frames and its player displayed elapsed time **0:11 / 25:40** with the pause control. This verifies streaming video in this emulator run, not every video, format or the owner's hardware. [Playback capture](../deliverables/dashboard-1.3.0-web-playing-800x480.png) · [Search capture](../deliverables/dashboard-1.3.0-web-search-800x480.png)
- The initial keyboard layout was too short for useful search; the final build handles IME insets explicitly and temporarily hides the native header/tabs/footer during typing. The final Maps search field and suggestions remained visible above the keyboard and the dashboard controls returned afterward.
- The offline/retry smoke script disables both emulator networks, checks that both panels show a missing-page message and retry control while the four shortcuts remain available, then restores networking and captures the retried real pages. See `tools/web_dashboard_smoke.py` and [home capture](../deliverables/dashboard-1.3.0-web-home-800x480.png).
- A UIAutomator hierarchy dump crashed in Android's `AccessibilityNodeInfoDumper` during one live dynamic-page inspection. The app remained running. Native instrumentation, touch interaction and screenshots were used to complete the UI checks.
- **Not validated:** exact Incarlink WebView/firmware behavior; native Maps handoff on the box; live GPS recentering in the web map; full route-selection flow; turn-by-turn guidance; live driving ETA/traffic; Google account login/personalized YouTube Home; IPTV subscription playback or Toyota tuner integration. The website's native-app prompts remain intact. The browser preview does not resolve the authorized Google Navigation SDK requirement. [Integration boundaries](WEB-DASHBOARD-AUDIT.md)

Final APK SHA-256: `1c93b51482b06a797391d0f8690a2b3a0eb748ff0e1c528f4e45dc378425cc23`.

## Version 1.2.0 — refreshed dashboard and YouTube sharing

Validated 5 November 2026. Debug assembly, all **11 unit tests**, and Android lint completed successfully with JDK 17. Lint: **0 errors, 22 warnings, 1 hint**. The APK retains package `com.autodrive.launcher` and the existing debug signing certificate, version code 10.

- All six Android 13 AOSP smoke groups passed at **800 × 480 / 160 dpi**: clean installation; missing Maps and media apps; four unique shortcuts with minimum 48 dp touch targets; tab switching and persisted tab; external radio handoff using the emulator clock app as a fixture; GPS permission denied and GPS disabled; offline state; app drawer; Back and Home with the original Home selection restored.
- Instrumentation runner returned **OK (4 tests)**. Executed checks validate share-to-dashboard, a visible official YouTube iframe without autoplay, minimum 200 × 200 dp player viewport, tabs retained above the player, return from Settings, switching away from and back to YouTube, and touch delivery to the hosted map view. The HLS test was skipped because no stream URL was supplied; this release makes no new HLS playback claim.
- A separate **800 × 480 / 213 dpi** check passed: the player uses the entire media panel to preserve its minimum viewport, with no player clipping. In this compact mode, use Media or Back to return to the tabs. Density was restored after the check.
- [Real dashboard screenshot](../deliverables/dashboard-1.2.0-800x480.png) shows the missing-Maps/no-selected-video state on the emulator. [Reference comparison](../previews/compare-1.2.0.html) preserves the supplied reference photo and places the real screenshot beside it; no synthetic map or video is used.
- The Google Play image did not reach a usable connected emulator state during this run. Actual Google widget traffic, zoom behavior and remote YouTube video playback were **not validated for 1.2.0**. An iframe being present does not establish successful video playback. Provider restrictions, connectivity and the box's WebView still need validation.
- No software was installed on the owner's Incarlink. The exact box density/insets, widget permissions, native Home routing, codec behavior and YouTube/Google service compatibility remain hardware checks. The map remains Nearby Traffic; inline destination selection and turn-by-turn guidance are unresolved. A selected YouTube video uses the official embedded player and does not reproduce the official YouTube application's browse screen.

APK SHA-256: `abf0abd03ce4231baf7824413e658cd4e1c0fd5eac7fa1fe845acdcb8b261079`.

## Version 1.1.7

- Debug assembly, all ten unit tests and all six Android 13 AOSP smoke groups passed. Android lint reported zero errors, 22 warnings and one hint.
- The Android 13 instrumentation test injected a tap and verified that it reached the view hosted in the map panel.
- The 800 × 480 Google Play emulator upgraded in place from 1.1.6 and reused a single bound Nearby Traffic widget. Google kept the widget in its loading state, so the native zoom and Google activity launch were unavailable to verify in this run. [Widget screenshot](../deliverables/dashboard-1.1.7-widget-800x480.png)
- APK SHA-256 is `928ac2b50e9ccc08de393ac97ce1467da72e9779d625be8eef0ca320f554c60a`. The package and signing certificate match 1.1.6.

## Version 1.1.6

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 22 warnings and one hint.
- On the 800 × 480 Android 13 Google Play emulator, AutoDrive requested consent on first binding and reused the existing binding on restart. The final build also removed an orphan binding from an older build. Android reported one AutoDrive widget binding after the upgrade.
- With AutoDrive as the Android Home app, repeated taps and a swipe on the widget kept AutoDrive fullscreen and launched no Maps activity. Google supplied the loading view. Live traffic was unavailable during the test. [Widget screenshot](../deliverables/dashboard-1.1.6-widget-800x480.png)
- The Android 13 AOSP suite verified that the map panel stayed on the dashboard when Maps was unavailable. The physical Incarlink widget and its traffic availability still need verification.
- APK SHA-256 is `c41c32282c90b9322100fffd08fbc1dab149289f0e82b27b315d49fad27294bd`. The package and debug signing certificate match 1.1.5.

## Version 1.1.5

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 25 warnings and one hint.
- The 800 × 480 Android 13 AOSP smoke suite passed all six groups, including missing Maps browser handoff, no separate map buttons, offline state, GPS permission, and Android Home and Back. [AOSP dashboard](../deliverables/dashboard-1.1.5-800x480.png)
- The Android 13 Google Play emulator upgraded in place from 1.1.4 with a bound Nearby Traffic widget. The upgraded dashboard used the interactive Maps entry point. Starting AutoDrive through Android Home produced a real Home task, and tapping the panel then placed a regular AutoDrive task and Google Maps in two visible Android windows.
- On that emulator, the optional Nearby Traffic widget could still be enabled from Settings and removed by switching back to interactive Maps.
- In the Maps window, double-tap and drag zoomed in and out. Native search found Golden Gate Bridge, and Start displayed genuine guidance, a route line and an ETA with the Media Hub beside it. The route used an emulator location. [Guidance capture](../deliverables/dashboard-1.1.5-guidance-800x480.png)
- Outside active navigation, pressing Android Home returned to the dashboard and another tap reopened both windows. During active guidance, Home returned to the dashboard with Google Maps in Android picture-in-picture. Tapping the map panel while Maps was pinned did not restore the split in that test.
- The 1.1.5 APK uses the same package and signing certificate as 1.1.4. SHA-256 is `3cc146e1b33c166accb4ffac9f36819b4725c5fb80cec8205852744cb828cbd1`. The physical Incarlink split and Home-key behavior remain unverified.

## Version 1.1.4

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 25 warnings and one hint.
- The 800 × 480 Android 13 AOSP smoke suite passed all six groups. It verified that the three launcher map buttons were gone and that tapping the map panel offered a browser handoff when Maps was absent. [Dashboard capture](../deliverables/dashboard-1.1.4-800x480.png)
- On the Android 13 Google Play emulator, the Nearby Traffic widget filled the left panel. Google reported “Can't show nearby traffic” and a tap on the widget opened official Maps fullscreen. After removing the widget through Settings on the emulator, a tap on the same panel opened official Maps next to AutoDrive in two visible Android windows. [Split-screen capture](../deliverables/dashboard-1.1.4-maps-800x480.png)
- In the adjacent Maps window, native search found Golden Gate Bridge. Tapping Start displayed Google's next turn, route line and ETA while the Media Hub remained visible. This used the emulator location. [Guidance capture](../deliverables/dashboard-1.1.4-guidance-800x480.png)
- Follow-up audit with the same APK and the widget removed: selecting AutoDrive as Android Home and launching it with the Home key produced a `type=home` task. Tapping the panel opened Maps in a split-screen stage, with no visible AutoDrive task or Media Hub after Maps loaded. The earlier successful split used AutoDrive as a regular app. The emulator's previous Home app was restored. [Pre-release audit](NAVIGATION-AUDIT.md#pre-release-audit-of-the-fullscreen-tap-in-114)
- The APK upgrades 1.1.3 with the same package and signing certificate. SHA-256 is `fff2fc67785d1d170f00914d7841223eb5db7753816bbd301715ed4a1552965b`. Split-screen and widget behavior on the physical Incarlink still need hardware verification.

## Version 1.1.3

- Debug assembly and ten unit tests passed. Android lint reported zero errors, 29 warnings and one hint.
- On the Android 13 Google Play emulator, **Explore** opened official Maps beside AutoDrive. **Find place** found Ferry Building in a separate adjacent Maps window with a map pin, details and native route actions.
- Zoomed in and out using Google's double-tap-and-drag gesture on the Maps pane at 800 × 480. [Map and place detail screenshot](../deliverables/dashboard-maps-800x480.png)
- The AOSP emulator opened the browser fallback for map display, place search and directions with the Maps app absent.
- APK package and signing certificate match 1.1.2 for an in-place upgrade. SHA-256 is `f7e3a5aea89a790a3a68945a38a166ed8f3537e0098f3285a43cd11e843e9863`.

## Version 1.1.2

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 27 warnings and one hint.
- On the Android 13 Google Play emulator, the dashboard IPTV player opened and rendered an authorized HLS test stream after an explicit Play action while the simulated GPS speed was 56 km/h.
- The Android 13 AOSP smoke suite passed all six groups, including opening the YouTube and IPTV setup dialogs without location permission.
- APK package and signing certificate match 1.1.1 for an in-place upgrade. SHA-256 is `54ba69dba127f007c7fee1504cc95a2cb4d4cb9528811c53ed6a63d05ec69052`.

## Version 1.1.1

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 27 warnings and one hint.
- On the Android 13 Google Play emulator, opening a fresh launcher triggered Android's widget consent directly. After approval, Nearby Traffic appeared in the expanded map panel and was restored after force-stop.
- With an emulator-provided stationary GPS reading, the YouTube tab accepted a video on the first visit. Reopening the tab brought back the saved video player after parked confirmation without re-entering the link.
- The Android 13 AOSP smoke suite passed all six groups, including offline and missing-app fallback, media tab persistence, parked gating and Android Home/Back.
- The APK uses the same package and signing certificate as 1.1.0. SHA-256 is `730f8f7dfd4a0896da6a37eeedf3610bc70c9744110792f4cdf33f474acd205d`.

## Version 1.1.0

Tested on 4 November 2026 with Android 13 AOSP and Google Play emulators at 800 × 480. The updated APK installed over 1.0 with the same package and signing certificate.

| Check | Result |
| --- | --- |
| Build and lint | Debug APK and instrumentation APK built. Android lint reported zero errors, 25 warnings and one hint |
| Unit tests | Ten passed, including four new tests for supported video links and invalid URL rejection |
| Instrumentation | Two passed. The YouTube view created the official embed with autoplay disabled. Media3 prepared and rendered the public Shaka HLS test stream and playback advanced after Play |
| AOSP smoke suite | Passed the six groups covering startup, missing apps, offline state, GPS unavailable, parking gate, tab persistence, Home and Back |
| YouTube on dashboard | Verified actual playback in the right panel after loading the link, confirming parked and tapping Play. The screen capture shows the official player rendering video |
| Nearby Traffic widget | Google Maps 11.129.0103's widget was bound through Android's Create dialog and restored after APK upgrade. The widget displayed its own location prompt and loading screen. Traffic tiles did not finish loading on the emulator during this test |
| Directions from the dashboard | Entered a destination in AutoDrive. Official Maps and AutoDrive opened in separate adjacent Android windows. Maps showed an active route, the next turn and ETA while AutoDrive's Media Hub stayed visible. [800 × 480 navigation capture](../deliverables/dashboard-navigation-800x480.png) |
| IPTV Smarters | The assigned Smarters app is an external option. Direct HLS playback was verified with a public test stream, independent of a Smarters account |
| Movement while playing | On the dashboard, the HLS test stream reached four seconds of playback at an emulator-reported 0 km/h. After the emulator supplied a moving GPS fix, the player closed and the Media Hub returned |
| APK SHA-256 | `c199fdfca0c8f42e9d8b5159a3d1e098ba07af5bc1befdcdf91d488f57f134f6` |

The device-specific Inlink widget implementation, widget traffic tiles on the box and playback of the owner's actual videos and IPTV channels remain unverified.

## Version 1.0 baseline

Test date 4 November 2026. The starting workspace contained the approved dashboard photo. AutoDrive is a new Android Studio project. The existing local Android toolchain was reused for development. All installations and Android configuration changes during validation targeted isolated emulators.

### Build

| Check | Result |
| --- | --- |
| Gradle Wrapper 8.13, JDK 17, compile SDK 35 | Build completed using the supplied wrapper |
| `:app:assembleDebug` | Passed. Package `com.autodrive.launcher`, version `1.0.0`, minimum API 33 |
| `:app:testDebugUnitTest` | Six tests passed. GPS conversion, smoothing, stationary detection, accuracy, stale readings and resets |
| `:app:lintDebug` | Zero errors, 16 warnings and one hint. The build retains the verified SDK and dependency versions and uses a landscape preference for the target dashboard |
| APK signature | `apksigner verify` passed for the debug APK, APK Signature Scheme v2 |
| APK SHA-256 | `88ab5aaef93315c651c2088bebefc848d42e7ea78b9ac9eb82dece24feb9e305` |

### Emulator coverage

Android 13 / API 33 x86_64 at 800 × 480 landscape, with an AOSP image for absent-package and no-Play-services coverage and a Google Play image for genuine Maps and YouTube package coverage.

| Scenario | Observed result |
| --- | --- |
| Clean installation, location permission absent | Launcher opened without forcing a permission request. The readout displayed `--` and reported precise location off |
| GPS disabled | With location permission granted and Android location turned off, the launcher reported GPS disabled and displayed `--` |
| Offline | With emulator Wi-Fi and mobile data disabled, the launcher stayed responsive and reported internet unavailable |
| Google Maps missing | Display and destination actions offered web Maps. The emulator browser received the map URL and the driving-directions URL. This verifies browser handoff |
| Google Play services missing | AutoDrive opened normally and the AOSP Settings page reported Play services unavailable |
| API key absent and embedded licensing unresolved | Both emulator paths ran without credentials. The dashboard used the external navigation panel |
| Official Google Maps installed | **Open Maps** brought Google Maps 11.129.0103 to the foreground. The driving action delivered `google.navigation:` to the official Maps package. After the Maps first-use notices, the app entered active driving guidance and displayed the next maneuvers, distance and ETA. The route used emulator-provided San Francisco coordinates |
| Split-screen | Android placed AutoDrive and official Google Maps in separate adjacent windows on the Google Play AVD |
| Radio missing or unassigned | The Radio tab reported that an app was not selected. Selecting Android's Clock as a launch fixture verified external app handoff. A separate attempt with the AOSP Calendar app triggered a crash in Calendar and returned to a responsive AutoDrive |
| YouTube missing | The AOSP Media Hub displayed YouTube unavailable and disabled the playback controls |
| Official YouTube installed, speed unavailable | The Google Play AVD recognized the official YouTube app. The launch path showed the parked-viewing restriction when the launcher lacked a trustworthy speed reading |
| IPTV missing or unassigned | The IPTV tab reported that an app was not selected. An assigned launch fixture was blocked by the parked-viewing restriction while speed was unknown |
| Media tab switching and restart | All three tabs could be selected. The selected IPTV tab survived force-stop and relaunch |
| App drawer, Android Back and Home | The drawer opened, Back returned to the dashboard, and Android Home returned to AutoDrive when selected as the emulator's default Home. The previous Home app was restored |
| 800 × 480 layout | Captured and visually reviewed the dashboard at 160 dpi and 200 dpi. At 160 dpi the four bottom shortcuts were each at least 48 × 48 dp. The design comparison shows the supplied dashboard crop and a real 160 dpi emulator capture |

The current dashboard capture shows version 1.1 playing an official YouTube embed on the Google Play emulator. The Nearby Traffic widget is loading on the left. [Dashboard capture](../deliverables/dashboard-800x480.png) · [Side-by-side comparison](../deliverables/dashboard-comparison.png)

Repeat the AOSP smoke checks on a disposable AVD. The script reinstalls AutoDrive and temporarily changes emulator connectivity, location and Home settings.

```powershell
python tools/emulator_smoke.py --serial emulator-5554 --apk deliverables/AutoDrive-debug.apk --screenshot dashboard-test.png
```

## Remaining hardware validation

- Exact X15-SLM927-72 Android package inventory, Maps and Google Play services versions and compatibility, GPS speed accuracy, stale fixes and course quality. Live driving speed was not measured on vehicle hardware.
- Google Maps routing in the target region, spoken guidance, real traffic, offline routes and route resumption. Visual turn-by-turn was verified with an emulator location. Vehicle movement and audio were outside that test.
- The box's reversible Home picker, ignition boot and wake behavior, physical Home and Back routing, Android split-screen under projection, display density and usable insets. Emulator results do not establish hardware behavior or performance.
- Vendor radio, Toyota FM tuner access, favorites, phone audio and Bluetooth routing. The actual radio and IPTV apps must be selected and tested on the box.
- Real YouTube and IPTV playback, media metadata, transport controls, HLS codecs, DRM, buffering and recovery depend on the selected apps. No live media session or IPTV stream was verified in this run.
- The parked restriction gates launches and playback-start requests from the launcher. It requires GPS and a user confirmation, because a gear-position signal was unavailable. Behavior of external video after handoff remains under the external app's control.
