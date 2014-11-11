package org.sagebionetworks.bridge.services;

import java.util.Collection;

import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyService {
    public Study getStudyByHostname(String hostname);
    public Collection<Study> getStudies();
    public Study getStudyByKey(String key);
}
