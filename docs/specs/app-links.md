# App links (invite links `https://ransel.app/j/<code>`)

Values the backend agent needs for **B4.6** (`/.well-known/...` on `ransel.app`). The frontend declares the
link in the Android manifest (`autoVerify`) and handles it in `MainActivity` / `InviteLinks`.

## Android: `/.well-known/assetlinks.json`

Package name: `app.ransel`

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "app.ransel",
    "sha256_cert_fingerprints": [
      "6D:B4:D9:01:2A:30:E5:1E:11:01:9A:AF:2D:FC:41:E2:D8:CC:9C:06:6F:AB:8C:55:2E:6E:87:DD:C7:6C:C7:D6"
    ]
  }
}]
```

That fingerprint is the **debug** keystore on the developer machine. Add the **release / Play App Signing**
SHA-256 before shipping; links do not verify on release builds without it. (Owner: needed.)

## iOS: `/.well-known/apple-app-site-association`

Served as `application/json`, no extension, no redirect.

```json
{ "applinks": { "details": [{ "appIDs": ["<TEAM_ID>.app.ransel"], "components": [{ "/": "/j/*" }] }] } }
```

`<TEAM_ID>` is the Apple developer team id (owner: needed). The bundle id is assumed to be `app.ransel`;
confirm when the iOS project is opened on a Mac. The app also needs the Associated Domains capability
`applinks:ransel.app` and a call to `InviteLinkHook.INSTANCE.open(url.absoluteString)` from `.onOpenURL`.

## Web

The web app reads `/j/<code>` from the page path, so `ransel.app/j/*` must fall back to the web app's
`index.html` (single-page fallback on the host).
