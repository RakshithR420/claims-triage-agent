package com.rakshith.claimstriage.config;

import com.rakshith.claimstriage.domain.*;
import com.rakshith.claimstriage.repository.ClaimRepository;
import com.rakshith.claimstriage.repository.PolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.rakshith.claimstriage.domain.LossType.*;

/**
 * Seeds demo policies (and a little claim history) on startup. Dates are relative to today
 * so the demo data never expires.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PolicyRepository policies;
    private final ClaimRepository claims;

    public DataSeeder(PolicyRepository policies, ClaimRepository claims) {
        this.policies = policies;
        this.claims = claims;
    }

    @Override
    public void run(String... args) {
        if (policies.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        policies.saveAll(List.of(
                new Policy("POL-1001", "Asha Menon", ProductType.AUTO, PolicyStatus.ACTIVE,
                        Set.of(COLLISION, THEFT, WEATHER), money(25_000), money(500),
                        today.minusYears(1), today.plusYears(1)),
                new Policy("POL-1002", "Vikram Iyer", ProductType.HOME, PolicyStatus.ACTIVE,
                        Set.of(FIRE, WATER_DAMAGE, THEFT, WEATHER), money(300_000), money(1_000),
                        today.minusYears(2), today.plusMonths(6)),
                new Policy("POL-1003", "Daniel Joseph", ProductType.AUTO, PolicyStatus.LAPSED,
                        Set.of(COLLISION, THEFT), money(20_000), money(500),
                        today.minusYears(2), today.minusMonths(3)),
                new Policy("POL-1004", "Priya Nair", ProductType.TRAVEL, PolicyStatus.ACTIVE,
                        Set.of(TRAVEL_DELAY, MEDICAL, THEFT), money(10_000), money(100),
                        today.minusMonths(2), today.plusMonths(1)),
                new Policy("POL-1005", "Karthik Rao", ProductType.HOME, PolicyStatus.ACTIVE,
                        Set.of(FIRE, WATER_DAMAGE, THEFT), money(150_000), money(1_000),
                        today.minusDays(12), today.plusYears(1)),
                new Policy("POL-1006", "Meera Krishnan", ProductType.AUTO, PolicyStatus.ACTIVE,
                        Set.of(COLLISION, THEFT, WEATHER), money(30_000), money(500),
                        today.minusYears(3), today.plusMonths(9))
        ));

        // POL-1006 already has two claims in the last year -> claim-frequency fraud signal.
        Claim prior1 = new Claim("POL-1006", "Meera Krishnan", COLLISION, today.minusMonths(7),
                today.minusMonths(7), money(4_200), "Rear-ended at a signal, bumper and tail lamp damage.");
        prior1.setStatus(ClaimStatus.REVIEWED);
        Claim prior2 = new Claim("POL-1006", "Meera Krishnan", THEFT, today.minusMonths(3),
                today.minusMonths(3), money(1_800), "Side mirrors and stereo stolen from parked car.");
        prior2.setStatus(ClaimStatus.REVIEWED);
        claims.saveAll(List.of(prior1, prior2));

        log.info("Seeded {} demo policies (POL-1001..POL-1006)", policies.count());
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
