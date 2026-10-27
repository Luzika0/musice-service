package user.music_service.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;
import user.music_service.enums.SongType;

@Data
@Entity(name = "songs")
@EqualsAndHashCode(callSuper = false)
public class Song extends BaseEntity {

    @Id
    @Column(name = "song_id", nullable = false, updatable = false)
    private UUID songId;

    private Integer artistId;

    private String songName;

    private String urlSong;

    @Enumerated(EnumType.STRING)
    @Column(name = "song_type", nullable = false)
    private SongType songType;
}