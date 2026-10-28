package user.music_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import user.music_service.model.Song;

public interface SongRepository extends JpaRepository<Song, UUID> {

    @Query(value = """
            select s
            from songs s
            left join Artist a on a.artistId = s.artistId
            where (
                cast(:searchText as string) is null
                or trim(cast(:searchText as string)) = ''
                or lower(s.songName) like lower(concat('%', cast(:searchText as string), '%'))
                or lower(a.artistName) like lower(concat('%', cast(:searchText as string), '%'))
                or lower(a.albumName) like lower(concat('%', cast(:searchText as string), '%'))
            )
            """)
    Page<Song> searchSongs(
            @Param("searchText") String searchText,
            Pageable pageable);

}
