package com.narainsons.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LoanApplication {

    private Long id;
    private Long userId;
    private String applicationCode;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private String purpose;
    private String purposeDetail;
    private BigDecimal monthlyIncome;
    private String employmentType;
    private String companyName;
    private String bankName;
    private String ifscCode;
    private String bankAccountLast4;
    private java.math.BigDecimal interestRateAnnual;
    private java.math.BigDecimal processingFee;
    private java.math.BigDecimal totalPayable;
    private java.math.BigDecimal emiAmount;
    private LocalDateTime disbursedAt;
    private String disbursementReference;
    private LocalDateTime closedAt;
    private String status;
    private String statusMessage;
    private String adminNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public java.math.BigDecimal getInterestRateAnnual() {
        return interestRateAnnual;
    }

    public void setInterestRateAnnual(java.math.BigDecimal interestRateAnnual) {
        this.interestRateAnnual = interestRateAnnual;
    }

    public java.math.BigDecimal getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(java.math.BigDecimal processingFee) {
        this.processingFee = processingFee;
    }

    public java.math.BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public void setTotalPayable(java.math.BigDecimal totalPayable) {
        this.totalPayable = totalPayable;
    }

    public java.math.BigDecimal getEmiAmount() {
        return emiAmount;
    }

    public void setEmiAmount(java.math.BigDecimal emiAmount) {
        this.emiAmount = emiAmount;
    }

    public LocalDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(LocalDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public String getDisbursementReference() {
        return disbursementReference;
    }

    public void setDisbursementReference(String disbursementReference) {
        this.disbursementReference = disbursementReference;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode) {
        this.applicationCode = applicationCode;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getPurposeDetail() {
        return purposeDetail;
    }

    public void setPurposeDetail(String purposeDetail) {
        this.purposeDetail = purposeDetail;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
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

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public String getBankAccountLast4() {
        return bankAccountLast4;
    }

    public void setBankAccountLast4(String bankAccountLast4) {
        this.bankAccountLast4 = bankAccountLast4;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
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
}
