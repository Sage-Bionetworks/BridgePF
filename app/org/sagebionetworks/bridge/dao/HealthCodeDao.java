package org.sagebionetworks.bridge.dao;

public interface HealthCodeDao {
    /**
     * Return the ID of the study associated with this health code; or null if 
     * the health code does not exist. This DAO exists for legacy uploads that 
     * do not have a studyId associated with them.
     */
    String getStudyIdentifier(String healthCode);
}
