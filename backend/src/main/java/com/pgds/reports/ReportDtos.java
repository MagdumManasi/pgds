package com.pgds.reports;

import com.pgds.repo.LabelCount;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record MonthlyRow(String period, BigDecimal riceKg, BigDecimal wheatKg, BigDecimal otherKg,
                             BigDecimal totalKg, long cardsServed) {}

    public record ShopRow(String shopCode, String shopName, String district, long beneficiaries, long cardsServed,
                          BigDecimal riceKg, BigDecimal wheatKg, BigDecimal totalKg, BigDecimal requirementKg,
                          double performancePercent, BigDecimal stockKg) {}

    public record DistrictRow(String district, int shops, long beneficiaries, long cardsServed,
                              BigDecimal riceKg, BigDecimal wheatKg, BigDecimal totalKg, BigDecimal requirementKg,
                              double performancePercent, BigDecimal stockKg) {}

    public record ComplaintReport(long total, List<LabelCount> byCategory, List<LabelCount> byStatus) {}
}
