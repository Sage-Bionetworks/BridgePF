package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public interface NotificationRegistrationDao {

    List<NotificationRegistration> listRegistrations(String healthCode);
    
    NotificationRegistration getRegistration(String healthCode, String guid);
    
    GuidHolder createRegistration(String platformARN, String healthCode, NotificationRegistration registration);
    
    GuidHolder updateRegistration(String platformARN, String healthCode, NotificationRegistration registration);
    
    void deleteRegistration(String healthCode, String guid);
    
}
