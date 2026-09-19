package com.pulsepass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_name", nullable = false, unique = true, length = 150)
    private String stageName;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 100)
    private String genre;

    @Column(nullable = false)
    private boolean active;

    @ManyToMany(mappedBy = "artists")
    private Set<Event> events = new HashSet<>();

    protected Artist() {
    }

    public Artist(String stageName, String country, String genre, boolean active) {
        this.stageName = stageName;
        this.country = country;
        this.genre = genre;
        this.active = active;
    }

    void assignEvent(Event event) {
        events.add(event);
    }

    public Long getId() {
        return id;
    }

    public String getStageName() {
        return stageName;
    }

    public String getCountry() {
        return country;
    }

    public String getGenre() {
        return genre;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Event> getEvents() {
        return events;
    }
}
