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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ticket es una entidad propia y no un simple @ManyToMany entre User y Event
 * porque tiene datos propios: ticketCode, type, price, status y purchaseDate (BR-006).
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 50)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketType type;

    // BR-007: BigDecimal/NUMERIC, nunca float/double.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    protected Ticket() {
    }

    public Ticket(User user, Event event, String ticketCode, TicketType type,
            BigDecimal price, TicketStatus status, LocalDateTime purchaseDate) {
        this.user = user;
        this.event = event;
        this.ticketCode = ticketCode;
        this.type = type;
        this.price = price;
        this.status = status;
        this.purchaseDate = purchaseDate;
    }

    public void markAsPaid() {
        this.status = TicketStatus.PAID;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public void markAsUsed() {
        this.status = TicketStatus.USED;
    }

    public Long getId() {
        return id;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public TicketType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public User getUser() {
        return user;
    }

    public Event getEvent() {
        return event;
    }
}
