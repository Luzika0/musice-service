package user.music_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Table(name = "permission")
@Entity
@Data
@EqualsAndHashCode(callSuper = false)
public class Permission extends BaseEntity {
    @Id
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission")
    private String permission;

    @Column(name = "desciption")
    private String desciption;
}
