package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyCohort;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface StudyCohortDao {

    public StudyCohort createStudyCohort(StudyCohort studyCohort);
    
    public List<StudyCohort> getStudyCohorts(StudyIdentifier studyId, boolean createDefault);
    
    public StudyCohort getStudyCohort(StudyIdentifier studyId, String guid);
    
    public StudyCohort getStudyCohortForUser(ScheduleContext context);
    
    public StudyCohort updateStudyCohort(StudyCohort studyCohort);

    public void deleteStudyCohort(StudyIdentifier studyId, String guid);
    
    // public void deleteStudyCohorts(StudyIdentifier studyId);
    
}
