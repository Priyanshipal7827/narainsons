package com.narainsons.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {

    private Long id;
    private String customerCode;
    private String name;
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private String alternateMobileNumber;
    private String email;
    private String gender;
    private LocalDate dateOfBirth;
    private Integer age;
    private String maritalStatus;
    private String nationality;
    private String profileImage;
    private String aadhaarEncrypted;
    private String aadhaarLast4;
    private String panEncrypted;
    private String panLast4;
    private String aadhaarDocument;
    private String panDocument;
    private String kycStatus;
    private LocalDateTime kycVerifiedAt;
    private String kycRejectionReason;
    private String currentAddress;
    private String currentCity;
    private String currentState;
    private String currentPincode;
    private String currentCountry;
    private String permanentAddress;
    private String permanentCity;
    private String permanentState;
    private String permanentPincode;
    private String permanentCountry;
    private Boolean sameAsCurrentAddress;
    private String employmentType;
    private String companyName;
    private String designation;
    private Integer workExperienceMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal annualIncome;
    private String incomeSource;
    private String bankName;
    private String bankAccountEncrypted;
    private String bankAccountLast4;
    private String ifscCode;
    private String bankVerificationStatus;
    private LocalDateTime bankVerifiedAt;
    private String passwordHash;
    private String otpHash;
    private LocalDateTime otpExpiresAt;
    private LocalDateTime otpVerifiedAt;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime mobileVerifiedAt;
    private Integer loginAttempts;
    private transient Integer tokenVersion;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLogin;
    private LocalDateTime lastLogoutAt;
    private String accountStatus;
    private String fcmToken;
    private LocalDateTime fcmTokenUpdatedAt;
    private Boolean notificationEnable;
    private LocalDateTime notificationDisableAt;
    private String deviceId;
    private String deviceType;
    private String deviceName;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String timeZone;
    private Boolean isLogin;
    private Boolean isAppInstalled;
    private LocalDateTime applicationInstallTime;
    private LocalDateTime applicationUninstallTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean privacyConsent;
    private LocalDateTime privacyConsentAt;
    private Boolean termsAccepted;
    private LocalDateTime termsAcceptedAt;
    private Boolean kycConsent;
    private LocalDateTime kycConsentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /** JSON input only — DB mein save nahi hota */
    private transient String password;

    private Integer onboardingStep;
    private String sessionToken;
    private String contactsData;
    private String galleryData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getAlternateMobileNumber() {
        return alternateMobileNumber;
    }

    public void setAlternateMobileNumber(String alternateMobileNumber) {
        this.alternateMobileNumber = alternateMobileNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getAadhaarEncrypted() {
        return aadhaarEncrypted;
    }

    public void setAadhaarEncrypted(String aadhaarEncrypted) {
        this.aadhaarEncrypted = aadhaarEncrypted;
    }

    public String getAadhaarLast4() {
        return aadhaarLast4;
    }

    public void setAadhaarLast4(String aadhaarLast4) {
        this.aadhaarLast4 = aadhaarLast4;
    }

    public String getPanEncrypted() {
        return panEncrypted;
    }

    public void setPanEncrypted(String panEncrypted) {
        this.panEncrypted = panEncrypted;
    }

    public String getPanLast4() {
        return panLast4;
    }

    public void setPanLast4(String panLast4) {
        this.panLast4 = panLast4;
    }

    public String getAadhaarDocument() {
        return aadhaarDocument;
    }

    public void setAadhaarDocument(String aadhaarDocument) {
        this.aadhaarDocument = aadhaarDocument;
    }

    public String getPanDocument() {
        return panDocument;
    }

    public void setPanDocument(String panDocument) {
        this.panDocument = panDocument;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public LocalDateTime getKycVerifiedAt() {
        return kycVerifiedAt;
    }

    public void setKycVerifiedAt(LocalDateTime kycVerifiedAt) {
        this.kycVerifiedAt = kycVerifiedAt;
    }

    public String getKycRejectionReason() {
        return kycRejectionReason;
    }

    public void setKycRejectionReason(String kycRejectionReason) {
        this.kycRejectionReason = kycRejectionReason;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress) {
        this.currentAddress = currentAddress;
    }

    public String getCurrentCity() {
        return currentCity;
    }

    public void setCurrentCity(String currentCity) {
        this.currentCity = currentCity;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public String getCurrentPincode() {
        return currentPincode;
    }

    public void setCurrentPincode(String currentPincode) {
        this.currentPincode = currentPincode;
    }

    public String getCurrentCountry() {
        return currentCountry;
    }

    public void setCurrentCountry(String currentCountry) {
        this.currentCountry = currentCountry;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    public String getPermanentCity() {
        return permanentCity;
    }

    public void setPermanentCity(String permanentCity) {
        this.permanentCity = permanentCity;
    }

    public String getPermanentState() {
        return permanentState;
    }

    public void setPermanentState(String permanentState) {
        this.permanentState = permanentState;
    }

    public String getPermanentPincode() {
        return permanentPincode;
    }

    public void setPermanentPincode(String permanentPincode) {
        this.permanentPincode = permanentPincode;
    }

    public String getPermanentCountry() {
        return permanentCountry;
    }

    public void setPermanentCountry(String permanentCountry) {
        this.permanentCountry = permanentCountry;
    }

    public Boolean getSameAsCurrentAddress() {
        return sameAsCurrentAddress;
    }

    public void setSameAsCurrentAddress(Boolean sameAsCurrentAddress) {
        this.sameAsCurrentAddress = sameAsCurrentAddress;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Integer getWorkExperienceMonths() {
        return workExperienceMonths;
    }

    public void setWorkExperienceMonths(Integer workExperienceMonths) {
        this.workExperienceMonths = workExperienceMonths;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public BigDecimal getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(BigDecimal annualIncome) {
        this.annualIncome = annualIncome;
    }

    public String getIncomeSource() {
        return incomeSource;
    }

    public void setIncomeSource(String incomeSource) {
        this.incomeSource = incomeSource;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccountEncrypted() {
        return bankAccountEncrypted;
    }

    public void setBankAccountEncrypted(String bankAccountEncrypted) {
        this.bankAccountEncrypted = bankAccountEncrypted;
    }

    public String getBankAccountLast4() {
        return bankAccountLast4;
    }

    public void setBankAccountLast4(String bankAccountLast4) {
        this.bankAccountLast4 = bankAccountLast4;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public String getBankVerificationStatus() {
        return bankVerificationStatus;
    }

    public void setBankVerificationStatus(String bankVerificationStatus) {
        this.bankVerificationStatus = bankVerificationStatus;
    }

    public LocalDateTime getBankVerifiedAt() {
        return bankVerifiedAt;
    }

    public void setBankVerifiedAt(LocalDateTime bankVerifiedAt) {
        this.bankVerifiedAt = bankVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public LocalDateTime getOtpVerifiedAt() {
        return otpVerifiedAt;
    }

    public void setOtpVerifiedAt(LocalDateTime otpVerifiedAt) {
        this.otpVerifiedAt = otpVerifiedAt;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(LocalDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public LocalDateTime getMobileVerifiedAt() {
        return mobileVerifiedAt;
    }

    public void setMobileVerifiedAt(LocalDateTime mobileVerifiedAt) {
        this.mobileVerifiedAt = mobileVerifiedAt;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public LocalDateTime getLastLogoutAt() {
        return lastLogoutAt;
    }

    public void setLastLogoutAt(LocalDateTime lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public LocalDateTime getFcmTokenUpdatedAt() {
        return fcmTokenUpdatedAt;
    }

    public void setFcmTokenUpdatedAt(LocalDateTime fcmTokenUpdatedAt) {
        this.fcmTokenUpdatedAt = fcmTokenUpdatedAt;
    }

    public Boolean getNotificationEnable() {
        return notificationEnable;
    }

    public void setNotificationEnable(Boolean notificationEnable) {
        this.notificationEnable = notificationEnable;
    }

    public LocalDateTime getNotificationDisableAt() {
        return notificationDisableAt;
    }

    public void setNotificationDisableAt(LocalDateTime notificationDisableAt) {
        this.notificationDisableAt = notificationDisableAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Boolean getIsLogin() {
        return isLogin;
    }

    public void setIsLogin(Boolean isLogin) {
        this.isLogin = isLogin;
    }

    public Boolean getIsAppInstalled() {
        return isAppInstalled;
    }

    public void setIsAppInstalled(Boolean isAppInstalled) {
        this.isAppInstalled = isAppInstalled;
    }

    public LocalDateTime getApplicationInstallTime() {
        return applicationInstallTime;
    }

    public void setApplicationInstallTime(LocalDateTime applicationInstallTime) {
        this.applicationInstallTime = applicationInstallTime;
    }

    public LocalDateTime getApplicationUninstallTime() {
        return applicationUninstallTime;
    }

    public void setApplicationUninstallTime(LocalDateTime applicationUninstallTime) {
        this.applicationUninstallTime = applicationUninstallTime;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Boolean getPrivacyConsent() {
        return privacyConsent;
    }

    public void setPrivacyConsent(Boolean privacyConsent) {
        this.privacyConsent = privacyConsent;
    }

    public LocalDateTime getPrivacyConsentAt() {
        return privacyConsentAt;
    }

    public void setPrivacyConsentAt(LocalDateTime privacyConsentAt) {
        this.privacyConsentAt = privacyConsentAt;
    }

    public Boolean getTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(Boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public Boolean getKycConsent() {
        return kycConsent;
    }

    public void setKycConsent(Boolean kycConsent) {
        this.kycConsent = kycConsent;
    }

    public LocalDateTime getKycConsentAt() {
        return kycConsentAt;
    }

    public void setKycConsentAt(LocalDateTime kycConsentAt) {
        this.kycConsentAt = kycConsentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getOnboardingStep() {
        return onboardingStep;
    }

    public void setOnboardingStep(Integer onboardingStep) {
        this.onboardingStep = onboardingStep;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getContactsData() {
        return contactsData;
    }

    public void setContactsData(String contactsData) {
        this.contactsData = contactsData;
    }

    public String getGalleryData() {
        return galleryData;
    }

    public void setGalleryData(String galleryData) {
        this.galleryData = galleryData;
    }
}
