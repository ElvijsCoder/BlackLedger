# Black Ledger: build with GitHub Actions

Every push to `main` builds a signed APK and publishes it as a GitHub release.
No Android Studio. Black Ledger has **its own** signing key, separate from Black Sand's.

## 1. Make the signing key (once, on your PC)
Needs Java (`keytool`). The alias must be `blackledger`; use one password for both prompts.

```
keytool -genkeypair -v -keystore blackledger.jks -alias blackledger -keyalg RSA -keysize 4096 -validity 10000
```

Turn it into text for GitHub (PowerShell):

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("blackledger.jks")) | Set-Content blackledger-base64.txt
```

Back `blackledger.jks` up with the other keys. Never put it in the repo (`.gitignore` already blocks `*.jks`).

## 2. Create the repo
- New GitHub repo, e.g. `BlackLedger`. Push this folder with GitHub Desktop.
  Check the hidden `.github` folder made it.
- Settings → Secrets and variables → Actions → New repository secret:
  - `KEYSTORE_BASE64` = contents of `blackledger-base64.txt`
  - `KEYSTORE_PASSWORD` = the password
- Delete `blackledger-base64.txt`.

## 3. Build
Actions → "Build APK" → Run workflow (or just push). When it's green the release shows up under Releases.

## 4. Obtainium
Add app → the repo URL. It follows the `v0.1.N` releases.
First install: allow Obtainium to install apps.

## 5. First run on the 3a
- Allow notifications when asked (briefing + alerts need it).
- Settings → Apps → Black Ledger → Battery → **Unrestricted**. Nothing OS can otherwise delay alarms.
- Add the widget: long-press home → Widgets → Black Ledger.

## 6. Check it works
- New directive, weekly, today ticked, alert 2 minutes from now → alert arrives; *Clear* from the notification clears it in the app and the widget.
- Widget *Shop* tab → add items in the app → tick / cross from the widget.
- End trip → N/A items offered for carry-over → new requisition holds only those.
- Next morning: briefing at 07:30; anything recurring you skipped yesterday is in the Missed log.
