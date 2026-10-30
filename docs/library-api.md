# Account library API

All routes are mounted at `/library` in Music Service and are exposed to Angular through the Auth Service gateway prefix `/api/auth/library/**`. The gateway strips the first two path segments before forwarding. Requests require the trusted `X-User-Id` header populated by the authenticated gateway; clients must not be trusted to choose another user's ID.

Responses use the existing envelope `{ "statusCode": 200, "message": "...", "data": ... }`.

## Favorites

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/library/favorites` | List the signed-in user's liked songs |
| POST | `/library/favorites/{songId}` | Like a song (idempotent) |
| DELETE | `/library/favorites/{songId}` | Unlike a song (idempotent) |

Song IDs are UUIDs from the Music Service catalog.

## Playlists

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/library/playlists` | List the signed-in user's playlists |
| POST | `/library/playlists` | Create a playlist |
| GET | `/library/playlists/{playlistId}` | Get one playlist and its ordered song IDs |
| PUT | `/library/playlists/{playlistId}` | Rename playlist and/or update description |
| DELETE | `/library/playlists/{playlistId}` | Delete playlist and its tracks |
| POST | `/library/playlists/{playlistId}/songs/{songId}` | Add a song |
| DELETE | `/library/playlists/{playlistId}/songs/{songId}` | Remove a song |
| PUT | `/library/playlists/{playlistId}/songs` | Replace order with a JSON array of all song UUIDs |

Create/update JSON:

```json
{
  "name": "Road Trip",
  "description": "Songs for the drive"
}
```

Reorder body:

```json
[
  "song-uuid-one",
  "song-uuid-two"
]
```

The reorder operation requires every song currently in the playlist exactly once. Playlist IDs are scoped to the authenticated user; another user's playlist is returned as not found. When an admin deletes a catalog song, its favorite rows and playlist entries are removed and the remaining playlist positions are compacted. The schema is managed by JPA schema update in the current Music Service configuration and creates `user_favorites`, `user_playlists`, and `playlist_songs` tables.

## Angular usage

- Home, Discover, Search, Now Playing: favorite buttons call POST/DELETE favorites.
- Liked Songs: GET favorites, then remove with DELETE.
- Library: GET playlists, create with POST, edit with PUT, delete with DELETE, add/remove tracks with the nested song routes, and reorder with PUT playlist songs.
