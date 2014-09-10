package org.sagebionetworks.bridge.services;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.Study;

import com.google.common.collect.Maps;

public class StudyServiceImpl implements StudyService {

    private Map<String,Study> studies = Maps.newHashMap();

    public void setStudies(List<Study> studiesList) {
        for (Study study : studiesList) {
            for (String hostname : study.getHostnames()) {
                studies.put(hostname, study);    
            }
        }
    }

    public Study getStudyByHostname(String hostname) {
        return studies.get(hostname);
    }
    
    public Collection<Study> getStudies() {
        return Collections.unmodifiableCollection(studies.values());
    }

}
