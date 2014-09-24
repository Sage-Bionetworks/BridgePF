package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.User;
import org.springframework.context.ApplicationEvent;

public class UserDeletedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 9006116733889260138L;

    public UserDeletedEvent(Object source) {
        super(source);
    }

    public User getUser() {
        return (User)getSource();
    }
    
}
