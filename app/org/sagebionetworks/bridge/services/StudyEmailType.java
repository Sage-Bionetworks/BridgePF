package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.studies.Study;

/** Enumeration of different email types associated with the study. Currently used to verify recipient emails. */
public enum StudyEmailType {
    /**
     * Email address that should receive consent notification emails, when a participant signs or withdraws consent.
     * See {@link Study#getConsentNotificationEmail}.
     */
    CONSENT_NOTIFICATION
}
