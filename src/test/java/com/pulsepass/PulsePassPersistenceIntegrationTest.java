package com.pulsepass;

import com.pulsepass.domain.*;
import com.pulsepass.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de integración de la capa de persistencia de PulsePass contra
 * PostgreSQL real (vía Testcontainers). Cubre QT-001 a QT-009 y las
 * validaciones funcionales AC-001 a AC-008 del PRD.
 *
 * Convención de construcción: RescueCase-style — Event exige Venue ya
 * construido, UserProfile y Ticket exigen sus referencias ya construidas
 * en el propio constructor. Por eso el orden es siempre "padre primero":
 * se guarda el padre (para que tenga id) y luego se construye el hijo
 * referenciándolo. Donde el modelo declara cascade = ALL (User.userProfile),
 * se aprovecha esa cascada para demostrarla en vez de guardar manualmente.
 */
@Testcontainers
@SpringBootTest
@Transactional
class PulsePassPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("pulsepass_test")
                    .withUsername("pulsepass")
                    .withPassword("pulsepass");

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------
    // QT-001 — Flyway
    // ------------------------------------------------------------------

    @Test
    void flywayAppliedAllMigrations() {
        List<Map<String, Object>> history =
                jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank");

        List<String> versions = history.stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();

        assertThat(versions).contains("1", "2", "3");
    }

    // ------------------------------------------------------------------
    // QT-002 — Hibernate valida el esquema (implícito: si el contexto
    // arranca y este repository responde, ddl-auto=validate no falló).
    // Además confirma que V2 sembró el catálogo de artistas.
    // ------------------------------------------------------------------

    @Test
    void artistCatalogSeededByMigrationV2() {
        assertThat(artistRepository.count()).isEqualTo(5);
        assertThat(artistRepository.findByStageName("Solar Beat")).isPresent();
        assertThat(artistRepository.findByStageName("Digital Pulse")).isPresent();
    }

    // ------------------------------------------------------------------
    // QT-003 — Relación 1:N Venue -> Event
    // ------------------------------------------------------------------

    @Test
    void oneToManyVenueToEvent() {
        Venue venue = venueRepository.save(
                new Venue("VEN-001", "Test Arena", "Bogota", "Calle 1", 1000, true));

        Event event1 = new Event("EVT-001", "Show 1", "desc", EventCategory.MUSIC,
                EventStatus.PUBLISHED, LocalDate.now().plusDays(10), 0, venue);
        Event event2 = new Event("EVT-002", "Show 2", "desc", EventCategory.MUSIC,
                EventStatus.DRAFT, LocalDate.now().plusDays(20), 0, venue);

        venue.addEvent(event1);
        venue.addEvent(event2);

        eventRepository.save(event1);
        eventRepository.save(event2);

        List<Event> eventsForVenue = eventRepository.findByVenue_Code("VEN-001");

        assertThat(eventsForVenue).hasSize(2);
        assertThat(eventsForVenue).allSatisfy(e ->
                assertThat(e.getVenue().getCode()).isEqualTo("VEN-001"));
    }

    // ------------------------------------------------------------------
    // QT-004 — Relación 1:1 User -> UserProfile (cascade ALL desde User)
    // ------------------------------------------------------------------

    @Test
    void oneToOneUserToUserProfileWithCascade() {
        User user = userRepository.save(new User("andrea", "andrea@example.com", true));

        UserProfile profile = new UserProfile(
                user, "Andrea", "Gomez", "3000000000", "Santa Marta", LocalDate.of(1995, 5, 20));

        user.assignProfile(profile);

        User saved = userRepository.save(user);

        assertThat(saved.getUserProfile()).isNotNull();
        assertThat(saved.getUserProfile().getId()).isNotNull();
        assertThat(saved.getUserProfile().getFirstName()).isEqualTo("Andrea");
    }

    // ------------------------------------------------------------------
    // QT-005 / AC-003 — Relación N:M Event <-> Artist, sin duplicar pares
    // ------------------------------------------------------------------

    @Test
    void manyToManyEventArtistWithoutDuplicatingPairs() {
        Venue venue = venueRepository.save(
                new Venue("VEN-002", "Test Arena 2", "Santa Marta", "Calle 2", 2000, true));
        Event event = eventRepository.save(new Event("EVT-100", "Multi Artist Show", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(30), 0, venue));

        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();

        event.addArtist(solarBeat);
        event.addArtist(neonWaves);
        event.addArtist(caribbeanSound);
        // Intento de duplicar el mismo par evento-artista: el Set no debe crecer.
        event.addArtist(solarBeat);

        eventRepository.save(event);

        Event reloaded = eventRepository.findByEventCode("EVT-100").orElseThrow();
        assertThat(reloaded.getArtists()).hasSize(3);
        assertThat(reloaded.getArtists()).extracting(Artist::getStageName)
                .containsExactlyInAnyOrder("Solar Beat", "Neon Waves", "Caribbean Sound");
    }

    // ------------------------------------------------------------------
    // QT-006 — Ticket -> User y Ticket -> Event
    // ------------------------------------------------------------------

    @Test
    void ticketRelationsToUserAndEvent() {
        Venue venue = venueRepository.save(
                new Venue("VEN-003", "Test Arena 3", "Cali", "Calle 3", 500, true));
        Event event = eventRepository.save(new Event("EVT-200", "Solo Show", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(5), 0, venue));
        User user = userRepository.save(new User("carlos", "carlos@example.com", true));

        Ticket ticket = new Ticket(user, event, "TCK-1000", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.PAID, LocalDateTime.now());

        Ticket saved = ticketRepository.save(ticket);

        Ticket reloaded = ticketRepository.findByTicketCode("TCK-1000").orElseThrow();
        assertThat(reloaded.getUser().getUsername()).isEqualTo("carlos");
        assertThat(reloaded.getEvent().getEventCode()).isEqualTo("EVT-200");
        assertThat(saved.getId()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Query Methods simples
    // ------------------------------------------------------------------

    @Test
    void queryMethodByEventCode() {
        Venue venue = venueRepository.save(
                new Venue("VEN-004", "Arena 4", "Medellin", "Calle 4", 800, true));
        eventRepository.save(new Event("EVT-300", "Event 300", "desc", EventCategory.SPORTS,
                EventStatus.DRAFT, LocalDate.now().plusDays(15), 0, venue));

        assertThat(eventRepository.findByEventCode("EVT-300")).isPresent();
        assertThat(eventRepository.findByEventCode("NON-EXISTENT")).isEmpty();
    }

    @Test
    void queryMethodByEmailIgnoreCase() {
        userRepository.save(new User("laura", "Laura@Example.com", true));

        assertThat(userRepository.findByEmailIgnoreCase("laura@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("LAURA@EXAMPLE.COM")).isPresent();
    }

    // ------------------------------------------------------------------
    // AC-006 — Solo se retornan eventos PUBLISHED, en orden cronológico
    // ------------------------------------------------------------------

    @Test
    void queryMethodPublishedEventsOrderedByDate() {
        Venue venue = venueRepository.save(
                new Venue("VEN-005", "Arena 5", "Santa Marta", "Calle 5", 3000, true));

        eventRepository.save(new Event("EVT-400", "Draft Event", "desc", EventCategory.MUSIC,
                EventStatus.DRAFT, LocalDate.now().plusDays(1), 0, venue));
        eventRepository.save(new Event("EVT-401", "Published Late", "desc", EventCategory.MUSIC,
                EventStatus.PUBLISHED, LocalDate.now().plusDays(20), 0, venue));
        eventRepository.save(new Event("EVT-402", "Published Early", "desc", EventCategory.MUSIC,
                EventStatus.PUBLISHED, LocalDate.now().plusDays(5), 0, venue));
        eventRepository.save(new Event("EVT-403", "Cancelled Event", "desc", EventCategory.MUSIC,
                EventStatus.CANCELLED, LocalDate.now().plusDays(3), 0, venue));

        List<Event> published = eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED);

        assertThat(published).extracting(Event::getEventCode)
                .containsExactly("EVT-402", "EVT-401");
    }

    // ------------------------------------------------------------------
    // FR-TKT-006 — Tickets de un usuario por email y status
    // ------------------------------------------------------------------

    @Test
    void queryMethodTicketsByUserEmailAndStatus() {
        Venue venue = venueRepository.save(
                new Venue("VEN-006", "Arena 6", "Santa Marta", "Calle 6", 1500, true));
        Event event = eventRepository.save(new Event("EVT-500", "Event 500", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(8), 0, venue));
        User user = userRepository.save(new User("miguel", "miguel@example.com", true));

        ticketRepository.save(new Ticket(user, event, "TCK-2000", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(user, event, "TCK-2001", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.RESERVED, LocalDateTime.now()));

        List<Ticket> paidTickets = ticketRepository.findByUser_EmailAndStatus("miguel@example.com", TicketStatus.PAID);
        List<Ticket> allTickets = ticketRepository.findByUser_Email("miguel@example.com");

        assertThat(paidTickets).hasSize(1);
        assertThat(allTickets).hasSize(2);
    }

    // ------------------------------------------------------------------
    // FR-TKT-007 — Tickets PAID de un evento por eventCode
    // ------------------------------------------------------------------

    @Test
    void queryMethodTicketsPaidByEventCode() {
        Venue venue = venueRepository.save(
                new Venue("VEN-007", "Arena 7", "Santa Marta", "Calle 7", 2500, true));
        Event event = eventRepository.save(new Event("EVT-600", "Event 600", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(12), 0, venue));
        User andrea = userRepository.save(new User("andrea2", "andrea2@example.com", true));
        User laura = userRepository.save(new User("laura2", "laura2@example.com", true));

        ticketRepository.save(new Ticket(andrea, event, "TCK-3000", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(laura, event, "TCK-3001", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.RESERVED, LocalDateTime.now()));

        List<Ticket> paid = ticketRepository.findByEvent_EventCodeAndStatus("EVT-600", TicketStatus.PAID);

        assertThat(paid).hasSize(1);
        assertThat(paid.get(0).getTicketCode()).isEqualTo("TCK-3000");
    }

    // ------------------------------------------------------------------
    // FR-SRC-001 / AC-007 — Eventos por artista, sin duplicados
    // ------------------------------------------------------------------

    @Test
    void jpqlEventsByArtistStageName() {
        Venue venue = venueRepository.save(
                new Venue("VEN-008", "Arena 8", "Santa Marta", "Calle 8", 4000, true));
        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();

        Event event1 = eventRepository.save(new Event("EVT-700", "Fest A", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(2), 0, venue));
        Event event2 = eventRepository.save(new Event("EVT-701", "Fest B", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(4), 0, venue));

        event1.addArtist(solarBeat);
        event2.addArtist(solarBeat);
        eventRepository.save(event1);
        eventRepository.save(event2);

        List<Event> events = eventRepository.findByArtistStageName("Solar Beat");

        assertThat(events).extracting(Event::getEventCode)
                .containsExactlyInAnyOrder("EVT-700", "EVT-701");
        // Sin duplicados aunque el artista participe en varios eventos.
        assertThat(events).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------
    // FR-SRC-002 — Eventos por ciudad y artista
    // ------------------------------------------------------------------

    @Test
    void jpqlEventsByCityAndArtist() {
        Venue santaMarta = venueRepository.save(
                new Venue("VEN-009", "Arena SM", "Santa Marta", "Calle 9", 3000, true));
        Venue bogota = venueRepository.save(
                new Venue("VEN-010", "Arena BOG", "Bogota", "Calle 10", 3000, true));

        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();

        Event eventInSantaMarta = eventRepository.save(new Event("EVT-800", "Event SM", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(6), 0, santaMarta));
        Event eventInBogota = eventRepository.save(new Event("EVT-801", "Event BOG", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(6), 0, bogota));

        eventInSantaMarta.addArtist(neonWaves);
        eventInBogota.addArtist(neonWaves);
        eventRepository.save(eventInSantaMarta);
        eventRepository.save(eventInBogota);

        List<Event> results = eventRepository.findByCityAndArtist("Santa Marta", "Neon Waves");

        assertThat(results).extracting(Event::getEventCode).containsExactly("EVT-800");
    }

    // ------------------------------------------------------------------
    // FR-SRC-003 — Eventos recomendados (status + fecha + ciudad + artista,
    // case-insensitive, DISTINCT, ordenado)
    // ------------------------------------------------------------------

    @Test
    void jpqlRecommendedEvents() {
        Venue venue = venueRepository.save(
                new Venue("VEN-011", "Arena 11", "Santa Marta", "Calle 11", 3500, true));
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();

        Event tooEarly = eventRepository.save(new Event("EVT-900", "Too Early", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(1), 0, venue));
        Event recommended = eventRepository.save(new Event("EVT-901", "Recommended", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(15), 0, venue));
        Event draftEvent = eventRepository.save(new Event("EVT-902", "Draft", "desc",
                EventCategory.MUSIC, EventStatus.DRAFT, LocalDate.now().plusDays(15), 0, venue));

        tooEarly.addArtist(caribbeanSound);
        recommended.addArtist(caribbeanSound);
        draftEvent.addArtist(caribbeanSound);
        eventRepository.save(tooEarly);
        eventRepository.save(recommended);
        eventRepository.save(draftEvent);

        List<Event> results = eventRepository.findRecommended(
                EventStatus.PUBLISHED, LocalDate.now().plusDays(10), "Santa Marta", "caribbean");

        assertThat(results).extracting(Event::getEventCode).containsExactly("EVT-901");
    }

    // ------------------------------------------------------------------
    // FR-TKT-008 / AC-008 — Conteo de tickets PAID
    // ------------------------------------------------------------------

    @Test
    void jpqlCountPaidTicketsByEvent() {
        Venue venue = venueRepository.save(
                new Venue("VEN-012", "Arena 12", "Santa Marta", "Calle 12", 5000, true));
        Event event = eventRepository.save(new Event("EVT-1000", "Count Test", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(9), 0, venue));

        User andrea = userRepository.save(new User("andrea3", "andrea3@example.com", true));
        User carlos = userRepository.save(new User("carlos3", "carlos3@example.com", true));
        User laura = userRepository.save(new User("laura3", "laura3@example.com", true));
        User miguel = userRepository.save(new User("miguel3", "miguel3@example.com", true));

        ticketRepository.save(new Ticket(andrea, event, "TCK-4000", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(carlos, event, "TCK-4001", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(laura, event, "TCK-4002", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.RESERVED, LocalDateTime.now()));
        ticketRepository.save(new Ticket(miguel, event, "TCK-4003", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.CANCELLED, LocalDateTime.now()));

        long paidCount = ticketRepository.countByEventCodeAndStatus("EVT-1000", TicketStatus.PAID);

        assertThat(paidCount).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // FR-SRC-004 — Tickets de eventos futuros, ordenados cronológicamente
    // ------------------------------------------------------------------

    @Test
    void jpqlFutureTickets() {
        Venue venue = venueRepository.save(
                new Venue("VEN-013", "Arena 13", "Santa Marta", "Calle 13", 1200, true));
        Event pastEvent = eventRepository.save(new Event("EVT-1100", "Past Event", "desc",
                EventCategory.MUSIC, EventStatus.FINISHED, LocalDate.now().minusDays(5), 0, venue));
        Event nearFuture = eventRepository.save(new Event("EVT-1101", "Near Future", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(3), 0, venue));
        Event farFuture = eventRepository.save(new Event("EVT-1102", "Far Future", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(30), 0, venue));

        User user = userRepository.save(new User("nico", "nico@example.com", true));

        ticketRepository.save(new Ticket(user, pastEvent, "TCK-5000", TicketType.GENERAL,
                new BigDecimal("100000.00"), TicketStatus.USED, LocalDateTime.now()));
        ticketRepository.save(new Ticket(user, farFuture, "TCK-5001", TicketType.GENERAL,
                new BigDecimal("100000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(user, nearFuture, "TCK-5002", TicketType.GENERAL,
                new BigDecimal("100000.00"), TicketStatus.PAID, LocalDateTime.now()));

        List<Ticket> futureTickets = ticketRepository.findByEventDateAfter(LocalDate.now());

        assertThat(futureTickets).extracting(Ticket::getTicketCode)
                .containsExactly("TCK-5002", "TCK-5001");
    }

    // ------------------------------------------------------------------
    // QT-009 / AC-005 — UNIQUE en ticketCode
    // ------------------------------------------------------------------

    @Test
    void uniqueConstraintViolationOnDuplicateTicketCode() {
        Venue venue = venueRepository.save(
                new Venue("VEN-014", "Arena 14", "Santa Marta", "Calle 14", 1000, true));
        Event event = eventRepository.save(new Event("EVT-1200", "Event 1200", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(7), 0, venue));
        User user1 = userRepository.save(new User("user1200a", "user1200a@example.com", true));
        User user2 = userRepository.save(new User("user1200b", "user1200b@example.com", true));

        ticketRepository.saveAndFlush(new Ticket(user1, event, "TCK-0001", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.PAID, LocalDateTime.now()));

        Ticket duplicate = new Ticket(user2, event, "TCK-0001", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.RESERVED, LocalDateTime.now());

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(duplicate));
    }

    // ------------------------------------------------------------------
    // AC-004 — La BD impide un segundo UserProfile para el mismo usuario
    // ------------------------------------------------------------------

    @Test
    void uniqueConstraintViolationOnSecondUserProfileForSameUser() {
        User user = userRepository.save(new User("dupprofile", "dupprofile@example.com", true));

        UserProfile firstProfile = new UserProfile(
                user, "Nombre", "Apellido", "3000000001", "Santa Marta", LocalDate.of(1990, 1, 1));
        userProfileRepository.saveAndFlush(firstProfile);

        UserProfile secondProfile = new UserProfile(
                user, "Otro", "Perfil", "3000000002", "Bogota", LocalDate.of(1992, 2, 2));

        assertThrows(DataIntegrityViolationException.class,
                () -> userProfileRepository.saveAndFlush(secondProfile));
    }

    // ------------------------------------------------------------------
    // FR-VEN-003 — CHECK capacity > 0
    // ------------------------------------------------------------------

    @Test
    void checkConstraintViolationOnInvalidVenueCapacity() {
        Venue invalidVenue = new Venue("VEN-INVALID", "Invalid Venue", "Santa Marta", "Calle X", 0, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> venueRepository.saveAndFlush(invalidVenue));
    }

    // ------------------------------------------------------------------
    // FR-TKT-003 — CHECK price >= 0
    // ------------------------------------------------------------------

    @Test
    void checkConstraintViolationOnNegativeTicketPrice() {
        Venue venue = venueRepository.save(
                new Venue("VEN-015", "Arena 15", "Santa Marta", "Calle 15", 1000, true));
        Event event = eventRepository.save(new Event("EVT-1300", "Event 1300", "desc",
                EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(11), 0, venue));
        User user = userRepository.save(new User("negprice", "negprice@example.com", true));

        Ticket invalidTicket = new Ticket(user, event, "TCK-NEG", TicketType.GENERAL,
                new BigDecimal("-10.00"), TicketStatus.RESERVED, LocalDateTime.now());

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(invalidTicket));
    }

    // ------------------------------------------------------------------
    // Reto integrador — escenario completo con los datos de referencia
    // de la sección 16 del PRD (AC-001, AC-002, AC-003, AC-006, AC-007, AC-008)
    // ------------------------------------------------------------------

    @Test
    void integratorScenarioWithReferenceData() {
        Venue venue = venueRepository.save(
                new Venue("VEN-SMR-01", "Marina Convention Center", "Santa Marta", "Av. del Mar", 5000, true));

        Event event = eventRepository.save(new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música del Caribe", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.of(2026, 12, 15), 0, venue));

        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();

        event.addArtist(solarBeat);
        event.addArtist(neonWaves);
        event.addArtist(caribbeanSound);
        eventRepository.save(event);

        User andrea = userRepository.save(new User("andrea_ref", "andrea_ref@example.com", true));
        User carlos = userRepository.save(new User("carlos_ref", "carlos_ref@example.com", true));
        User laura = userRepository.save(new User("laura_ref", "laura_ref@example.com", true));
        User miguel = userRepository.save(new User("miguel_ref", "miguel_ref@example.com", true));

        ticketRepository.save(new Ticket(andrea, event, "TCK-REF-01", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(carlos, event, "TCK-REF-02", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.PAID, LocalDateTime.now()));
        ticketRepository.save(new Ticket(laura, event, "TCK-REF-03", TicketType.GENERAL,
                new BigDecimal("120000.00"), TicketStatus.RESERVED, LocalDateTime.now()));
        ticketRepository.save(new Ticket(miguel, event, "TCK-REF-04", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.CANCELLED, LocalDateTime.now()));

        // AC-001: el venue se recupera por código y su capacidad es > 0.
        Venue reloadedVenue = venueRepository.findByCode("VEN-SMR-01").orElseThrow();
        assertThat(reloadedVenue.getCapacity()).isGreaterThan(0);

        // AC-002: el evento se recupera por eventCode junto con su venue.
        Event reloadedEvent = eventRepository.findByEventCode("CMF-2026").orElseThrow();
        assertThat(reloadedEvent.getVenue().getCode()).isEqualTo("VEN-SMR-01");

        // AC-003: los tres artistas aparecen relacionados, sin duplicar el par.
        assertThat(reloadedEvent.getArtists()).hasSize(3);

        // AC-006: al consultar PUBLISHED, el evento aparece.
        List<Event> published = eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED);
        assertThat(published).extracting(Event::getEventCode).contains("CMF-2026");

        // AC-007: buscar por artista retorna el evento una sola vez.
        List<Event> bySolarBeat = eventRepository.findByArtistStageName("Solar Beat");
        assertThat(bySolarBeat).extracting(Event::getEventCode).containsOnlyOnce("CMF-2026");

        // AC-008: solo los tickets PAID participan del conteo de ventas.
        long paidCount = ticketRepository.countByEventCodeAndStatus("CMF-2026", TicketStatus.PAID);
        assertThat(paidCount).isEqualTo(2);
    }
}
