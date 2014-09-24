package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.User;
import org.springframework.context.ApplicationEvent;

public class UserCreatedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 6936099523628754415L;

    public UserCreatedEvent(Object source) {
        super(source);
    }

    public User getUser() {
        return (User)getSource();
    }
    
}
