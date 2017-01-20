package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public interface NotificationRegistrationDao {

    List<NotificationRegistration> listRegistrations(String healthCode);
    
    NotificationRegistration getRegistration(String healthCode, String guid);
    
    NotificationRegistration createRegistration(String platformARN, NotificationRegistration registration);
    
    NotificationRegistration updateRegistration(NotificationRegistration registration);
    
    void deleteRegistration(String healthCode, String guid);
    
}
