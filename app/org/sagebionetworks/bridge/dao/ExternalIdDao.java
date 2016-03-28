package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.DynamoPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * A DAO for managing external identifiers. For studies utilizing strict validation of external identifiers, they must be 
 * selected from a known list of identifiers, uploaded by study designers. An ID will not be assigned to two different users 
 * or re-assigned to another user if assigned.
 */
public interface ExternalIdDao {

    /**
     * Retrieve external IDs that match the ID and/or assignment filters. These records are returned in pages of pageSize 
     * records. Each page is identified by the offsetKey of the last record of the immediately prior page. If that value is 
     * null, there is not a further page of IDs to retrieve.
     * 
     * More interestingly, you can retrieve the next available ID by asking for pageSize=1, assignmentFilter=FALSE.
     *   
     * @param studyId
     *      study of caller
     * @param offsetKey
     *      if it exists, the key that must be passed to the next call of getExternalIds() to move the cursor forward one 
     *      more page.
     * @param pageSize
     *      the number of records to return
     * @param idFilter
     *      a case-sensitive string that must be found in an external identifier to return it in the results
     * @param assignmentFilter
     *      an optional boolean (can be null). If TRUE, all records returned will be assigned. More usefully, if FALSE,
     *      will return unassigned external identifiers. 
     */
    DynamoPagedResourceList<? extends ExternalIdentifier> getExternalIds(StudyIdentifier studyId, String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter);
    
    /**
     * Add one or more external IDs. Existing IDs are left alone without changing the assignment status of the ID.
     */
    void addExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);
    
    /**
     * Reserve this ID. Reserving the ID prevents it from being taken by another caller for a short duration (30 seconds), 
     * allowing this reserving thread to do other work to set up the user (e.g. creating a Stormpath account, which can 
     * take a long time to finish). If the code is not assigned by calling assignExternalId(...), it will become available 
     * again after the duration expires.   
     */
    void reserveExternalId(StudyIdentifier studyId, String externalIdentifier);
    
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
     * Delete external IDs. This is used by tests to delete test records.
     */
    void deleteExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);
    
}
