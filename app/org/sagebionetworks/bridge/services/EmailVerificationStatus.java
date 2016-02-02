package org.sagebionetworks.bridge.services;

/**
 * <p>From the SES docs, the status of a verified email address can be the strings "Pending", "Success", 
 * "Failed", or "TemporaryFailure". We map these to three values:</p>
 * <ul>
 *  <li>Unverified - Failed, TemporaryFailure</li>
 *  <li>Pending - Pending</li>
 *  <li>Verified - Success</li>
 * </ul>
 */
public enum EmailVerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED;
    
    public static final EmailVerificationStatus fromSesVerificationStatus(String status) {
        if (status != null) {
            switch(status.toLowerCase()) {
            case "success":
            case "verified":
                return VERIFIED;
            case "pending":
                return PENDING;
            }
        }
        return UNVERIFIED;
    }
}
