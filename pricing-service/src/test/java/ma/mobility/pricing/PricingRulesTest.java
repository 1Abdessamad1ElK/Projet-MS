package ma.mobility.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingRulesTest {

    @Test
    void singleModeHasNoDiscount() {
        PricingRules.Result r = PricingRules.compute(List.of(
                new PricingRules.Line("TRAIN", "T1", 8000, 2)));
        assertEquals(16000, r.subtotalMinor());
        assertEquals(0, r.discountPercent());
        assertEquals(16000, r.totalMinor());
    }

    @Test
    void twoModesGetFivePercent() {
        PricingRules.Result r = PricingRules.compute(List.of(
                new PricingRules.Line("TRAIN", "T1", 8000, 1),
                new PricingRules.Line("FLIGHT", "F1", 12000, 1)));
        assertEquals(20000, r.subtotalMinor());
        assertEquals(5, r.discountPercent());
        assertEquals(1000, r.discountMinor());
        assertEquals(19000, r.totalMinor());
    }

    @Test
    void threeModesGetTenPercent() {
        PricingRules.Result r = PricingRules.compute(List.of(
                new PricingRules.Line("TRAIN", "T1", 8000, 1),
                new PricingRules.Line("FLIGHT", "F1", 180000, 1),
                new PricingRules.Line("CAR", "C1", 35000, 1)));
        assertEquals(223000, r.subtotalMinor());
        assertEquals(10, r.discountPercent());
        assertEquals(22300, r.discountMinor());
        assertEquals(200700, r.totalMinor());
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PricingRules.compute(List.of()));
        assertThrows(IllegalArgumentException.class, () -> PricingRules.compute(List.of(
                new PricingRules.Line("TRAIN", "T1", 8000, 0))));
    }
}
