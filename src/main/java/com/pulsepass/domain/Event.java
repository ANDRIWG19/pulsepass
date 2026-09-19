package com.pulsepass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, unique = true, length = 50)
    private String eventCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "minimum_age", nullable = false)
    private int minimumAge;

    // Nace NULL; se agrega recién en la migración V3 (FR-EVT-006).
    @Column(name = "streaming_url", length = 500)
    private String streamingUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    // Event es el lado dueño de la relación N:M (tabla event_artists).
    @ManyToMany
    @JoinTable(name = "event_artists",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id"))
    private Set<Artist> artists = new HashSet<>();

    // Sin cascade: un ticket no debe desaparecer si el evento se manipula en memoria.
    @OneToMany(mappedBy = "event")
    private List<Ticket> tickets = new ArrayList<>();

    protected Event() {
    }

    public Event(String eventCode, String name, String description, EventCategory category,
            EventStatus status, LocalDate eventDate, int minimumAge, Venue venue) {
        this.eventCode = eventCode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.status = status;
        this.eventDate = eventDate;
        this.minimumAge = minimumAge;
        this.venue = venue;
    }

    void assignVenue(Venue venue) {
        this.venue = venue;
    }

    public void addArtist(Artist artist) {
        if (artists.add(artist)) {
            artist.assignEvent(this);
        }
    }

    public void changeStatus(EventStatus status) {
        this.status = status;
    }

    public void setStreamingUrl(String streamingUrl) {
        this.streamingUrl = streamingUrl;
    }

    public Long getId() {
        return id;
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EventCategory getCategory() {
        return category;
    }

    public EventStatus getStatus() {
        return status;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public int getMinimumAge() {
        return minimumAge;
    }

    public String getStreamingUrl() {
        return streamingUrl;
    }

    public Venue getVenue() {
        return venue;
    }

    public Set<Artist> getArtists() {
        return artists;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }
}
