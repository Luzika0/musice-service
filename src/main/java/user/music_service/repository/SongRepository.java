package user.music_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import user.music_service.model.Song;

public interface SongRepository extends JpaRepository<Song, UUID> {

    @Query(
        value = """
            SELECT s.*
            FROM songs s
            LEFT JOIN artist a ON a.artist_id = s.artist_id
            WHERE (
                NULLIF(TRIM(CAST(:searchText AS text)), '') IS NULL
                OR CAST(s.song_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
                OR CAST(a.artist_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
                OR CAST(a.album_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
            )
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM songs s
            LEFT JOIN artist a ON a.artist_id = s.artist_id
            WHERE (
                NULLIF(TRIM(CAST(:searchText AS text)), '') IS NULL
                OR CAST(s.song_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
                OR CAST(a.artist_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
                OR CAST(a.album_name AS text) ILIKE
                   '%' || CAST(:searchText AS text) || '%'
            )
            """,
        nativeQuery = true
    )
    Page<Song> searchSongs(
            @Param("searchText") String searchText,
            Pageable pageable);
}
