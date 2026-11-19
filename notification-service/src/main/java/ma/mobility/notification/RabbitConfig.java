package ma.mobility.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologie RabbitMQ : l'exchange "mobility.events" (topic) est alimente par la
 * saga ; ce service s'abonne a tous les evenements "reservation.#" via sa propre file.
 * Choreographie cote notifications : la saga ne sait meme pas que ce service existe.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mobility.events";
    public static final String QUEUE = "notifications";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue notificationsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding notificationsBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with("reservation.#");
    }
}
