package ma.mobility.carshare;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un modele de vehicule disponible dans une ville (units = exemplaires libres). */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private String offerId;
    private String city;
    private String model;
    private long pricePerDayMinor;
    private int units;

    protected Vehicle() {}

    public Vehicle(String offerId, String city, String model, long pricePerDayMinor, int units) {
        this.offerId = offerId;
        this.city = city;
        this.model = model;
        this.pricePerDayMinor = pricePerDayMinor;
        this.units = units;
    }

    public String getOfferId() { return offerId; }
    public String getCity() { return city; }
    public String getModel() { return model; }
    public long getPricePerDayMinor() { return pricePerDayMinor; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
}
