package ma.mobility.carshare;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CarHoldRepository extends JpaRepository<CarHold, String> {

    Optional<CarHold> findByBookingRef(String bookingRef);

    List<CarHold> findByStatusAndExpiresAtBefore(CarHold.Status status, Instant instant);
}
