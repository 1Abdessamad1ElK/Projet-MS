package ma.mobility.reservations.saga;

import ma.mobility.reservations.saga.Models.Booking;
import ma.mobility.reservations.saga.Models.Hold;
import ma.mobility.reservations.saga.Models.LegRequest;
import ma.mobility.reservations.saga.Models.NotAvailableException;
import ma.mobility.reservations.saga.Models.Offer;
import ma.mobility.reservations.saga.Models.Quote;
import ma.mobility.reservations.saga.Models.QuoteLine;
import ma.mobility.reservations.saga.Models.Reservation;
import ma.mobility.reservations.saga.Ports.Bridge;
import ma.mobility.reservations.saga.Ports.EventPublisher;
import ma.mobility.reservations.saga.Ports.Pricing;
import ma.mobility.reservations.saga.Ports.ReservationStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests de la saga avec de faux operateurs : aucun reseau, aucune base, execution instantanee. */
class SagaOrchestratorTest {

    // ---------- faux operateur ----------
    static class FakeBridge implements Bridge {
        final String mode;
        final List<String> calls = new ArrayList<>();
        final Set<String> failHoldOn;
        final Set<String> failConfirmOn;
        final Map<String, String> holdToOffer = new LinkedHashMap<>();
        int holdCounter = 0;

        FakeBridge(String mode, Set<String> failHoldOn, Set<String> failConfirmOn) {
            this.mode = mode;
            this.failHoldOn = failHoldOn;
            this.failConfirmOn = failConfirmOn;
        }

        @Override
        public List<Offer> search(String origin, String destination, String date) {
            // une offre "<mode>-1" et une offre "<mode>-FULL"
            return List.of(
                    new Offer(mode + "-1", mode, origin, destination, "d", "a", 10000, "MAD", 10),
                    new Offer(mode + "-FULL", mode, origin, destination, "d", "a", 20000, "MAD", 1));
        }

        @Override
        public Hold hold(String offerId, int seats) {
            calls.add("hold:" + offerId);
            if (failHoldOn.contains(offerId)) {
                throw new NotAvailableException("no seats on " + offerId);
            }
            String holdId = "H-" + mode + "-" + (++holdCounter);
            holdToOffer.put(holdId, offerId);
            return new Hold(holdId, offerId, mode);
        }

        @Override
        public Booking confirm(String holdId) {
            calls.add("confirm:" + holdId);
            if (failConfirmOn.contains(holdToOffer.get(holdId))) {
                throw new NotAvailableException("confirm refused for " + holdToOffer.get(holdId));
            }
            return new Booking("B-" + holdId, holdId, mode);
        }

        @Override
        public void cancel(String reference) {
            calls.add("cancel:" + reference);
        }
    }

    static class MemoryStore implements ReservationStore {
        final Map<String, Reservation> byId = new LinkedHashMap<>();
        @Override public void save(Reservation r) { byId.put(r.id, r); }
        @Override public Optional<Reservation> findById(String id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Reservation> findByIdempotencyKey(String key) {
            return byId.values().stream().filter(r -> key.equals(r.idempotencyKey)).findFirst();
        }
        @Override public List<Reservation> findAll() { return new ArrayList<>(byId.values()); }
    }

    FakeBridge train, flight, car;
    MemoryStore store;
    List<String> events;
    SagaOrchestrator saga;
    long now;

    @BeforeEach
    void setUp() {
        train = new FakeBridge("TRAIN", Set.of(), Set.of());
        flight = new FakeBridge("FLIGHT", Set.of("FLIGHT-FULL"), Set.of());
        car = new FakeBridge("CAR", Set.of(), Set.of());
        build();
    }

    void build() {
        store = new MemoryStore();
        events = new ArrayList<>();
        now = 0;
        Map<String, Bridge> bridges = new LinkedHashMap<>();
        bridges.put("TRAIN", train);
        bridges.put("FLIGHT", flight);
        bridges.put("CAR", car);
        Pricing pricing = (List<QuoteLine> lines) -> {
            long sum = 0;
            for (QuoteLine l : lines) sum += l.priceMinor() * l.quantity();
            return new Quote("Q1", sum, 0, 0, sum, "MAD");
        };
        EventPublisher publisher = (type, r, msg) -> events.add(type);
        saga = new SagaOrchestrator(bridges, pricing, store, publisher, () -> now, 480_000);
    }

    static List<LegRequest> legs(String flightOffer) {
        return List.of(
                new LegRequest("TRAIN", "TRAIN-1", 1, "A", "B", "2026-11-15"),
                new LegRequest("FLIGHT", flightOffer, 1, "B", "C", "2026-11-15"),
                new LegRequest("CAR", "CAR-1", 1, "C", "C", "2026-11-15"));
    }

    // ---------- scenarios ----------

    @Test
    void nominalPathConfirmsEveryLeg() {
        Reservation r = saga.book("a@b.ma", legs("FLIGHT-1"), null);

        assertEquals("CONFIRMED", r.status);
        assertEquals(30000, r.totalMinor); // prix relus aupres des bridges (10000 x 3)
        for (var leg : r.legs) {
            assertEquals("CONFIRMED", leg.status);
            assertTrue(leg.bookingRef.startsWith("B-"));
        }
        assertEquals(List.of("reservation.requested", "reservation.leg.held", "reservation.leg.held",
                "reservation.leg.held", "reservation.confirmed"), events);
    }

    @Test
    void holdFailureCompensatesHoldsAlreadyObtained() {
        // le vol "FLIGHT-FULL" refuse le hold : le train deja maintenu doit etre libere
        Reservation r = saga.book("a@b.ma", legs("FLIGHT-FULL"), null);

        assertEquals("FAILED", r.status);
        assertEquals("CANCELLED", r.legs.get(0).status);  // train annule (compensation)
        assertEquals("FAILED", r.legs.get(1).status);     // vol : le hold a echoue
        assertEquals("PENDING", r.legs.get(2).status);    // voiture : jamais tentee
        assertTrue(train.calls.contains("cancel:H-TRAIN-1"));
        assertTrue(car.calls.isEmpty());                   // aucun appel a la voiture
        assertTrue(events.contains("reservation.failed"));
    }

    @Test
    void confirmFailureCompensatesConfirmedBookingsAndRemainingHolds() {
        // la voiture "CAR-1" sera maintenue puis refusera le confirm
        car = new FakeBridge("CAR", Set.of(), Set.of("CAR-1"));
        build();
        Reservation r = saga.book("a@b.ma", legs("FLIGHT-1"), null);

        assertEquals("FAILED", r.status);
        for (var leg : r.legs) {
            assertEquals("CANCELLED", leg.status);
        }
        // train et vol etaient CONFIRMED -> on annule leur bookingRef ; voiture etait HELD -> on annule le holdId
        assertTrue(train.calls.contains("cancel:B-H-TRAIN-1"));
        assertTrue(flight.calls.contains("cancel:B-H-FLIGHT-1"));
        assertTrue(car.calls.contains("cancel:H-CAR-1"));
    }

    @Test
    void sameIdempotencyKeyDoesNotReplayAnything() {
        Reservation first = saga.book("a@b.ma", legs("FLIGHT-1"), "key-42");
        int callsAfterFirst = train.calls.size();

        Reservation second = saga.book("a@b.ma", legs("FLIGHT-1"), "key-42");

        assertEquals(first.id, second.id);
        assertEquals(callsAfterFirst, train.calls.size()); // aucun nouvel appel a l'operateur
        assertEquals(1, store.findAll().size());
    }

    @Test
    void unknownOfferFailsBeforeAnyHold() {
        Reservation r = saga.book("a@b.ma", List.of(
                new LegRequest("TRAIN", "TRAIN-DOES-NOT-EXIST", 1, "A", "B", "2026-11-15")), null);

        assertEquals("FAILED", r.status);
        assertTrue(train.calls.isEmpty()); // phase 0 : rien a compenser
    }

    @Test
    void confirmWindowExceededTriggersCompensation() {
        // le 2e hold met 9 minutes a repondre : on refuse alors de confirmer des holds presque expires
        long[] clock = {0};
        FakeBridge okTrain = new FakeBridge("TRAIN", Set.of(), Set.of());
        FakeBridge slowFlight = new FakeBridge("FLIGHT", Set.of(), Set.of()) {
            @Override public Hold hold(String offerId, int seats) {
                Hold h = super.hold(offerId, seats);
                clock[0] += 540_000;
                return h;
            }
        };
        Map<String, Bridge> bridges = new LinkedHashMap<>();
        bridges.put("TRAIN", okTrain);
        bridges.put("FLIGHT", slowFlight);
        SagaOrchestrator s = new SagaOrchestrator(bridges,
                lines -> new Quote("Q", 20000, 0, 0, 20000, "MAD"),
                store, (t, r, m) -> { }, () -> clock[0], 480_000);

        Reservation r = s.book("a@b.ma", List.of(
                new LegRequest("TRAIN", "TRAIN-1", 1, "A", "B", "d"),
                new LegRequest("FLIGHT", "FLIGHT-1", 1, "B", "C", "d")), null);

        assertEquals("FAILED", r.status);
        assertTrue(okTrain.calls.contains("cancel:H-TRAIN-1"));
        assertTrue(slowFlight.calls.contains("cancel:H-FLIGHT-1"));
        assertTrue(r.failureReason.contains("window"));
    }

    @Test
    void invalidRequestIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> saga.book("", legs("FLIGHT-1"), null));
        assertThrows(IllegalArgumentException.class, () -> saga.book("a@b.ma", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> saga.book("a@b.ma", List.of(
                new LegRequest("BOAT", "X", 1, "A", "B", "d")), null));
        assertEquals(0, store.findAll().size());
    }
}
