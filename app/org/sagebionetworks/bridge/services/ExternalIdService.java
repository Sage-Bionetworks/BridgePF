package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class ExternalIdService {
    
    private interface ExternalIdDao {
        List<String> getExternalIds(StudyIdentifier studyId, String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter);
        void addExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);
        void reserveExternalId(StudyIdentifier studyId, String externalIdentifier);
        void assignExternalId(StudyIdentifier studyId, String externalIdentifier, String healthCode);
        void unassignExternalId(StudyIdentifier studyId, String externalIdentifier);
        void deleteExternalIds(StudyIdentifier studyId, List<String> externalIdentifiers);
    }
    
    private ExternalIdDao dao;
    
    // TODO: Change to DynamoPagedResourceList
    public List<String> getExternalIds(Study study, String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter) {
        checkNotNull(study);
        // you can get IDS regardless of the validation status, and you can add them too. They are not used until the flag is
        // set to enabled.
        return dao.getExternalIds(study.getStudyIdentifier(), offsetKey, pageSize, idFilter, assignmentFilter);
    }
    
    public void addExternalIds(Study study, List<String> externalIdentifiers) {
        checkNotNull(study);
        checkNotNull(externalIdentifiers);
        
        dao.addExternalIds(study.getStudyIdentifier(), externalIdentifiers);
    }
    
    public void reserveExternalId(Study study, String externalIdentifier) {
    }
    
    public void assignExternalId(Study study, String externalIdentifier, String healthCode) {
    }
    
    public void unassignExternalId(Study study, String externalIdentifier) {
    }

    public void deleteExternalIds(Study study, List<String> externalIdentifiers) { 
    }
}
