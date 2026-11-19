package ma.mobility.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consomme les evenements de reservation (JSON texte) et "envoie" les notifications. */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository repo;
    private final ObjectMapper mapper;

    public NotificationListener(NotificationRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onEvent(String payload) throws Exception {
        JsonNode e = mapper.readTree(payload);
        String type = e.path("type").asText();
        String reservationId = e.path("reservationId").asText();
        String recipient = e.path("customerEmail").asText();
        String message = e.path("message").asText();

        repo.save(new Notification(type, reservationId, recipient, message));
        // Simulation d'envoi d'e-mail / SMS : un simple journal suffit pour la demo
        log.info("[NOTIFICATION -> {}] {} | reservation={} | {}", recipient, type, reservationId, message);
    }
}
