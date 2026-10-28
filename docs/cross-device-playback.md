# Cross-device playback

## Policy

The latest valid playback-token request wins for a user. Creating a new session revokes the user's previous sessions. PostgreSQL transaction-scoped advisory locking serializes concurrent requests across Music Service instances.

The token remains short-lived, song-specific, and stored as a SHA-256 hash. Private Cloudflare R2 objects are still served through the existing authenticated HLS endpoint; a browser notification is not an authorization boundary.

## Endpoints

- `POST /song/{songId}/playback-session`: preferred endpoint to create a playback session and revoke older sessions. The response includes `token`, `sessionId`, `expiresAt`, and `songId`.
- `GET /song/{songId}/playback-token`: legacy compatibility endpoint; migrate clients to POST because creating a session revokes older sessions.
- `GET /song/{songId}/playback-status`: returns `{"active":true|false}` for the current token. Response caching is disabled.
- `GET /song/{songId}/playback-events`: authenticated Server-Sent Events (SSE). It sends `playback_taken_over` with `{"active":false,"reason":"SESSION_REPLACED"}` after the token is revoked. A heartbeat is sent every 15 seconds while active.

All endpoints use the existing trusted gateway identity headers `X-User-Id` and `playBack-token`. The gateway must authenticate the caller, remove any client-supplied identity headers, and set these headers itself. Do not expose the Music Service directly to untrusted clients.

## Browser client requirements

Use `fetch()` with the same trusted identity/token headers to consume the SSE stream; native `EventSource` cannot set arbitrary request headers. When a `playback_taken_over` event arrives:

1. Pause the HTML audio element immediately.
2. Destroy the HLS.js instance and cancel pending playlist/segment requests.
3. Clear the old token from player state.
4. Do not automatically resume with the old token.

When the app reconnects, it must request a new playback token before playing. If the old token is supplied to the events endpoint, the server immediately reports takeover rather than restoring that session.

## Example SSE client

```javascript
async function watchPlaybackEvents({ songId, userId, token, onTakeover }) {
  const controller = new AbortController();
  const response = await fetch(
    `/song/${songId}/playback-events`,
    {
      headers: {
        "X-User-Id": String(userId),
        "playBack-token": token,
        "Accept": "text/event-stream"
      },
      cache: "no-store",
      signal: controller.signal
    }
  );

  if (!response.ok || !response.body) {
    throw new Error(`Playback event stream failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");

      let boundary;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const eventName = frame.match(/^event:\s*(.+)$/m)?.[1];
        const dataLine = frame.match(/^data:\s*(.+)$/m)?.[1];
        if (eventName === "playback_taken_over") {
          onTakeover(dataLine ? JSON.parse(dataLine) : { active: false });
          controller.abort();
          return;
        }
      }
    }
  } finally {
    reader.releaseLock();
  }

  return () => controller.abort();
}
```

The client should abort this stream when playback stops or the page is disposed. It should reconnect with a newly issued token only after the user explicitly starts playback again.

## Operational notes

- SSE is one-way (server to browser) and uses a database-backed active-session check every second. This lets a player connected to another service instance detect revocation without a shared in-memory event bus.
- Keep the SSE endpoint and HLS requests behind the same gateway authentication. Never put playback tokens in query strings.
- The polling status endpoint is a fallback for clients that cannot keep an SSE stream open.
- Validate deployment behavior and run the Maven tests before merging.
