package ma.mobility.reservations.infra;

import com.fasterxml.jackson.databind.ObjectMapper;

import ma.mobility.reservations.saga.Models.Reservation;
import ma.mobility.reservations.saga.Ports.EventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publie les evenements de la saga sur l'exchange topic "mobility.events"
 * (routing key = type d'evenement, ex: reservation.confirmed).
 * Un broker en panne ne doit pas faire echouer une reservation : on journalise et on continue.
 */
public class RabbitEventPublisher implements EventPublisher {

    public static final String EXCHANGE = "mobility.events";
    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper = new ObjectMapper();

    public RabbitEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publish(String type, Reservation r, String message) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type);
            event.put("reservationId", r.id);
            event.put("customerEmail", r.customerEmail);
            event.put("status", r.status);
            event.put("message", message);
            event.put("totalMinor", r.totalMinor);
            event.put("timestamp", Instant.now().toString());
            rabbit.convertAndSend(EXCHANGE, type, mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Evenement {} non publie (broker indisponible ?) : {}", type, e.getMessage());
        }
    }
}
