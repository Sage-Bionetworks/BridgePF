package org.sagebionetworks.bridge.services.email;

/** Enumerates the different type of emails. The main purpose of this class is logging and analytics. */
public enum EmailType {
    EMAIL_SIGN_IN,
    RESEND_CONSENT,
    RESET_PASSWORD,
    SIGN_CONSENT,
    UNKNOWN,
    VERIFY_CONSENT_EMAIL,
    VERIFY_EMAIL,
    WITHDRAW_CONSENT,
    APP_INSTALL,
}
