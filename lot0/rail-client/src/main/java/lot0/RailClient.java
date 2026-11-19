package lot0;

import mobility.rail.NotAvailable;
import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;

import org.omg.CORBA.ORB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * LOT 0 - Client de test CORBA (operateur ferroviaire).
 *
 * Etapes CORBA a retenir (pour la soutenance !) :
 *   1. lire l'IOR (la "carte d'identite" reseau de l'objet distant) ;
 *   2. orb.string_to_object(ior)  -> reference generique ;
 *   3. RailBookingHelper.narrow() -> reference typee (stub genere depuis l'IDL) ;
 *   4. appeler les methodes comme si l'objet etait local.
 *
 * Lancer (JDK 8, voir docs/00-START-HERE.md), depuis lot0/rail-client :
 *     mvn -q -DskipTests package
 *     java -jar target/rail-client-1.0.0-jar-with-dependencies.jar
 *
 * Variable optionnelle : IOR_FILE (defaut ../../ior/RailBooking.ior)
 */
public final class RailClient {

    public static void main(String[] args) throws Exception {
        String iorFile = args.length > 0 ? args[0] : env("IOR_FILE", "../../ior/RailBooking.ior");
        String ior = new String(Files.readAllBytes(Paths.get(iorFile)), StandardCharsets.UTF_8).trim();

        Properties props = new Properties();
        props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        ORB orb = ORB.init(new String[0], props);

        try {
            org.omg.CORBA.Object obj = orb.string_to_object(ior);
            RailBooking rail = RailBookingHelper.narrow(obj);

            System.out.println("== 1. Recherche Casablanca -> Rabat (2026-11-15)");
            Trip[] trips = rail.search("Casablanca", "Rabat", "2026-11-15");
            for (Trip t : trips) {
                System.out.println("   " + t.tripId + "  " + t.origin + " -> " + t.destination
                        + "  " + t.departure + "  prix=" + t.priceMinor + " centimes"
                        + "  places=" + t.seatsAvailable);
            }

            System.out.println("== 2. Chemin nominal : hold -> confirm");
            String holdId = rail.hold("RAIL-CAS-RAB-0730", 2);
            System.out.println("   holdId     = " + holdId);
            String bookingRef = rail.confirm(holdId);
            System.out.println("   bookingRef = " + bookingRef);

            System.out.println("== 3. Compensation : cancel (rejoue 2 fois -> idempotent)");
            rail.cancel(bookingRef);
            System.out.println("   cancel #1 OK");
            rail.cancel(bookingRef);
            System.out.println("   cancel #2 OK (aucune erreur, c'est le comportement attendu)");

            System.out.println("== 4. Echec voulu : 100000 places sur un train");
            try {
                rail.hold("RAIL-CAS-RAB-0730", 100000);
                System.out.println("   ??? aucune erreur : inattendu");
            } catch (NotAvailable e) {
                System.out.println("   OK, NotAvailable recue : " + e.reason);
            }
        } finally {
            orb.shutdown(true);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    private RailClient() {}
}
