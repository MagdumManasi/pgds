package com.pgds.reports;

import com.pgds.reports.ReportDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** /api/officer/** = ADMIN + OFFICER, /api/audit/** = ADMIN + AUDITOR (see SecurityConfig). Every report also has a .csv export. */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private static final String M = "/api/officer/reports/monthly", MA = "/api/audit/reports/monthly";
    private static final String D = "/api/officer/reports/districts", DA = "/api/audit/reports/districts";
    private static final String S = "/api/officer/reports/shops", SA = "/api/audit/reports/shops";
    private static final String C = "/api/officer/reports/complaints", CA = "/api/audit/reports/complaints";

    private final ReportService service;

    // ----- JSON -----
    @GetMapping({M, MA}) public List<MonthlyRow> monthly(@RequestParam(defaultValue = "12") int months) { return service.monthly(months); }
    @GetMapping({D, DA}) public List<DistrictRow> districts(@RequestParam(required = false) String period) { return service.districts(period); }
    @GetMapping({S, SA}) public List<ShopRow> shops(@RequestParam(required = false) String period) { return service.shops(period); }
    @GetMapping({C, CA}) public ComplaintReport complaints() { return service.complaints(); }

    // ----- CSV -----
    @GetMapping({M + ".csv", MA + ".csv"})
    public ResponseEntity<String> monthlyCsv(@RequestParam(defaultValue = "12") int months) {
        var rows = service.monthly(months).stream().map(r -> row(r.period(), r.riceKg(), r.wheatKg(), r.otherKg(), r.totalKg(), r.cardsServed())).toList();
        return csv("monthly-report.csv", CsvWriter.toCsv(List.of("period", "rice_kg", "wheat_kg", "other_kg", "total_kg", "cards_served"), rows));
    }

    @GetMapping({D + ".csv", DA + ".csv"})
    public ResponseEntity<String> districtsCsv(@RequestParam(required = false) String period) {
        var rows = service.districts(period).stream().map(r -> row(r.district(), r.shops(), r.beneficiaries(), r.cardsServed(),
                r.riceKg(), r.wheatKg(), r.totalKg(), r.requirementKg(), r.performancePercent(), r.stockKg())).toList();
        return csv("district-report.csv", CsvWriter.toCsv(List.of("district", "shops", "beneficiaries", "cards_served", "rice_kg",
                "wheat_kg", "total_kg", "requirement_kg", "performance_percent", "stock_kg"), rows));
    }

    @GetMapping({S + ".csv", SA + ".csv"})
    public ResponseEntity<String> shopsCsv(@RequestParam(required = false) String period) {
        var rows = service.shops(period).stream().map(r -> row(r.shopCode(), r.shopName(), r.district(), r.beneficiaries(),
                r.cardsServed(), r.riceKg(), r.wheatKg(), r.totalKg(), r.requirementKg(), r.performancePercent(), r.stockKg())).toList();
        return csv("shop-report.csv", CsvWriter.toCsv(List.of("shop_code", "shop_name", "district", "beneficiaries", "cards_served",
                "rice_kg", "wheat_kg", "total_kg", "requirement_kg", "performance_percent", "stock_kg"), rows));
    }

    @GetMapping({C + ".csv", CA + ".csv"})
    public ResponseEntity<String> complaintsCsv() {
        ComplaintReport r = service.complaints();
        var rows = new java.util.ArrayList<List<Object>>();
        r.byCategory().forEach(c -> rows.add(row("category", c.label(), c.count())));
        r.byStatus().forEach(c -> rows.add(row("status", c.label(), c.count())));
        return csv("complaint-report.csv", CsvWriter.toCsv(List.of("group", "label", "count"), rows));
    }

    private static List<Object> row(Object... cells) { return Arrays.asList(cells); }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
