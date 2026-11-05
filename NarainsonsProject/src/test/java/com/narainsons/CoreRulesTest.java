package com.narainsons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.narainsons.config.LoanConfig;
import com.narainsons.service.LoanCalculator;
import com.narainsons.util.ApiException;
import com.narainsons.util.JwtService;
import com.narainsons.util.RateLimiter;
import com.narainsons.util.Validators;

class CoreRulesTest {

    @ParameterizedTest
    @CsvSource({ "10000,3", "50000,24", "15000,7", "10000,1", "25000,12" })
    void installmentsAlwaysAddUpToTheLoanAmount(String amount, int months) {
        List<BigDecimal> emis = LoanCalculator.installments(new BigDecimal(amount), months, BigDecimal.ZERO);
        assertEquals(months, emis.size());
        assertEquals(0, emis.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal(amount)));
    }

    @Test
    void lastInstallmentTakesTheRounding() {
        assertEquals(List.of(new BigDecimal("3333.33"), new BigDecimal("3333.33"), new BigDecimal("3333.34")),
                LoanCalculator.installments(new BigDecimal("10000.00"), 3, BigDecimal.ZERO));
    }

    @ParameterizedTest
    @CsvSource({ "10000,1", "15000,1", "20000,2", "50000,5" })
    void guarantorCount(int amount, int expected) {
        assertEquals(expected, LoanConfig.requiredGuarantorCount(amount));
    }

    @ParameterizedTest
    @ValueSource(strings = { "9876543210", "+91 98765 43210", "09876543210", "919876543210" })
    void mobileFormatsNormalizeToTheSameNumber(String raw) {
        assertEquals("9876543210", Validators.normalizeMobile(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "12345", "123456789012345", " " })
    void badMobilesAreRejected(String raw) {
        assertThrows(IllegalArgumentException.class, () -> Validators.normalizeMobile(raw));
    }

    @Test
    void emailIsLowerCasedAndChecked() {
        assertEquals("a.b@x.in", Validators.normalizeEmail(" A.B@X.in "));
        assertThrows(IllegalArgumentException.class, () -> Validators.normalizeEmail("not-an-email"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,x", "//evil.example/x", "x", "" })
    void unsafeUrlsAreRejected(String url) {
        assertFalse(Validators.isSafeUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://cdn.example.com/a.png", "http://localhost:8082/a.png", "/uploads/a.png" })
    void normalUrlsAreAccepted(String url) {
        assertTrue(Validators.isSafeUrl(url));
    }

    @Test
    void rateLimiterBlocksInsideTheWindowAndResetsAfterIt() {
        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.tryAcquire("k", 2, 1000, 0));
        assertTrue(limiter.tryAcquire("k", 2, 1000, 10));
        assertFalse(limiter.tryAcquire("k", 2, 1000, 20));
        assertTrue(limiter.tryAcquire("other", 2, 1000, 20));
        assertTrue(limiter.tryAcquire("k", 2, 1000, 1000));
    }

    @Test
    void emiFollowsTheReducingBalanceFormula() {
        LoanCalculator.Quote q = LoanCalculator.quote(new BigDecimal("100000"), 12, new BigDecimal("12"), new BigDecimal("2"));
        assertEquals(new BigDecimal("8884.88"), q.emi());
        assertEquals(new BigDecimal("2000.00"), q.processingFee());
        assertEquals(new BigDecimal("98000.00"), q.netDisbursal());
        assertEquals(0, q.totalPayable().compareTo(q.principal().add(q.totalInterest())));
        // The last EMI clears the balance, so it can only differ from the others by rounding.
        BigDecimal last = q.installments().get(11);
        assertTrue(last.subtract(q.emi()).abs().compareTo(new BigDecimal("1")) < 0, last.toString());
    }

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void jwtRoundTripKeepsTheClaims() {
        JwtService jwt = new JwtService(SECRET);
        JwtService.Claims c = jwt.verify(jwt.issue(42, JwtService.SCOPE_USER, 3, 60), JwtService.SCOPE_USER);
        assertEquals(42, c.subjectId());
        assertEquals(3, c.tokenVersion());
    }

    @Test
    void jwtRejectsWrongScopeWrongKeyTamperingAndExpiry() {
        JwtService jwt = new JwtService(SECRET);
        String token = jwt.issue(42, JwtService.SCOPE_USER, 0, 60);
        assertEquals("INVALID_TOKEN", assertThrows(ApiException.class, () -> jwt.verify(token, JwtService.SCOPE_ADMIN)).code());
        JwtService other = new JwtService(SECRET + "x");
        assertEquals("INVALID_TOKEN", assertThrows(ApiException.class, () -> other.verify(token)).code());
        assertEquals("INVALID_TOKEN", assertThrows(ApiException.class, () -> jwt.verify(token + "x")).code());
        String expired = jwt.issue(42, JwtService.SCOPE_USER, 0, -10);
        assertEquals("TOKEN_EXPIRED", assertThrows(ApiException.class, () -> jwt.verify(expired)).code());
    }

    @Test
    void jwtRefusesAWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short"));
    }
}
