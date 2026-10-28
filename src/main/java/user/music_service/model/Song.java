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

    /**
     * R2 object key for the HLS master playlist, not the original audio.
     */
    @Column(name = "url_song")
    private String urlSong;

    /**
     * R2 object key for the original cover image.
     */
    @Column(name = "song_image")
    private String songImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "song_type", nullable = false)
    private SongType songType;
}
