package user.music_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import user.music_service.model.Song;

public interface SongRepository extends JpaRepository<Song, UUID> {

    @Query("""
            select s
            from songs s
            left join Artist a on a.artistId = s.artistId
            where :searchText is null
               or lower(s.songName) like lower(concat('%', :searchText, '%'))
               or lower(a.artistName) like lower(concat('%', :searchText, '%'))
               or lower(a.albumName) like lower(concat('%', :searchText, '%'))
            """)
    Page<Song> searchSongs(
            @Param("searchText") String searchText,
            Pageable pageable);
}
