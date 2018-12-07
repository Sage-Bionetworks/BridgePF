package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
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
    ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(StudyIdentifier studyId,
            String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter);
    
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
    void assignExternalId(Account account, String externalIdentifier);
    
    /**
     * Unassign an external ID. This makes the identifier available again and adjusts the account object so it can 
     * be persisted.
     */
    void unassignExternalId(Account account, String externalIdentifier);
    
}
