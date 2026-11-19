package ma.mobility.carshare;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    List<Vehicle> findByCityIgnoreCase(String city);

    /** Verrou pessimiste : deux holds concurrents ne peuvent pas depasser le stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.offerId = :id")
    Optional<Vehicle> lockById(@Param("id") String id);
}
