package com.narainsons.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Reducing-balance EMI maths. Pure, so the same numbers show up in the quote, the loan and the schedule. */
public final class LoanCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MC = new MathContext(20);

    public record Quote(BigDecimal principal, int months, BigDecimal annualRatePercent, BigDecimal processingFee,
            BigDecimal netDisbursal, BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayable,
            List<BigDecimal> installments) {
    }

    private LoanCalculator() {
    }

    public static Quote quote(BigDecimal principal, int months, BigDecimal annualRatePercent, BigDecimal feePercent) {
        BigDecimal fee = principal.multiply(feePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        List<BigDecimal> installments = installments(principal, months, annualRatePercent);
        BigDecimal total = installments.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Quote(principal, months, annualRatePercent, fee, principal.subtract(fee), installments.get(0),
                total.subtract(principal), total, installments);
    }

    /** Equal EMIs; the last one clears whatever rounding left over, so the balance ends at exactly zero. */
    public static List<BigDecimal> installments(BigDecimal principal, int months, BigDecimal annualRatePercent) {
        List<BigDecimal> amounts = new ArrayList<>();
        if (annualRatePercent.signum() == 0) {
            BigDecimal emi = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
            for (int i = 1; i < months; i++) {
                amounts.add(emi);
            }
            amounts.add(principal.subtract(emi.multiply(BigDecimal.valueOf(months - 1))));
            return amounts;
        }
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, MC);
        BigDecimal emi = principal.multiply(monthlyRate, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        BigDecimal balance = principal;
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = balance.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = i == months ? balance.add(interest) : emi;
            amounts.add(payment);
            balance = balance.add(interest).subtract(payment);
        }
        return amounts;
    }
}
