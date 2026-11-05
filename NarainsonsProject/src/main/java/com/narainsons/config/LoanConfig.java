package com.narainsons.config;

import java.util.List;

public final class LoanConfig {

    public static final int MAX_AMOUNT = 50_000;
    public static final int MIN_AMOUNT = 10_000;
    public static final int AMOUNT_STEP = 5_000;
    public static final int MIN_TENURE_MONTHS = 3;
    public static final int MAX_TENURE_MONTHS = 24;

    /** Har ₹10,000 par ek guarantor — 10k→1, 20k→2, … 50k→5 */
    public static final int GUARANTOR_PER_AMOUNT = 10_000;

    public static final String PENDING_MESSAGE =
            "Aapka loan application pending hai. Admin jald approve karega — shortly update milega.";

    private LoanConfig() {
    }

    public static List<Integer> allowedAmounts() {
        return java.util.stream.IntStream.iterate(MIN_AMOUNT, a -> a <= MAX_AMOUNT, a -> a + AMOUNT_STEP)
                .boxed()
                .toList();
    }

    /** ₹10,000 → 1, ₹20,000 → 2, … ₹50,000 → 5 (amount / 10,000). */
    public static int requiredGuarantorCount(int amountRupee) {
        if (amountRupee < MIN_AMOUNT) {
            return 0;
        }
        int count = amountRupee / GUARANTOR_PER_AMOUNT;
        return Math.max(1, count);
    }
}
