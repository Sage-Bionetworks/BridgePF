package org.sagebionetworks.bridge.models.schedules;

import java.util.ArrayList;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public class ScheduleContext {

    private final Study study;
    private final List<User> users;
    
    // Should be an array list because we need a RandomAccess list for shuffling strategies
    public ScheduleContext(Study study, ArrayList<User> users) {
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
