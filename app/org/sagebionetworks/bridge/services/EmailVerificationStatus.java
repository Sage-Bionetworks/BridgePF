package org.sagebionetworks.bridge.services;

public enum EmailVerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED;
    
    public static final EmailVerificationStatus fromSesVerificationStatus(String status) {
        // From the docs: "Pending", "Success", "Failed", or "TemporaryFailure".
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
