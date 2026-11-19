package ma.mobility.reservations.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dossier de voyage consolide. Le detail (maillons, journal) est stocke en JSON. */
@Entity
@Table(name = "reservation")
public class ReservationEntity {

    @Id
    private String id;
    @Column(unique = true)
    private String idempotencyKey;
    private String status;
    private String createdAt;
    @Column(length = 20000)
    private String json;

    protected ReservationEntity() {}

    public ReservationEntity(String id, String idempotencyKey, String status, String createdAt, String json) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = createdAt;
        this.json = json;
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getJson() { return json; }
}
