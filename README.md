# Pagefeed

A reader that turns books into small chunks of text you scroll through, or turn like real pages.
Design follows Google Play Books (dark). Offline. Android 8+ (built for Galaxy Note 10+, Android 12).

## Build the APK (GitHub Actions)
1. Create a new GitHub repo and push this folder to it (keep `.github/workflows/build.yml`).
2. Open the repo > **Actions** > **Build APK** > wait for green > download the **Pagefeed-apk** artifact.
3. Unzip, copy `app-release.apk` to the phone, install (allow "install unknown apps").

## First run
- Library > **Add book** (EPUB / PDF / TXT / MD), or in a file manager tap the file > **Open with > Pagefeed**.
- Notes need **All files access** (Android asks via a dialog the first time you highlight). Notes are written to
  `/storage/emulated/0/notes from books/<Book title>.md`, one file per book, rewritten on every change.

## Reading
- Tap centre: toolbars. **Aa**: font, size, spacing, alignment, theme, brightness, night light.
- Menu (three dots): bookmark, switch between **feed** (vertical) and **pages** (page-curl).
- Long-press a paragraph: highlight, add note, copy, bookmark. Highlights are whole paragraphs/chunks.
- Chunks are paragraphs; paragraphs over ~400 characters are split only between sentences.
- Page numbers are virtual (1 page = ~1,800 characters) so bookmarks and notes stay stable when you change font size.

## Hidden reading record
Settings (profile icon on Home/Library) > tap **About** 5 times > **Reading activity**.
Daily goal is set in Settings; a snackbar appears in the reader when you reach it.

## Third-party
Fonts: Literata, Merriweather, Goudy Bookletter 1911, Vollkorn (SIL OFL, see FONTS-LICENSE-OFL.txt).
Libraries: PageCurl (Apache 2.0), jsoup, PdfBox-Android.
