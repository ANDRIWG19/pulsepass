package com.pulsepass.repository;

import com.pulsepass.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    // Buscar usuario por email ignorando mayúsculas/minúsculas (sección 14).
    Optional<User> findByEmailIgnoreCase(String email);
}
