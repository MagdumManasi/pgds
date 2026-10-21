package com.pgds.assistant;

import com.pgds.ai.AlertService;
import com.pgds.dashboard.DashboardService;
import com.pgds.distribution.DistributionService;
import com.pgds.domain.AlertStatus;
import com.pgds.domain.Role;
import com.pgds.repo.BeneficiaryRepository;
import com.pgds.repo.UserRepository;
import com.pgds.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live facts the assistant may use, chosen by the caller's role. This is the access-control boundary of the assistant:
 * a beneficiary only gets their own entitlement, a dealer or warehouse manager only their own stock, and only
 * officers and admins get system-wide figures. Nothing outside this list ever reaches the language model.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataContextService {

    private static final Pattern SHOP_CODE = Pattern.compile("FPS\\d+", Pattern.CASE_INSENSITIVE);
    private static final List<String> ALERT_WORDS = List.of("alert", "flag", "anomal", "fraud", "suspicious", "why", "unusual", "burst");
    private static final List<String> STOCK_WORDS = List.of("low", "stock", "shortage", "critical", "run out");

    private final DashboardService dashboard;
    private final AlertService alerts;
    private final StockService stock;
    private final BeneficiaryRepository beneficiaries;
    private final DistributionService distributions;
    private final UserRepository users;

    public List<String> build(String username, Role role, String question) {
        List<String> out = new ArrayList<>();
        try {
            switch (role) {
                case SUPER_ADMIN, GOVT_OFFICER -> overseer(question, true, out);
                case AUDITOR -> overseer(question, false, out);
                case WAREHOUSE_MANAGER -> warehouse(username, out);
                case FPS_DEALER -> dealer(username, out);
                case BENEFICIARY -> beneficiary(username, out);
            }
        } catch (Exception e) {
            // live data is best effort; the assistant can still answer from the policy notes
        }
        return out;
    }

    private void overseer(String question, boolean fullAccess, List<String> out) {
        String lower = question.toLowerCase(Locale.ROOT);
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = SHOP_CODE.matcher(question);
        while (m.find()) codes.add(m.group().toUpperCase(Locale.ROOT));

        if (fullAccess) {
            var s = dashboard.summary();
            out.add("Period " + s.period() + ": " + s.distributedThisMonthKg().toPlainString() + " kg distributed so far, total stock "
                    + s.totalStockKg().toPlainString() + " kg, " + s.healthyShopPercent() + " percent of shops healthy, "
                    + s.openComplaints() + " open complaints, " + s.openAiAlerts() + " open AI alerts (" + s.highPriorityAiAlerts() + " high priority).");
            var shops = dashboard.shopStatuses(null);
            if (!codes.isEmpty()) {
                shops.stream().filter(x -> codes.contains(x.shopCode())).forEach(x -> out.add(x.shopCode() + " (" + x.name() + "): stock "
                        + x.stockKg().toPlainString() + " kg, monthly need " + x.monthlyRequirementKg().toPlainString()
                        + " kg, status " + x.status() + "."));
            } else if (STOCK_WORDS.stream().anyMatch(lower::contains)) {
                shops.stream().filter(x -> x.status().name().equals("LOW") || x.status().name().equals("CRITICAL")).limit(5)
                        .forEach(x -> out.add(x.shopCode() + " (" + x.name() + ") has " + x.status() + " stock: " + x.stockKg().toPlainString()
                                + " kg against a monthly need of " + x.monthlyRequirementKg().toPlainString() + " kg."));
            }
        }
        if (!codes.isEmpty() || ALERT_WORDS.stream().anyMatch(lower::contains)) {
            var open = alerts.list(AlertStatus.OPEN, PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "detectedAt"))).items();
            open.stream().filter(a -> codes.isEmpty() || (a.shopCode() != null && codes.contains(a.shopCode()))).limit(5)
                    .forEach(a -> out.add("Open alert [" + a.severity() + "] " + a.title() + ": " + a.details()));
        }
    }

    private void warehouse(String username, List<String> out) {
        var u = users.findByUsername(username).orElseThrow();
        if (u.getWarehouse() == null) return;
        var s = stock.warehouseStock(username, u.getWarehouse().getId());
        s.balances().forEach(b -> out.add("Warehouse " + s.name() + " holds " + b.quantityKg().toPlainString() + " kg " + b.grainType() + "."));
    }

    private void dealer(String username, List<String> out) {
        var u = users.findByUsername(username).orElseThrow();
        if (u.getFps() == null) return;
        Long id = u.getFps().getId();
        var s = stock.fpsStock(username, id);
        s.balances().forEach(b -> out.add("Your shop " + s.name() + " holds " + b.quantityKg().toPlainString() + " kg " + b.grainType() + "."));
        dashboard.shopStatuses(null).stream().filter(x -> id.equals(x.fpsId())).findFirst()
                .ifPresent(x -> out.add("Your shop stock status is " + x.status() + " (monthly need " + x.monthlyRequirementKg().toPlainString() + " kg)."));
    }

    private void beneficiary(String username, List<String> out) {
        var b = beneficiaries.findByUserUsername(username);
        if (b.isEmpty()) { out.add("Your login is not linked to a ration card yet. An officer must link it."); return; }
        var bal = distributions.balance(b.get().getRationCard());
        bal.grains().forEach(g -> out.add("Card " + bal.cardNumber() + ", " + bal.period() + ", " + g.grainType() + ": "
                + g.entitledKg().toPlainString() + " kg entitled, " + g.distributedKg().toPlainString() + " kg received, "
                + g.remainingKg().toPlainString() + " kg remaining."));
    }
}
