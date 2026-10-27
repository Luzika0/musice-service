package user.music_service.repository;



import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {

}
