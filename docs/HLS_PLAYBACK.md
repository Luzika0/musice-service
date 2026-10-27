# Private HLS playback (first integration stage)

This branch adds a protected HLS delivery path while retaining the existing `GET /song/{songId}/play-music` endpoint as a fallback.

## What it adds

- `POST /song/{songId}/hls/session`: validates the existing `playBack-token` and `X-User-Id` headers, then sets a short-lived `HttpOnly` `HLS_SESSION` cookie. Only the hash of the cookie value is stored in PostgreSQL.
- `GET /song/{songId}/hls/audio.m3u8`: returns the playlist only for a valid user/song/session.
- `GET /song/{songId}/hls/{segment-file}`: validates the same session before returning a segment.
- `POST /song/{songId}/hls/package`: ADMIN-only ZIP upload to private R2 under `hls/{songId}/`.

The package ZIP must contain `audio.m3u8` and every referenced `.ts` segment at the ZIP root. Public R2 access is not required. The original audio object and existing `/play-music` endpoint are unchanged.

## Create a package in PowerShell

After generating HLS files in `$HOME\hls-music`, create a ZIP containing only the playlist and segments:

```powershell
Compress-Archive -Path "$HOME\hls-music\audio.m3u8", "$HOME\hls-music\segment*.ts" -DestinationPath "$HOME\hls-music\hls-package.zip" -Force
```

Do not include `input.mp3` in the ZIP.

Upload the ZIP through the same API gateway path used by your other Music Service endpoints. Use your authenticated admin session and send the existing gateway-required `X-User-Id` context:

```powershell
curl.exe -X POST "https://YOUR_API_HOST/api/auth/song/YOUR_SONG_UUID/hls/package" `
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" `
  -F "file=@$HOME\hls-music\hls-package.zip"
```

Replace the host, UUID, and access token with your own values. Do not commit tokens or credentials.

## Browser playback flow

1. Call the existing `GET /song/{songId}/playback-token` with your normal authentication.
2. Call `POST /song/{songId}/hls/session` with the returned token in the `playBack-token` header. The browser stores the HttpOnly cookie from `Set-Cookie`.
3. Play `/song/{songId}/hls/audio.m3u8` with an HLS-capable player. Requests must use the same site so the browser sends the cookie. If using HLS.js, configure its requests to include credentials when cross-origin; configure exact-origin CORS and credential support on the gateway if needed.
4. Keep `/song/{songId}/play-music` as a fallback until browser playback and expiry behavior are tested.

Production defaults to a Secure cookie. For local HTTP-only testing, set `HLS_COOKIE_SECURE=false`; never disable Secure cookies in production. Cookie path defaults to `/`; override `HLS_COOKIE_PATH` only if your gateway routing requires a narrower path.

## Important limitations of this first stage

- This stage serves HLS segments behind session authorization but does **not** add AES-128 segment encryption or commercial DRM.
- Do not upload a playlist containing an `EXT-X-KEY` directive yet; the service rejects encrypted packages until a private key-delivery endpoint is implemented.
- HLS is not DRM by itself. Segments can still be captured by an authorized client. True Widevine/FairPlay DRM needs a compatible license service/provider and additional frontend integration.
- Existing uploaded source audio is not modified or re-encoded by this feature.
- The service currently reads each requested segment into memory before responding. This is suitable for the short generated segments in the current test, but production should use bounded streaming and rate limits.
