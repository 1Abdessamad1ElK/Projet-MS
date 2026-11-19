package ma.mobility.reservations.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, String> {

    Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);

    List<ReservationEntity> findAllByOrderByCreatedAtDesc();
}
