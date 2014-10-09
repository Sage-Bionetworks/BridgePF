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
    public Study getStudyByKey(String key) {
        for (Study study : studies.values()) {
            if (study.getKey().equals(key)) {
                return study;
            }
        }
        return null;
    }

    public Study getStudyByHostname(String hostname) {
        Study study = studies.get(hostname);
        return (study == null) ? getStudyByKey("teststudy") : study;
    }
    
    public Collection<Study> getStudies() {
        return Collections.unmodifiableCollection(studies.values());
    }

}
