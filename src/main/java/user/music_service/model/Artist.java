package user.music_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "artist")
@Data
@EqualsAndHashCode(callSuper = false)
public class Artist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer artistId;

    private String artistName;

    private String albumName;

    /** Private R2 object key, e.g. music/artistImage/12/cover.jpg. */
    @Column(name = "artist_image")
    private String artistImage;

    /** Private R2 object key, e.g. music/albumnImage/12/cover.jpg. */
    @Column(name = "album_image")
    private String albumImage;

    private String status;
}
