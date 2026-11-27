# Zad Al-Momen: fixed requirements / متطلبات ثابتة

These come from the user. They apply to every future build. Re-verify each one before any release, and add every new note from the user here.
دي طلبات المستخدم. بتتطبق على كل نسخة جاية. يتأكد منها قبل أي إصدار، وأي ملاحظة جديدة تتضاف هنا.

## Mushaf / المصحف
1. Looks like the printed Madinah Mushaf: 15 lines per page, ayah markers, hizb/rub, juz, surah headers, sajdah, page number.
2. Large font, page filled to the full width and height, no wide gaps. Side margin about 7 px. Quran text is never changed (line breaks come from the data).
3. Short pages (last pages): same font as normal pages, natural spacing, centred. Pages 1 and 2 (user note 1:42 AM, "make the text here bigger"): the largest font where the widest line fits in the safe margins (96% of the inner width), about 35 px at 360 wide; every line stays on one line, no marker overlap, no horizontal scroll.
4. Header pills stay clear of the Android status bar (safe-area inset plus 40 px header).
5. No marker (quarter/half/hizb badge, juz, sajdah, page badges) may overlap or hide any letter. Badges sit in the gap above a line or in the side margin, never over text. Check every page (rub-px.js: 0 overlaps on all 604 pages).
6. Text size above 100% is capped in the printed mode.

## Whole app / كل البرنامج
7. No zoom anywhere: pinch, double-tap, ctrl-wheel. Viewport user-scalable=no, touch-action pan-y.
8. Every screen fits the screen width. Vertical scroll only when content is long. Never horizontal scroll or content wider than the screen (check 320, 360, 412 wide). Bottom tab labels must not be clipped.
9. Back button works on every screen, sheet and overlay and goes one level up: podcast full player, podcast show page, podcast search results, library section, settings group, Mushaf options sheet, reader, dialogs.
10. On main screens a single Back never exits. A second Back within 2 seconds exits, and the first shows "اضغط مرة أخرى للخروج".

## Podcast / البودكاست
11. No animation, transition or movement in podcast screens, the mini player or the full player.
12. The seek bar works on the full player and the mini player: tap and drag jump to that point and play from there. Needs a host that supports HTTP Range.

## Radio / الإذاعة
13. Radio must be loud enough: volume at full in the player, plus a "تقوية الصوت" switch (+9 dB with a peak limiter at -2 dB via DynamicsProcessing on Android 9+, fallback LoudnessEnhancer +6 dB; on by default). The stream itself is quiet (about -24 LUFS, peak -6.5 dBFS). Bench simulation: about -15.4 LUFS, peak -1.9 dBFS. Only the live Egyptian radio is used (user: "I want the live one"); no reciter stream option.
14. Mushaf font: each page uses the largest font that fits the width (the user chose this, like his page 5 screenshot).

## Updates / التحديثات
15. Same package, same signing key, higher version. User data is kept. Publish to main only after the user's explicit OK.

## Regression checks (tests/)
- ov.js: horizontal overflow at 320/360/412 on all 9 screens.
- fs-px.js: Mushaf font per page (360 and 412 wide) and page fill.
- seek-px.js with rs.py (Range server): seek by drag/tap, full and mini player.
- rub-px.js: no marker overlaps any word on all 604 pages.
- back-px.js: back-button scenarios and double-back exit.
- app.js: headless Chrome runner with real device metrics (360x768 at DPR 2).

## Version / الإصدار
16. (user note 2:00 AM, "why does the version stay 1") The build sets Android versionCode AND versionName from the CI run number, and Settings shows "الإصدار N" at the bottom. Check after each build: the APK's versionName equals the release tag number.

## Verse sheet / تفسير الآية (user, 2:26-2:28 AM)
17. LONG-PRESS (about half a second) any verse in the Mushaf (user decision 2:43 AM): one bottom sheet opens by itself, nothing to choose. A normal tap goes back to the old behavior: it only shows or hides the Mushaf bar. The sheet has a button "تشغيل الآية وعلامات" that opens the old verse card (play, marks, copy). Tested in tests/td-px.js (tap does not open, long press opens, the click after it does not toggle the bar, a moving press does not open). Order: the verse, المعنى المختصر (الميسر), تفسير السعدي, تفسير ابن كثير, مكان النزول, سبب النزول. The source is written under every part. Texts are shown unchanged from the sources (only the double-bracket footnote marks are hidden). If there is no authentic sabab, the sheet says so in one line.
18. The sheet scrolls inside itself (touch-action pan-y), no horizontal scroll, no zoom, Back closes it (tests/td-px.js).
19. Data: الميسر, Makki/Madani and sabab al-nuzul are packed into the APK at build time (tools/build_td.py, from the public sources, build fails if they are incomplete). السعدي and ابن كثير load per surah on first use and are cached on the phone.
20. آية اليوم on Home: one full verse per Cairo day from a fixed list of 30 well-known verses, text taken from the Mushaf data; tapping the card opens the same sheet. PENDING sheikh review: the verse list, sabab wording/sources, Makki/Madani verse-level exceptions, an approved "aim" line for the daily verse, the unfiltered Ibn Kathir passages.

## Radio keeps playing in the background (user, 2:50 AM)
(Item 21 was "أثر آية": out of the current build by the user at 2:55 AM, see docs/DESIGN_WORK.md: active design work, kept out of release builds. Number kept free.)
22. Quran radio must keep playing when the app is closed (swiped from recents) or the screen is off, like a normal radio. A foreground media service (RadioService, type mediaPlayback) shows an ongoing notification "إذاعة القرآن الكريم · بث مباشر" with an "إيقاف" button; the stop button and the in-app button both stop the radio and remove the notification. The player is not released when the activity is destroyed. On reopening the app the radio button shows the real state (tests/radio-bg.js). CI fails if the manifest lacks the service or the FOREGROUND_SERVICE_MEDIA_PLAYBACK permission. NOT verifiable without a real phone: battery optimization (some makers kill background audio), lock screen behavior, Android 13+ notification permission denied, audio focus when the adhan plays.

## Share buttons / المشاركة (user, 2:30-2:56 AM)
23. Every content type has a share button: verse (in the verse sheet), hadith (reader), dhikr/dua/tasbih (text and image), Fajr campaign, seerah chapter, prayer times, daily wird, podcast episode. Check each after every build (tests/share.js; the podcast and hadith-reader paths are only checked as present, not executed).
24. Every share = a short description + content quoted exactly from the app's own data + the app name and download link (frame line "من تطبيق زَادُ الْمُؤْمِنِ / تحميل التطبيق: <link>").
25. Verse share: the verse plus a verbatim 1-2 sentence quote from التفسير الميسر with the source name. Never paraphrased.
26. Dhikr share: the dhikr, its count and its source. A benefit line appears ONLY if a hadith was verified with book and number (currently 2 entries: Bukhari 6405 / Muslim 2691 and Bukhari 3293 / Muslim 2691). No unverified benefit line is ever shown. PENDING sheikh review of these lines and of any additions.
27. Fajr campaign share has no day count. Wird share with 0 pages read today shows a toast and does not share.
28. Image shares carry the same text as the text share.

## Home tiles and nav icons (user, 3:52-3:55 AM)
29. Home shows 4 illustrated tiles in a 2x2 grid (رجالة الفجر، يومك في زاد المؤمن، القرآن الكريم، أثر آية), drawn as inline SVG (no extra download). No figure on the Fajr dome. Each tile still opens the same screen as before. The آية اليوم card is unchanged (checked byte-for-byte against the previous build). (Update 8:15 AM: the user approved أثر آية, see item 31.)
30. The bottom nav has colorful icons in the option-A style for all 6 tabs (الرئيسية، المصحف، الأذكار، المكتبة، المسبحة، الإعدادات); the selected tab has a gold ring. Tab names, order and behavior are unchanged.

## أثر آية / private verse notes (user approved 8:15 AM)
31. A 4th illustrated tile "أثر آية" opens a page "آثار القرآن". Notes are private: saved on the device only (local storage key athar-v1), never uploaded. User request 08:35: each note has a share button (image in the same share frame as the other cards) showing the note text plus the verse reference only, as stored; no verse text, no tafsir, nothing invented. Test: tests/athar_share.js. Online sync is NOT built (user agreed it is later). The app writes no tafsir or religious text for it: only the user's words plus the verse text taken from the Mushaf data (the verse is stored as surah and ayah numbers, never copied).
32. New note: pick the verse (آية اليوم, من آخر صفحة قرأتها, or any surah and ayah), pick one of 4 types (فهمت إيه، هطبّق إيه، أثّرت فيا إزاي، دعاء استلهمته منها), write text (required, up to 1000 characters), save. Notebook: grouped by date, filter by type, tap a note to edit, delete needs a second tap to confirm, empty state with one sentence. Back closes the sheet first, then leaves the page. Test: tests/athar.js.
33. The آية اليوم card is untouched. Backup/export and sync are not included (export is not built, so uninstalling the app loses the notes).
34. User request 08:36: home tiles are shorter (96px high, 2x2). Home page is about 100px longer than b83 (two rows instead of one). The user decided at 08:58 (relayed by the parent) to keep 2x2; a one-row variant was dropped. Athar Aya stays local only: no login, no sync, no accounts.
35. User request (relayed 09:30): custom dhikr inside the existing Adhkar section, not a separate section. A new chip "أَذْكَارِي" in the Adhkar chips. The user adds his own dhikr (text exactly as typed, up to 300 characters), sets the count (1 to 1000), and optionally a daily reminder time. Counter per dhikr resets daily like the others. Edit and delete (delete needs a second tap). Stored only on the device (localStorage mydhikr-v1). We author no religious text; only UI labels. Test: tests/mydhikr.js.
36. Reminders for custom dhikr use the same native notification scheduling as the other reminders (next 14 days, channel "reminders", title "أَذْكَارِي", body = the dhikr text). Needs the notification permission; works in the Android app only. NOT verified on a real phone.
37. User request (15:19): آية اليوم shows ONE tafsir, preferably التفسير الميسر. Tapping the card opens a sheet with the verse and only التفسير الميسر (مجمع الملك فهد), with its source line. السعدي and ابن كثير are removed from this sheet only (they are still in the Quran reader). مكان النزول and سبب النزول stay, each with its source. Quran text unchanged. Muyassar text is bundled offline; the 30 daily verses were compared word for word with spa5k/tafsir_api ar-tafsir-muyassar and matched (30 of 30).
