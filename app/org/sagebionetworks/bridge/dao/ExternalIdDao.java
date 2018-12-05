package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * A DAO for managing external identifiers. For studies utilizing strict validation of external identifiers, they must be 
 * selected from a known list of identifiers, uploaded by study designers. An ID will not be assigned to two different users 
 * or re-assigned to another user if assigned.
 */
public interface ExternalIdDao {

    String EXTERNAL_ID_GET_RATE = "external.id.get.rate";
    String CONFIG_KEY_ADD_LIMIT = "external.id.add.limit";
    String CONFIG_KEY_LOCK_DURATION = "external.id.lock.duration";

    /**
     * Get a single external ID record. Returns null if there is no record or it doesn't match the caller's
     * substudy membership.
     */
    ExternalIdentifier getExternalId(StudyIdentifier studyId, String externalId);

    /**
     * Get a forward-only cursor page of results, filtered for the caller's substudy memberships, and optionally
     * by the start of the identifier or its assignment status (assigned or not). 
     */
    ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIdentifiers(StudyIdentifier studyId,
            Set<String> callerSubstudies, String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter);
    
    /**
     * Create a new external identifier.
     */
    void createExternalIdentifier(ExternalIdentifier externalIdentifier);
    
    /**
     * Delete an external identifier.
     */
    void deleteExternalIdentifier(ExternalIdentifier externalIdentifier);
    
    /**
     * Assign an external identifier. Once assigned, it cannot be re-assigned. If already assigned to this health code, 
     * nothing happens.  
     */
    void assignExternalId(StudyIdentifier studyId, String externalIdentifier, String healthCode);
    
    /**
     * Unassign an external ID. This makes the identifier available again. This method should be called when a user
     * is deleted, usually during testing.
     */
    void unassignExternalId(StudyIdentifier studyId, String externalIdentifier);
    
    /**
     * Retrieve external IDs that match the ID and/or assignment filters. These records are returned in pages of pageSize 
     * records. Each page is identified by the offsetKey of the last record of the immediately prior page. If that value is 
     * null, there is not a further page of IDs to retrieve.
     * 
     * More interestingly, you can retrieve the next available ID by asking for pageSize=1, assignmentFilter=FALSE.
     */
    @Deprecated
    ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(StudyIdentifier studyId, String offsetKey,
            int pageSize, String idFilter, Boolean assignmentFilter);
    
    /**
     * Add one or more external IDs. Existing IDs are left alone without changing the assignment status of the ID.
     */
    @Deprecated
    void addExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);

    /**
     * Delete external IDs. This is used by tests to delete test records.
     */
    @Deprecated
    void deleteExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);
    
}
