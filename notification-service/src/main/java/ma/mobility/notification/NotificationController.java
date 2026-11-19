package ma.mobility.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    /** Liste des notifications envoyees (la plus recente d'abord) - utile pour la demo. */
    @GetMapping("/notifications")
    public List<Notification> list() {
        return repo.findAllByOrderByIdDesc();
    }
}
