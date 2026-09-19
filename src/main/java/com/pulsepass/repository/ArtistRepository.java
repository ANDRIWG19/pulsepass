package com.pulsepass.repository;

import com.pulsepass.domain.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    // FR-ART-002: stageName es el identificador de negocio único.
    Optional<Artist> findByStageName(String stageName);
}
