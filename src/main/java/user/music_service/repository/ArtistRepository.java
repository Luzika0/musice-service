package user.music_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import user.music_service.model.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {

    Artist findByArtistNameAndAlbumName(String artisName, String albumName);

    @Query("SELECT a FROM Artist a WHERE :searchText IS NULL OR :searchText = '' " +
            "OR LOWER(a.artistName) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
            "OR LOWER(a.albumName) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    Page<Artist> searchArtistsAndAlbums(
            @Param("searchText") String searchText,
            Pageable pageable);
}
