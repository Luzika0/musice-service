-- Fix PostgreSQL text columns used by SongRepository.searchSongs().
-- Run this once against the same database/schema used by Music Service.
-- JPA maps these fields as Java String, so they must be text/varchar rather than bytea.
-- Existing bytea values are assumed to contain UTF-8 encoded text.

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND data_type = 'bytea'
          AND (
              (table_name = 'songs' AND column_name = 'song_name')
              OR (table_name = 'artist' AND column_name IN ('artist_name', 'album_name'))
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I TYPE text USING convert_from(%I, %L)',
            c.table_name,
            c.column_name,
            c.column_name,
            'UTF8'
        );
        RAISE NOTICE 'Converted %.% from bytea to text', c.table_name, c.column_name;
    END LOOP;
END $$;

-- Verify the resulting column types:
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND (
      (table_name = 'songs' AND column_name = 'song_name')
      OR (table_name = 'artist' AND column_name IN ('artist_name', 'album_name'))
  )
ORDER BY table_name, column_name;
