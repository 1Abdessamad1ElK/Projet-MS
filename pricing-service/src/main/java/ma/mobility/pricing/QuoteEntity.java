package ma.mobility.pricing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Devis conserve pour audit (base propre au service). */
@Entity
@Table(name = "quote")
public class QuoteEntity {

    @Id
    private String quoteId;
    private long subtotalMinor;
    private int discountPercent;
    private long discountMinor;
    private long totalMinor;
    private String currency;
    private Instant createdAt;

    protected QuoteEntity() {}

    public QuoteEntity(String quoteId, PricingRules.Result r, String currency) {
        this.quoteId = quoteId;
        this.subtotalMinor = r.subtotalMinor();
        this.discountPercent = r.discountPercent();
        this.discountMinor = r.discountMinor();
        this.totalMinor = r.totalMinor();
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public String getQuoteId() { return quoteId; }
    public long getSubtotalMinor() { return subtotalMinor; }
    public int getDiscountPercent() { return discountPercent; }
    public long getDiscountMinor() { return discountMinor; }
    public long getTotalMinor() { return totalMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
