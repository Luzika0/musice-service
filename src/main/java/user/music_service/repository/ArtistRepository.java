package user.music_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {

    Artist findByArtistName(String artisName);

    Page<Artist> findAllOrByArtistNameContainingIgnoreCase(String artistName, Pageable pageable);

}
