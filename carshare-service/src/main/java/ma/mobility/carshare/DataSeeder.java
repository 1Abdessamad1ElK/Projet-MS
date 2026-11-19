package ma.mobility.carshare;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Jeu de donnees initial (base H2 en memoire, propre a ce service). */
@Component
public class DataSeeder implements CommandLineRunner {

    private final VehicleRepository vehicles;

    public DataSeeder(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public void run(String... args) {
        if (vehicles.count() > 0) {
            return;
        }
        vehicles.save(new Vehicle("CAR-PAR-001", "Paris", "Renault Zoe (electrique)", 35000, 5));
        vehicles.save(new Vehicle("CAR-PAR-002", "Paris", "Peugeot 208", 40000, 3));
        vehicles.save(new Vehicle("CAR-PAR-FAULTY", "Paris", "DEMO - echec au paiement (confirm)", 30000, 5));
        vehicles.save(new Vehicle("CAR-MAD-001", "Madrid", "Seat Ibiza", 33000, 4));
        vehicles.save(new Vehicle("CAR-DSS-001", "Dakar", "Toyota Yaris", 30000, 4));
        vehicles.save(new Vehicle("CAR-RAB-001", "Rabat", "Dacia Logan", 25000, 4));
        vehicles.save(new Vehicle("CAR-CMN-001", "Casablanca", "Dacia Sandero", 28000, 4));
        vehicles.save(new Vehicle("CAR-MAR-001", "Marrakech", "Renault Clio", 27000, 4));
    }
}
