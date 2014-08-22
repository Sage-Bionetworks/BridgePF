package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface StudyConsent {

    /**
     * The study associated with this consent.
     */
    @JsonIgnore
    String getStudyKey();

    /**
     * Timestamp when this consent is created.
     */
    long getCreatedOn();

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
