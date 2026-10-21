package com.pgds.reports;

import com.pgds.common.ApiException;
import com.pgds.domain.ComplaintStatus;
import com.pgds.domain.GrainType;
import com.pgds.entitlement.EntitlementService;
import com.pgds.reports.ReportDtos.*;
import com.pgds.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Government-level reports. All figures come from the distribution and stock ledgers. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final DistributionRepository distributions;
    private final FairPriceShopRepository shops;
    private final RationCardRepository cards;
    private final BeneficiaryRepository beneficiaries;
    private final StockTransactionRepository txns;
    private final ComplaintRepository complaints;
    private final EntitlementService entitlement;
    private final Clock clock;

    /** Distribution per month for the last N months (1-36). */
    public List<MonthlyRow> monthly(int months) {
        int n = Math.max(1, Math.min(months, 36));
        YearMonth now = YearMonth.now(clock);
        List<String> periods = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) periods.add(now.minusMonths(i).toString());

        Map<String, Map<GrainType, BigDecimal>> kgs = new HashMap<>();
        distributions.trendSince(periods.get(0)).forEach(r ->
                kgs.computeIfAbsent(r.period(), k -> new EnumMap<>(GrainType.class)).put(r.grainType(), nz(r.kg())));
        Map<String, Long> served = new HashMap<>();
        distributions.cardsServedByPeriod(periods.get(0)).forEach(r -> served.put(r.period(), r.count()));

        return periods.stream().map(p -> {
            Map<GrainType, BigDecimal> m = kgs.getOrDefault(p, Map.of());
            BigDecimal rice = m.getOrDefault(GrainType.RICE, BigDecimal.ZERO);
            BigDecimal wheat = m.getOrDefault(GrainType.WHEAT, BigDecimal.ZERO);
            BigDecimal other = m.getOrDefault(GrainType.OTHER, BigDecimal.ZERO);
            return new MonthlyRow(p, rice, wheat, other, rice.add(wheat).add(other), served.getOrDefault(p, 0L));
        }).toList();
    }

    /** Shop performance for one month. Performance = issued / monthly entitlement of the shop's eligible cards, capped at 100. */
    public List<ShopRow> shops(String period) {
        String p = validPeriod(period);
        Map<Long, Map<GrainType, BigDecimal>> issued = new HashMap<>();
        distributions.distributedByShopGrain(p).forEach(r ->
                issued.computeIfAbsent(r.fpsId(), k -> new EnumMap<>(GrainType.class)).put(r.grainType(), nz(r.kg())));
        Map<Long, Long> served = new HashMap<>();
        distributions.cardsServedByShop(p).forEach(r -> served.put(r.fpsId(), r.count()));
        Map<Long, Long> people = new HashMap<>();
        beneficiaries.countByShop().forEach(r -> people.put(r.fpsId(), r.count()));
        Map<Long, BigDecimal> need = new HashMap<>();
        cards.shopDemand().forEach(r -> need.merge(r.fpsId(), entitlement.groupTotalKg(r.category(),
                r.cards() == null ? 0 : r.cards(), r.members() == null ? 0 : r.members()), BigDecimal::add));
        Map<Long, BigDecimal> stock = new HashMap<>();
        txns.fpsBalances().forEach(r -> stock.merge(r.locationId(), nz(r.kg()), BigDecimal::add));

        return shops.findAll().stream().map(s -> {
            Map<GrainType, BigDecimal> m = issued.getOrDefault(s.getId(), Map.of());
            BigDecimal rice = m.getOrDefault(GrainType.RICE, BigDecimal.ZERO);
            BigDecimal wheat = m.getOrDefault(GrainType.WHEAT, BigDecimal.ZERO);
            BigDecimal total = rice.add(wheat).add(m.getOrDefault(GrainType.OTHER, BigDecimal.ZERO));
            BigDecimal req = need.getOrDefault(s.getId(), BigDecimal.ZERO);
            return new ShopRow(s.getShopCode(), s.getName(), s.getDistrict(), people.getOrDefault(s.getId(), 0L),
                    served.getOrDefault(s.getId(), 0L), rice, wheat, total, req, percent(total, req),
                    stock.getOrDefault(s.getId(), BigDecimal.ZERO));
        }).sorted(Comparator.comparing(ShopRow::district).thenComparing(ShopRow::shopCode)).toList();
    }

    /** District roll-up of the shop report. */
    public List<DistrictRow> districts(String period) {
        Map<String, List<ShopRow>> byDistrict = new TreeMap<>();
        for (ShopRow r : shops(period)) byDistrict.computeIfAbsent(r.district(), k -> new ArrayList<>()).add(r);
        List<DistrictRow> out = new ArrayList<>();
        byDistrict.forEach((d, list) -> {
            BigDecimal rice = sum(list, ShopRow::riceKg), wheat = sum(list, ShopRow::wheatKg);
            BigDecimal total = sum(list, ShopRow::totalKg), req = sum(list, ShopRow::requirementKg);
            out.add(new DistrictRow(d, list.size(), list.stream().mapToLong(ShopRow::beneficiaries).sum(),
                    list.stream().mapToLong(ShopRow::cardsServed).sum(), rice, wheat, total, req,
                    percent(total, req), sum(list, ShopRow::stockKg)));
        });
        return out;
    }

    public ComplaintReport complaints() {
        List<LabelCount> byCategory = complaints.countByCategory().stream()
                .map(c -> new LabelCount(c.label() == null ? "UNCATEGORISED" : c.label(), c.count()))
                .sorted(Comparator.comparingLong(LabelCount::count).reversed()).toList();
        List<LabelCount> byStatus = Arrays.stream(ComplaintStatus.values())
                .map(s -> new LabelCount(s.name(), complaints.countByStatus(s))).toList();
        return new ComplaintReport(complaints.count(), byCategory, byStatus);
    }

    // ---------- helpers ----------

    private String validPeriod(String period) {
        if (period == null || period.isBlank()) return YearMonth.now(clock).toString();
        try { return YearMonth.parse(period).toString(); }
        catch (DateTimeParseException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "period must look like 2026-10"); }
    }

    private static double percent(BigDecimal done, BigDecimal required) {
        if (required.signum() == 0) return 0;
        double v = done.multiply(BigDecimal.valueOf(100)).divide(required, 2, RoundingMode.HALF_UP).doubleValue();
        return Math.min(100.0, Math.round(v * 10.0) / 10.0);
    }

    private static <T> BigDecimal sum(List<T> list, java.util.function.Function<T, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
