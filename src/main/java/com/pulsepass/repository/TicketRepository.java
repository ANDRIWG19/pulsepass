package com.pulsepass.repository;

import com.pulsepass.domain.Ticket;
import com.pulsepass.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // FR-TKT-002: ticketCode es el identificador de negocio único.
    Optional<Ticket> findByTicketCode(String ticketCode);

    // FR-TKT-006: tickets de un usuario por email, sin filtrar por estado.
    List<Ticket> findByUser_Email(String email);

    // FR-TKT-006: tickets de un usuario por email y, opcionalmente, por estado
    // (se expone como un método aparte porque un Query Method no puede tratar
    // un parámetro como "opcional": si se pasa null, Spring Data generaría
    // "status IS NULL" en vez de "omitir el filtro").
    List<Ticket> findByUser_EmailAndStatus(String email, TicketStatus status);

    // FR-TKT-007: tickets PAID (o de cualquier status) de un evento por eventCode.
    // Se resuelve con Query Method y no con JPQL porque es una navegación
    // directa de dos propiedades (event.eventCode + status) sin agregaciones
    // ni múltiples joins: un JPQL aquí no aportaría legibilidad extra (NFR-007).
    List<Ticket> findByEvent_EventCodeAndStatus(String eventCode, TicketStatus status);

    // FR-TKT-008 / AC-008: conteo de tickets PAID de un evento.
    // Requiere @Query porque las funciones de agregación (COUNT) no tienen
    // una forma derivada tan legible como "countBy..." cuando además se
    // navega una relación (event.eventCode).
    @Query("""
            select count(t)
            from Ticket t
            where t.event.eventCode = :eventCode
              and t.status = :status
            """)
    long countByEventCodeAndStatus(
            @Param("eventCode") String eventCode,
            @Param("status") TicketStatus status);

    // FR-SRC-004: tickets cuyo evento es posterior a una fecha, ordenados
    // cronológicamente por la fecha del evento.
    @Query("""
            select t
            from Ticket t
            where t.event.eventDate > :afterDate
            order by t.event.eventDate asc
            """)
    List<Ticket> findByEventDateAfter(@Param("afterDate") LocalDate afterDate);
}
