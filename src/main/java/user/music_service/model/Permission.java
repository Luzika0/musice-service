package user.music_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Table(name = "permission")
@Entity
@Data

public class Permission {
    @Id
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission")
    private String permission;

    @Column(name = "desciption")
    private String desciption;
}
