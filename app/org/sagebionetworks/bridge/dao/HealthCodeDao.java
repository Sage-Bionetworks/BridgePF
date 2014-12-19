package org.sagebionetworks.bridge.dao;

public interface HealthCodeDao {

    /**
     * @return true, if the code does not exist yet and is set; false, if the code already exists.
     */
    boolean setIfNotExist(String code, String studyId);

    /**
     * @return The ID of the study associated with this health code; or null if the health code does not exist.
     */
    String getStudyIdentifier(String code);

    /**
     * For backfilling study ID.
     */
    void setStudyId(String code, String studyId);
}
