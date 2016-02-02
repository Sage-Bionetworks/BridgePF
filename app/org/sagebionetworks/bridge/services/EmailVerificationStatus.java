package org.sagebionetworks.bridge.services;

public enum EmailVerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED;
    
    public static final EmailVerificationStatus fromSesVerificationStatus(String status) {
        // From the SES docs, this string can be : "Pending", "Success", "Failed", or "TemporaryFailure". 
        // We also call Pending == Verified and Sending == Pending. Everything else is unverified 
        // and is eligible to trigger the verification workflow again.
        if (status != null) {
            String lower = status.toLowerCase();
            if ("success".equals(lower) || "verified".equals(lower)) {
                return VERIFIED;
            } else if ("sending".equals(lower) || "pending".equals(lower)) {
                return PENDING;
            }
        }
        return UNVERIFIED;
    }
}
