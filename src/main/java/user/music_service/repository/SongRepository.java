package user.music_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.Song;

public interface SongRepository extends JpaRepository<Song, UUID> {

}
