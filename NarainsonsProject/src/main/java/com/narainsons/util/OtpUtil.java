package com.narainsons.util;

import java.security.SecureRandom;

public final class OtpUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpUtil() {
    }

    public static String generateSixDigitOtp() {
        int value = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(value);
    }

    public static String hashOtp(String otp) {
        return PasswordUtil.hash(otp);
    }

    public static boolean verifyOtp(String plainOtp, String otpHash) {
        return PasswordUtil.matches(plainOtp, otpHash);
    }
}
