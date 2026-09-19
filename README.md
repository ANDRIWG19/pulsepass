Integrantes: 
DANIEL ANDRES VARELA PERDOMO 2024214048
MATEO ANDRES CAMPUZANO CAMARGO 2024214063

# PulsePass

## 1. Descripción

**PulsePass** es la capa de persistencia de una plataforma para descubrir eventos y
administrar entradas de conciertos, festivales, conferencias y actividades culturales.
Construido con **Java 21**, **Spring Boot 4**, **Spring Data JPA / Hibernate**, **Flyway**
y **PostgreSQL**, con pruebas de integración reales contra PostgreSQL mediante
**Testcontainers**.

Este proyecto es el MVP académico definido en el PRD: se centra exclusivamente en el
modelo relacional, las migraciones y las consultas de negocio. No incluye autenticación,
pasarela de pagos, notificaciones, frontend ni una capa de servicios/API REST.

## 2. Modelo de datos

| Entidad | Tabla | Descripción |
|---|---|---|
| `Venue` | `venues` | Recinto donde se realizan eventos (código, nombre, ciudad, dirección, capacidad) |
| `Event` | `events` | Evento publicable (código, categoría, estado, fecha, edad mínima, venue) |
| `Artist` | `artists` | Artista que puede participar en múltiples eventos |
| `User` | `users` | Usuario de la plataforma (identidad de dominio, sin credenciales) |
| `UserProfile` | `user_profiles` | Perfil individual y único de un usuario |
| `Ticket` | `tickets` | Entrada emitida para un usuario y un evento, con precio, tipo y estado |

Tabla intermedia: `event_artists` (relación N:M entre `Event` y `Artist`, con clave
primaria compuesta `(event_id, artist_id)` que impide duplicar la misma asociación).

Enumerados (persistidos como `VARCHAR` vía `@Enumerated(EnumType.STRING)`, nunca por
ordinal — BR-008):

- `EventCategory`: `MUSIC`, `SPORTS`, `TECHNOLOGY`, `EDUCATION`, `CULTURE`, `ENTERTAINMENT`
- `EventStatus`: `DRAFT`, `PUBLISHED`, `SOLD_OUT`, `CANCELLED`, `FINISHED`
- `TicketType`: `GENERAL`, `VIP`, `BACKSTAGE`, `STUDENT`
- `TicketStatus`: `RESERVED`, `PAID`, `CANCELLED`, `USED`

`Ticket` es una entidad propia (no un simple `@ManyToMany` entre `User` y `Event`) porque
tiene datos propios: `ticketCode`, `type`, `price`, `status` y `purchaseDate` (BR-006).

## 3. Relaciones

```
Venue (1) ──── (N) Event (N) ──── (M) Artist

User (1) ──── (1) UserProfile

User (1) ──── (N) Ticket (N) ──── (1) Event
```

| Relación | Tipo | Lado dueño de la FK | Cascade |
|---|---|---|---|
| `Venue` → `Event` | 1:N | `Event` (`venue_id`) | Ninguno (BR-002: eliminar un venue no arrastra sus eventos) |
| `Event` ↔ `Artist` | N:M | `Event` (`@JoinTable event_artists`) | Ninguno |
| `User` → `UserProfile` | 1:1 | `UserProfile` (`user_id`, `UNIQUE`) | `ALL` desde `User.userProfile` (BR-004) |
| `User` → `Ticket` | 1:N | `Ticket` (`user_id`) | Ninguno |
| `Event` → `Ticket` | 1:N | `Ticket` (`event_id`) | Ninguno |

**Nota sobre el orden de construcción**: `Event`, `UserProfile` y `Ticket` exigen la
entidad "padre" ya construida en su propio constructor (`Event(..., venue)`,
`UserProfile(user, ...)`, `Ticket(user, event, ...)`). El orden de persistencia es
siempre de arriba hacia abajo: se guarda primero el padre (para que tenga `id`) y luego
se construye y guarda el hijo referenciándolo. Donde existe `cascade = ALL`
(`User.userProfile`), guardar el usuario ya persiste el perfil automáticamente.

## 4. Instrucciones para ejecutar la aplicación

Requiere una instancia de PostgreSQL disponible. Por defecto se conecta a
`jdbc:postgresql://localhost:5432/pulsepass` con usuario y clave `postgres`; se puede
sobreescribir con las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`.

```bash
mvn spring-boot:run
```

Al arrancar, Flyway ejecuta automáticamente las migraciones (`V1`, `V2`, `V3`) contra la
base configurada, y Hibernate valida (`ddl-auto: validate`) que las entidades coincidan
exactamente con el esquema ya creado.

## 5. Instrucciones para ejecutar los tests

```bash
mvn clean test
```

No se requiere ninguna base de datos local: **Testcontainers** levanta automáticamente un
contenedor Docker con PostgreSQL para la ejecución de las pruebas (ver sección 8). Solo se
necesita tener **Docker** corriendo en la máquina.

```bash
docker ps   # para ver el contenedor mientras corren los tests
```

## 6. Flyway

Flyway es el único responsable de crear y evolucionar el esquema de la base de datos.
Hibernate **nunca** genera DDL (`ddl-auto: validate`): en el arranque solo compara las
entidades `@Entity` contra las tablas que Flyway ya creó, y falla si hay una discrepancia
real de columnas o tipos (NFR-002, NFR-003).

Las migraciones viven en `src/main/resources/db/migration`:

| Migración | Contenido |
|---|---|
| `V1__create_schema.sql` | Crea las 6 tablas de negocio + la tabla intermedia `event_artists`, con sus `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK` (capacidad > 0, precio ≥ 0, catálogos de categoría/estado/tipo) e índices sobre FKs y columnas de filtrado frecuente |
| `V2__insert_initial_artists.sql` | Inserta el catálogo inicial de artistas de prueba: Solar Beat, Neon Waves, Caribbean Sound, Ocean Drive y Digital Pulse |
| `V3__add_streaming_url_to_event.sql` | Agrega `streaming_url VARCHAR(500)` nullable a `events`, sin modificar `V1` (FR-EVT-006) |

## 7. Testcontainers

`PulsePassPersistenceIntegrationTest` usa `@Testcontainers` + `@SpringBootTest` +
`@Transactional` para ejecutar pruebas de integración contra una instancia **real** de
PostgreSQL, nunca H2 (NFR-005). Esto garantiza que los `CHECK`, `UNIQUE` y el
comportamiento real de Hibernate/PostgreSQL se validan contra el mismo motor que se usará
en producción.

```java
@Container
@ServiceConnection
static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("pulsepass_test")
                .withUsername("pulsepass")
                .withPassword("pulsepass");
```

- `@Container` delega a Testcontainers el ciclo de vida del contenedor.
- `@ServiceConnection` conecta automáticamente el `DataSource` de Spring Boot al
  contenedor, sin `@DynamicPropertySource` manual.
- Cada test corre en una transacción (`@Transactional`) que se revierte al finalizar, así
  que los tests no interfieren entre sí aunque compartan el mismo contenedor (NFR-004).
- Flyway corre sus migraciones automáticamente contra el contenedor al levantar el
  contexto de Spring.

## 8. Query Methods implementados

| Repository | Método | Propósito |
|---|---|---|
| `VenueRepository` | `findByCode(String code)` | Buscar un venue por su código de negocio |
| `EventRepository` | `findByEventCode(String eventCode)` | Buscar un evento por su código |
| `EventRepository` | `findByStatusOrderByEventDateAsc(EventStatus status)` | Eventos con determinado estado, ordenados por fecha ascendente (FR-EVT-005) |
| `EventRepository` | `findByVenue_Code(String venueCode)` | Eventos de un venue, navegando `venue.code` (FR-VEN-004) |
| `ArtistRepository` | `findByStageName(String stageName)` | Buscar un artista por su nombre artístico |
| `UserRepository` | `findByUsername(String username)` | Buscar un usuario por su username |
| `UserRepository` | `findByEmailIgnoreCase(String email)` | Buscar un usuario por email, sin distinguir mayúsculas/minúsculas |
| `TicketRepository` | `findByTicketCode(String ticketCode)` | Buscar un ticket por su código |
| `TicketRepository` | `findByUser_Email(String email)` | Tickets de un usuario, navegando `user.email` |
| `TicketRepository` | `findByUser_EmailAndStatus(String email, TicketStatus status)` | Tickets de un usuario filtrados por estado (FR-TKT-006) |
| `TicketRepository` | `findByEvent_EventCodeAndStatus(String eventCode, TicketStatus status)` | Tickets de un evento filtrados por estado, p. ej. `PAID` (FR-TKT-007) |

> **Nota de diseño (FR-TKT-007)**: la búsqueda de tickets pagados por `eventCode` se
> resolvió con un Query Method (`findByEvent_EventCodeAndStatus`) y no con JPQL, porque es
> una navegación directa de dos propiedades sin agregaciones ni múltiples joins — un JPQL
> aquí no aportaría legibilidad adicional (NFR-007).

## 9. Consultas JPQL implementadas (`@Query`)

| Repository | Método | JPQL | Propósito |
|---|---|---|---|
| `EventRepository` | `findByArtistStageName(String stageName)` | `select distinct e from Event e join e.artists a where a.stageName = :stageName` | Eventos donde participa un artista (relación N:M, `DISTINCT` evita duplicados) — FR-SRC-001 / FR-ART-004 |
| `EventRepository` | `findByCityAndArtist(String city, String stageName)` | `select distinct e from Event e join e.artists a where e.venue.city = :city and a.stageName = :stageName` | Eventos de una ciudad en los que participa un artista específico — FR-SRC-002 |
| `EventRepository` | `findRecommended(EventStatus status, LocalDate afterDate, String city, String artistFragment)` | `select distinct e from Event e join e.artists a where e.status = :status and e.eventDate > :afterDate and e.venue.city = :city and lower(a.stageName) like lower(concat('%', :artistFragment, '%')) order by e.eventDate asc` | Eventos recomendados: publicados, posteriores a una fecha, en una ciudad, con artista cuyo nombre contenga cierto texto (case-insensitive, `DISTINCT`, ordenado) — FR-SRC-003 |
| `TicketRepository` | `countByEventCodeAndStatus(String eventCode, TicketStatus status)` | `select count(t) from Ticket t where t.event.eventCode = :eventCode and t.status = :status` | Conteo de tickets con determinado estado (p. ej. `PAID`) de un evento — FR-TKT-008 |
| `TicketRepository` | `findByEventDateAfter(LocalDate afterDate)` | `select t from Ticket t where t.event.eventDate > :afterDate order by t.event.eventDate asc` | Tickets cuyo evento es posterior a una fecha, ordenados cronológicamente — FR-SRC-004 |

## 10. Estructura del proyecto

```
pulsepass/
├── pom.xml
├── README.md
├── src/main
│   ├── java/com/pulsepass
│   │   ├── PulsePassApplication.java
│   │   ├── domain/        (entidades JPA + enums)
│   │   └── repository/    (interfaces JpaRepository)
│   └── resources
│       ├── application.yml
│       └── db/migration   (V1, V2, V3)
└── src/test
    └── java/com/pulsepass
        └── PulsePassPersistenceIntegrationTest.java
```

## 11. Trazabilidad de pruebas (QT-001 a QT-009)

| Criterio | Cubierto por |
|---|---|
| QT-001 — Flyway aplica V1, V2, V3 | `flywayAppliedAllMigrations` |
| QT-002 — Hibernate valida sin crear/actualizar | Arranque exitoso del contexto + `artistCatalogSeededByMigrationV2` |
| QT-003 — Venue 1:N Event | `oneToManyVenueToEvent` |
| QT-004 — User 1:1 UserProfile | `oneToOneUserToUserProfileWithCascade` |
| QT-005 — Event N:M Artist | `manyToManyEventArtistWithoutDuplicatingPairs` |
| QT-006 — Ticket → User y Ticket → Event | `ticketRelationsToUserAndEvent` |
| QT-007 — Query Methods simples y con navegación | `queryMethodByEventCode`, `queryMethodByEmailIgnoreCase`, `queryMethodTicketsByUserEmailAndStatus`, `queryMethodTicketsPaidByEventCode` |
| QT-008 — JPQL con JOIN y COUNT | `jpqlEventsByArtistStageName`, `jpqlEventsByCityAndArtist`, `jpqlRecommendedEvents`, `jpqlCountPaidTicketsByEvent` |
| QT-009 — Restricción UNIQUE con `saveAndFlush` | `uniqueConstraintViolationOnDuplicateTicketCode`, `uniqueConstraintViolationOnSecondUserProfileForSameUser` |

El test `integratorScenarioWithReferenceData` reproduce los datos de referencia de la
sección 16 del PRD (venue `VEN-SMR-01`, evento `CMF-2026`, tickets de Andrea/Carlos/
Laura/Miguel) y valida en un solo escenario los criterios AC-001, AC-002, AC-003, AC-006,
AC-007 y AC-008.
