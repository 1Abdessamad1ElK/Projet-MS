package ma.mobility.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventType;
    private String reservationId;
    private String recipient;
    private String message;
    private Instant createdAt;

    protected Notification() {}

    public Notification(String eventType, String reservationId, String recipient, String message) {
        this.eventType = eventType;
        this.reservationId = reservationId;
        this.recipient = recipient;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getReservationId() { return reservationId; }
    public String getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
