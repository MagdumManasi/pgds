package com.pgds.stock;

import com.pgds.common.ApiException;
import com.pgds.common.PageResponse;
import com.pgds.domain.*;
import com.pgds.entitlement.EntitlementService;
import com.pgds.repo.*;
import com.pgds.security.AccessGuard;
import com.pgds.stock.StockDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ledger-based stock. Nothing is ever updated in place: every movement is a new StockTransaction row
 * and the balance is the signed sum. Outflows lock the source row so two requests cannot overdraw stock.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final WarehouseRepository warehouses;
    private final FairPriceShopRepository shops;
    private final StockTransactionRepository txns;
    private final RationCardRepository cards;
    private final EntitlementService entitlements;
    private final AccessGuard access;

    // ---------- warehouse ----------

    @Transactional(readOnly = true)
    public StockSummary warehouseStock(String username, Long warehouseId) {
        access.checkWarehouse(username, warehouseId);
        Warehouse w = warehouses.findById(warehouseId).orElseThrow(() -> notFound("Warehouse"));
        return new StockSummary(LocationType.WAREHOUSE, w.getId(), w.getName(),
                balances(g -> txns.warehouseBalance(w.getId(), g)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TxnView> warehouseTransactions(String username, Long warehouseId, Pageable p) {
        access.checkWarehouse(username, warehouseId);
        return PageResponse.of(txns.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, p).map(TxnView::of));
    }

    public TxnView receive(String username, Long warehouseId, StockEntry e) {
        access.checkWarehouse(username, warehouseId);
        Warehouse w = warehouses.findById(warehouseId).orElseThrow(() -> notFound("Warehouse"));
        return TxnView.of(txns.save(warehouseTxn(w, e.grainType(), StockTxnType.RECEIPT, e.quantityKg(), e.remarks(), username)));
    }

    public TxnView damageAtWarehouse(String username, Long warehouseId, StockEntry e) {
        access.checkWarehouse(username, warehouseId);
        Warehouse w = warehouses.findByIdForUpdate(warehouseId).orElseThrow(() -> notFound("Warehouse"));
        requireBalance(txns.warehouseBalance(w.getId(), e.grainType()), e.quantityKg());
        return TxnView.of(txns.save(warehouseTxn(w, e.grainType(), StockTxnType.DAMAGE, e.quantityKg(), e.remarks(), username)));
    }

    /** Warehouse -> FPS. Writes TRANSFER_OUT and TRANSFER_IN in one transaction. Returns the outgoing entry. */
    public TxnView transfer(String username, Long warehouseId, TransferRequest r) {
        access.checkWarehouse(username, warehouseId);
        Warehouse w = warehouses.findByIdForUpdate(warehouseId).orElseThrow(() -> notFound("Warehouse"));
        FairPriceShop fps = shops.findById(r.fpsId()).orElseThrow(() -> notFound("Fair Price Shop"));
        if (fps.getWarehouse() == null || !fps.getWarehouse().getId().equals(w.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "This shop is not served by the selected warehouse");
        requireBalance(txns.warehouseBalance(w.getId(), r.grainType()), r.quantityKg());

        String note = r.remarks() == null ? "" : r.remarks();
        StockTransaction out = warehouseTxn(w, r.grainType(), StockTxnType.TRANSFER_OUT, r.quantityKg(),
                "To " + fps.getShopCode() + (note.isEmpty() ? "" : " - " + note), username);
        StockTransaction in = StockTransaction.builder().locationType(LocationType.FPS).fps(fps)
                .grainType(r.grainType()).txnType(StockTxnType.TRANSFER_IN).quantityKg(r.quantityKg())
                .remarks("From warehouse " + w.getId() + (note.isEmpty() ? "" : " - " + note)).createdBy(username).build();
        txns.saveAll(List.of(out, in));
        return TxnView.of(out);
    }

    /** Shops supplied by this warehouse (for the transfer form). */
    @Transactional(readOnly = true)
    public List<ShopRef> shopsServed(String username, Long warehouseId) {
        access.checkWarehouse(username, warehouseId);
        Map<Long, BigDecimal[]> requirements = new HashMap<>();
        Map<Long, BigDecimal[]> sentThisMonth = new HashMap<>();
        Map<Long, BigDecimal[]> sentTotal = new HashMap<>();
        for (ShopDemandRow row : cards.shopDemand()) {
            BigDecimal totalKg = entitlements.groupTotalKg(row.category(), row.cards(), row.members());
            BigDecimal riceKg = totalKg.multiply(entitlements.riceShare()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal wheatKg = totalKg.subtract(riceKg).setScale(2, RoundingMode.HALF_UP);
            BigDecimal[] demand = requirements.computeIfAbsent(row.fpsId(),
                    ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            demand[0] = demand[0].add(riceKg);
            demand[1] = demand[1].add(wheatKg);
        }
        Instant monthStart = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .withDayOfMonth(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        addTransfers(sentThisMonth, txns.fpsTransfersSince(monthStart));
        addTransfers(sentTotal, txns.fpsTransfers());
        return shops.findByWarehouseId(warehouseId).stream()
                .map(s -> {
                    BigDecimal[] demand = requirements.getOrDefault(s.getId(),
                            new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    BigDecimal[] monthSent = sentThisMonth.getOrDefault(s.getId(),
                            new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    BigDecimal[] totalSent = sentTotal.getOrDefault(s.getId(),
                            new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    BigDecimal riceStock = txns.fpsBalance(s.getId(), GrainType.RICE);
                    BigDecimal wheatStock = txns.fpsBalance(s.getId(), GrainType.WHEAT);
                    return new ShopRef(s.getId(), s.getShopCode(), s.getName(), s.getDistrict(), s.getAddress(),
                            demand[0], demand[1], riceStock, wheatStock, monthSent[0], monthSent[1],
                            totalSent[0], totalSent[1],
                            shortfall(demand[0], riceStock), shortfall(demand[1], wheatStock));
                }).toList();
    }

    private void addTransfers(Map<Long, BigDecimal[]> totals, List<LocationGrainBalance> transfers) {
        for (LocationGrainBalance transfer : transfers) {
            BigDecimal[] grains = totals.computeIfAbsent(transfer.locationId(),
                    ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int index = transfer.grainType() == GrainType.RICE ? 0 : transfer.grainType() == GrainType.WHEAT ? 1 : -1;
            if (index >= 0) grains[index] = grains[index].add(transfer.kg());
        }
    }

    // ---------- fair price shop ----------

    @Transactional(readOnly = true)
    public StockSummary fpsStock(String username, Long fpsId) {
        access.checkFps(username, fpsId);
        FairPriceShop s = shops.findById(fpsId).orElseThrow(() -> notFound("Fair Price Shop"));
        return new StockSummary(LocationType.FPS, s.getId(), s.getName(), balances(g -> txns.fpsBalance(s.getId(), g)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TxnView> fpsTransactions(String username, Long fpsId, Pageable p) {
        access.checkFps(username, fpsId);
        return PageResponse.of(txns.findByFpsIdOrderByCreatedAtDesc(fpsId, p).map(TxnView::of));
    }

    public TxnView damageAtFps(String username, Long fpsId, StockEntry e) {
        access.checkFps(username, fpsId);
        FairPriceShop s = shops.findByIdForUpdate(fpsId).orElseThrow(() -> notFound("Fair Price Shop"));
        requireBalance(txns.fpsBalance(s.getId(), e.grainType()), e.quantityKg());
        return TxnView.of(txns.save(StockTransaction.builder().locationType(LocationType.FPS).fps(s)
                .grainType(e.grainType()).txnType(StockTxnType.DAMAGE).quantityKg(e.quantityKg())
                .remarks(e.remarks()).createdBy(username).build()));
    }

    // ---------- helpers ----------

    private StockTransaction warehouseTxn(Warehouse w, GrainType g, StockTxnType type, BigDecimal qty, String remarks, String user) {
        return StockTransaction.builder().locationType(LocationType.WAREHOUSE).warehouse(w)
                .grainType(g).txnType(type).quantityKg(qty).remarks(remarks).createdBy(user).build();
    }

    private List<StockBalance> balances(java.util.function.Function<GrainType, BigDecimal> fn) {
        return Arrays.stream(GrainType.values()).map(g -> new StockBalance(g, fn.apply(g))).toList();
    }

    private void requireBalance(BigDecimal available, BigDecimal requested) {
        if (available == null || available.compareTo(requested) < 0)
            throw new ApiException(HttpStatus.CONFLICT,
                    "Insufficient stock: available " + (available == null ? "0" : available.toPlainString()) + " kg");
    }

    private BigDecimal shortfall(BigDecimal requirement, BigDecimal available) {
        return requirement.subtract(available).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND, what + " not found"); }
}
