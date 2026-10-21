package com.pgds.ai;

import com.pgds.ai.AnomalyDetectionService.Scored;
import com.pgds.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Measures the fraud detectors against ground truth. The demo generator labels the anomalies it injects with receipt
 * numbers starting "RCP-DEMO-ANOM-"; everything else is treated as normal.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyEvaluationService {

    static final String LABEL_PREFIX = "RCP-DEMO-ANOM-";

    public record Metrics(String detector, double threshold, int flagged, int truePositives, int falsePositives,
                          int falseNegatives, double precision, double recall, double f1) {}

    public record Evaluation(int rowsEvaluated, int labelledAnomalies, boolean isolationForestTrained,
                             List<Metrics> isolationForest, Metrics rules, Metrics combined) {}

    private final AnomalyDetectionService detection;

    public Evaluation evaluate() {
        List<Scored> rows = detection.scoreWindow();
        int labelled = (int) rows.stream().filter(AnomalyEvaluationService::isInjected).count();
        if (labelled == 0)
            throw new ApiException(HttpStatus.CONFLICT, "No labelled demo anomalies in the training window. Generate demo history first.");

        boolean trained = rows.get(0).score() != null;
        List<Metrics> iso = new ArrayList<>();
        if (trained)
            for (double t : new double[]{0.50, 0.55, 0.60, 0.65, 0.70, 0.75})
                iso.add(metrics("ISOLATION_FOREST", t, rows, labelled, r -> r.score() >= t));

        double th = detection.threshold();
        Metrics rules = metrics("RULES", 0, rows, labelled, r -> AnomalyDetectionService.breaksRule(r.features()));
        Metrics combined = metrics("RULES+ISOLATION_FOREST", th, rows, labelled,
                r -> AnomalyDetectionService.breaksRule(r.features()) || (r.score() != null && r.score() >= th));
        return new Evaluation(rows.size(), labelled, trained, iso, rules, combined);
    }

    private static boolean isInjected(Scored r) { return r.dist().getReceiptNo().startsWith(LABEL_PREFIX); }

    private static Metrics metrics(String name, double threshold, List<Scored> rows, int labelled, Predicate<Scored> flag) {
        int flagged = 0, tp = 0;
        for (Scored r : rows) {
            if (!flag.test(r)) continue;
            flagged++;
            if (isInjected(r)) tp++;
        }
        int fp = flagged - tp, fn = labelled - tp;
        double precision = flagged == 0 ? 0 : (double) tp / flagged;
        double recall = labelled == 0 ? 0 : (double) tp / labelled;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Metrics(name, threshold, flagged, tp, fp, fn, round(precision), round(recall), round(f1));
    }

    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
