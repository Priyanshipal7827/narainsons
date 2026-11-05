package com.narainsons.dao;

import com.narainsons.model.User;

final class UserDefaultsLite {

    private UserDefaultsLite() {
    }

    static void apply(User user) {
        if (user.getNationality() == null) {
            user.setNationality("Indian");
        }
        if (user.getCurrentCountry() == null) {
            user.setCurrentCountry("India");
        }
        if (user.getPermanentCountry() == null) {
            user.setPermanentCountry("India");
        }
        if (user.getKycStatus() == null) {
            user.setKycStatus("pending");
        }
        if (user.getBankVerificationStatus() == null) {
            user.setBankVerificationStatus("pending");
        }
        if (user.getAccountStatus() == null) {
            user.setAccountStatus("active");
        }
        if (user.getLoginAttempts() == null) {
            user.setLoginAttempts(0);
        }
    }
}
