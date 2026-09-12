package com.primus.core.eval;

import java.util.List;

public record EvalSummary(
        String systemName,
        int totalRuns,
        double diagnosisAccuracyPct,
        double remediationAccuracyPct,
        double resolvedPct,
        List<EvalResult> runs
) {
    public static EvalSummary from(String systemName, List<EvalResult> results) {
        int total = results.size();
        long correctDiagnosis = results.stream().filter(EvalResult::diagnosisCorrect).count();
        long correctRemediation = results.stream().filter(EvalResult::remediationCorrect).count();
        long resolved = results.stream().filter(EvalResult::resolved).count();

        return new EvalSummary(
                systemName,
                total,
                total == 0 ? 0.0 : (correctDiagnosis * 100.0) / total,
                total == 0 ? 0.0 : (correctRemediation * 100.0) / total,
                total == 0 ? 0.0 : (resolved * 100.0) / total,
                results
        );
    }
}