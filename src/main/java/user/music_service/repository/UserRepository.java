package user.music_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {

}