package ma.mobility.pricing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regles de tarification multimodale (fonction pure, facile a tester) :
 *   - sous-total = somme(prix x quantite) ;
 *   - remise "multimodal" : 2 modes differents = 5 %, 3 modes (ou plus) = 10 %.
 * Montants en centimes de MAD (entiers, jamais de flottants pour l'argent).
 */
public final class PricingRules {

    private PricingRules() {}

    public record Line(String mode, String offerId, long priceMinor, int quantity) {}

    public record Result(long subtotalMinor, int discountPercent, long discountMinor, long totalMinor) {}

    public static Result compute(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        long subtotal = 0;
        Set<String> modes = new HashSet<>();
        for (Line l : lines) {
            if (l.quantity() < 1) {
                throw new IllegalArgumentException("quantity must be >= 1 for " + l.offerId());
            }
            if (l.priceMinor() < 0) {
                throw new IllegalArgumentException("priceMinor must be >= 0 for " + l.offerId());
            }
            subtotal += l.priceMinor() * l.quantity();
            modes.add(l.mode());
        }
        int percent = modes.size() >= 3 ? 10 : (modes.size() == 2 ? 5 : 0);
        long discount = subtotal * percent / 100;
        return new Result(subtotal, percent, discount, subtotal - discount);
    }
}
