package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public class ScheduleContext {

    private final Study study;
    private final List<User> users;
    
    public ScheduleContext(Study study, List<User> users) {
        this.study = study;
        this.users = users;
    }
    
    public Study getStudy() {
        return study;
    }
    public List<User> getUsers() {
        return users;
    }
    
}
