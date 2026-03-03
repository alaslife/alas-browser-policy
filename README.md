# ALAS Browser (Open-Source Snapshot)

ALAS Browser is an Android browser project (`com.sun.alasbrowser`) with a custom UI layer and WebView-based browsing engine.

## Included in this public repo
- App UI and Compose screens
- Browser shell and navigation logic
- WebView integration and settings
- Theme/resources and reusable components

## Not included
- Signing keys (`*.jks`, `*.keystore`)
- Firebase config secrets (`google-services.json`)
- Local machine files and logs

## Legal and policy
- In-app legal screen includes Privacy Policy, Terms, and Security disclosures.
- Hosted policy page: `https://alaslife.github.io/alas-browser-policy/`
- Google privacy policy: `https://policies.google.com/privacy`

## Build
Use Android Studio or Gradle:

```bash
./gradlew :app:assembleDebug
```
