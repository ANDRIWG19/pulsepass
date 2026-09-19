package com.pulsepass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    // Sin cascade: eliminar un venue no debe arrastrar sus eventos (BR-002).
    @OneToMany(mappedBy = "venue")
    private List<Event> events = new ArrayList<>();

    protected Venue() {
    }

    public Venue(String code, String name, String city, String address, int capacity, boolean active) {
        this.code = code;
        this.name = name;
        this.city = city;
        this.address = address;
        this.capacity = capacity;
        this.active = active;
    }

    public void addEvent(Event event) {
        if (!events.contains(event)) {
            events.add(event);
        }
        event.assignVenue(this);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }

    public List<Event> getEvents() {
        return events;
    }
}
