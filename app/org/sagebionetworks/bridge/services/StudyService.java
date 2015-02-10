package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;

public interface StudyService {

    public Tracker getTracker(String trackerId);

    public Study getStudy(String identifier);

    public List<Study> getStudies();

    public Study createStudy(Study study);

    public Study updateStudy(Study study);

    public void deleteStudy(String identifier);

}
