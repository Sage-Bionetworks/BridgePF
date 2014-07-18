package controllers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.Study;

import play.mvc.Http.Request;

import com.google.common.collect.Maps;

public class StudyControllerService {

    private Map<String,Study> studies = Maps.newHashMap();

    public void setStudies(List<Study> studiesList) {
        for (Study study : studiesList) {
            for (String hostname : study.getHostnames()) {
                studies.put(hostname, study);    
            }
        }
    }

    public Study getStudyByHostname(Request request) {
        String hostname = getHostname(request);
        return studies.get(hostname);
    }

    public Collection<Study> getStudies() {
        return Collections.unmodifiableCollection(studies.values());
    }

    private String getHostname(Request request) {
        // InetAddress.getLocalHost().getHostName() ?
        String host = request.host();
        if (host.indexOf(":") > -1) {
            host = host.split(":")[0];
        }
        return host;
    }

}
