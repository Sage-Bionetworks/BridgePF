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
    void createExternalId(ExternalIdentifier externalIdentifier);
    
    /**
     * Delete an external identifier.
     */
    void deleteExternalId(ExternalIdentifier externalIdentifier);
    
    /**
     * Complete the external ID assignment. This operation has to be orchestrated with the persistence of an 
     * account, and is similar to a create operation except that DynamoDB will enforce a check that the 
     * externalIdentifier has not been assigned a health code.
     */
    void commitAssignExternalId(ExternalIdentifier externalId);
    
    /**
     * Unassign an external ID. This makes the identifier available again and adjusts the account object so it can 
     * be persisted. Calling this method when the external identifier is not assigned to the account, but the 
     * account has not been correctly updated, will adjust the account so it is correct and can be persisted. It is
     * therefore safest to always update the account after calling this method.
     */
    void unassignExternalId(Account account, String externalIdentifier);
    
}
