package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.springframework.context.ApplicationEvent;

public class UserUnenrolledEvent extends ApplicationEvent {
    private static final long serialVersionUID = 2877568195689510418L;
    
    private Study study;
    
    public UserUnenrolledEvent(Object source, Study study) {
        super(source);
        this.study = study;
    }
    
    public User getUser() {
        return (User)getSource();
    }
    
    public Study getStudy() {
        return study;
    }

}
