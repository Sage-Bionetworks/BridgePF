package org.sagebionetworks.bridge.models;

public interface StudyConsent {

    /**
     * The study associated with this consent.
     */
    String getStudyKey();

    /**
     * Timestamp when this consent is created.
     */
    long getTimestamp();

    /**
     * Whether the consent is active.
     */
    boolean getActive();

    /**
     * Where to find the consent document.
     */
    String getPath();

    /**
     * Minimum age required to sign this consent.
     */
    int getMinAge();
}
