package ma.mobility.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    public record LoginRequest(String username, String password) {}

    private final JwtService jwt;
    private final Map<String, String> users = new HashMap<>();

    /** gateway.users = "demo:demo123,admin:admin123" (comptes de demonstration). */
    public AuthController(JwtService jwt, @Value("${gateway.users}") String usersProperty) {
        this.jwt = jwt;
        for (String pair : usersProperty.split(",")) {
            String[] kv = pair.trim().split(":", 2);
            if (kv.length == 2) {
                users.put(kv[0], kv[1]);
            }
        }
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        String expected = req.username() == null ? null : users.get(req.username());
        if (expected == null || !expected.equals(req.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "BAD_CREDENTIALS", "detail", "Unknown user or wrong password"));
        }
        return ResponseEntity.ok(Map.of("token", jwt.issue(req.username()), "expiresInSeconds", jwt.ttlSeconds()));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
