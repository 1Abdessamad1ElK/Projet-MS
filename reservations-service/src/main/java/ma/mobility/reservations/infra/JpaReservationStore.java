package ma.mobility.reservations.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ma.mobility.reservations.saga.Models.Reservation;
import ma.mobility.reservations.saga.Ports.ReservationStore;

import java.util.List;
import java.util.Optional;

/**
 * Adaptateur JPA du port ReservationStore : un enregistrement par reservation,
 * l'etat complet (maillons + journal de saga) est serialise en JSON.
 * Chaque save() est sa propre transaction courte : on ne garde JAMAIS une transaction
 * de base ouverte pendant un appel reseau vers un operateur.
 */
public class JpaReservationStore implements ReservationStore {

    private final ReservationJpaRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public JpaReservationStore(ReservationJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(Reservation r) {
        repo.save(new ReservationEntity(r.id, r.idempotencyKey, r.status, r.createdAt, toJson(r)));
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return repo.findById(id).map(e -> fromJson(e.getJson()));
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String key) {
        return repo.findByIdempotencyKey(key).map(e -> fromJson(e.getJson()));
    }

    @Override
    public List<Reservation> findAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(e -> fromJson(e.getJson())).toList();
    }

    private String toJson(Reservation r) {
        try {
            return mapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reservation " + r.id, e);
        }
    }

    private Reservation fromJson(String json) {
        try {
            return mapper.readValue(json, Reservation.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored reservation", e);
        }
    }
}
