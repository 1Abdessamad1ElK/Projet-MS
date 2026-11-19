package ma.mobility.carshare;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Maintien (HELD) puis reservation ferme (CONFIRMED), ou annule (CANCELLED). */
@Entity
@Table(name = "car_hold")
public class CarHold {

    public enum Status { HELD, CONFIRMED, CANCELLED }

    @Id
    private String holdId;
    private String offerId;
    private int units;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String bookingRef;
    private Instant expiresAt;

    protected CarHold() {}

    public CarHold(String holdId, String offerId, int units, Instant expiresAt) {
        this.holdId = holdId;
        this.offerId = offerId;
        this.units = units;
        this.status = Status.HELD;
        this.expiresAt = expiresAt;
    }

    public String getHoldId() { return holdId; }
    public String getOfferId() { return offerId; }
    public int getUnits() { return units; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getBookingRef() { return bookingRef; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public Instant getExpiresAt() { return expiresAt; }
}
