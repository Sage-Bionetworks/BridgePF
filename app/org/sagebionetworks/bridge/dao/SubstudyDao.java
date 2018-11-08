package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;

public interface SubstudyDao {
    
    List<Substudy> getSubstudies(StudyIdentifier studyId, boolean includeDeleted);
    
    Substudy getSubstudy(StudyIdentifier studyId, String id);
    
    VersionHolder createSubstudy(Substudy substudy);
    
    VersionHolder updateSubstudy(Substudy substudy);
    
    void deleteSubstudyPermanently(StudyIdentifier studyId, String id);

}
