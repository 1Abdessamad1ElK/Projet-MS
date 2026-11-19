package ma.mobility.reservations.saga;

import ma.mobility.reservations.saga.Models.Booking;
import ma.mobility.reservations.saga.Models.Hold;
import ma.mobility.reservations.saga.Models.Offer;
import ma.mobility.reservations.saga.Models.Quote;
import ma.mobility.reservations.saga.Models.QuoteLine;
import ma.mobility.reservations.saga.Models.Reservation;

import java.util.List;
import java.util.Optional;

/**
 * Ports de la saga (architecture hexagonale simplifiee) : l'orchestrateur ne depend que de
 * ces interfaces. Il ne sait pas que derriere il y a du REST, du CORBA ou du SOAP.
 */
public final class Ports {
    private Ports() {}

    /** Meme interface pour les 3 modes (train, vol, voiture) : voir contracts/bridge-api.openapi.yaml. */
    public interface Bridge {
        List<Offer> search(String origin, String destination, String date);
        Hold hold(String offerId, int seats);
        Booking confirm(String holdId);
        /** IDEMPOTENT : peut etre rejoue sans risque. */
        void cancel(String reference);
    }

    public interface Pricing {
        Quote quote(List<QuoteLine> lines);
    }

    public interface ReservationStore {
        void save(Reservation r);
        Optional<Reservation> findById(String id);
        Optional<Reservation> findByIdempotencyKey(String key);
        List<Reservation> findAll();
    }

    /** Evenements asynchrones (RabbitMQ) : ne doivent JAMAIS faire echouer la saga. */
    public interface EventPublisher {
        void publish(String type, Reservation r, String message);
    }
}
