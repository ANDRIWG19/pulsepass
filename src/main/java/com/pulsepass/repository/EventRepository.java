package com.pulsepass.repository;

import com.pulsepass.domain.Event;
import com.pulsepass.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // FR-EVT-002: buscar evento por su código de negocio.
    Optional<Event> findByEventCode(String eventCode);

    // FR-EVT-005 / AC-006: eventos publicados ordenados por fecha ascendente.
    // (se reutiliza con cualquier EventStatus, no solo PUBLISHED)
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);

    // FR-VEN-004: eventos de un venue, navegando venue.code.
    List<Event> findByVenue_Code(String venueCode);

    // FR-SRC-001 / FR-ART-004 / AC-007: eventos donde participa un artista.
    // Requiere @Query porque hay que atravesar la relación N:M explícitamente
    // con JOIN; DISTINCT evita duplicar el evento si en el futuro se agregan
    // más columnas de proyección desde la tabla intermedia.
    @Query("""
            select distinct e
            from Event e
            join e.artists a
            where a.stageName = :stageName
            """)
    List<Event> findByArtistStageName(@Param("stageName") String stageName);

    // FR-SRC-002: eventos de una ciudad en los que participa un artista.
    // Combina dos asociaciones (venue.city y artists.stageName) en el mismo JOIN.
    @Query("""
            select distinct e
            from Event e
            join e.artists a
            where e.venue.city = :city
              and a.stageName = :stageName
            """)
    List<Event> findByCityAndArtist(
            @Param("city") String city,
            @Param("stageName") String stageName);

    // FR-SRC-003: eventos recomendados (publicados, posteriores a una fecha,
    // en una ciudad, con un artista cuyo nombre contenga cierto texto).
    // Case-insensitive con lower() + like, DISTINCT y orden por fecha.
    @Query("""
            select distinct e
            from Event e
            join e.artists a
            where e.status = :status
              and e.eventDate > :afterDate
              and e.venue.city = :city
              and lower(a.stageName) like lower(concat('%', :artistFragment, '%'))
            order by e.eventDate asc
            """)
    List<Event> findRecommended(
            @Param("status") EventStatus status,
            @Param("afterDate") LocalDate afterDate,
            @Param("city") String city,
            @Param("artistFragment") String artistFragment);
}
