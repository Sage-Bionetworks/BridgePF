package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

public interface NotificationRegistrationDao {

    List<NotificationRegistration> listRegistrations(String healthCode);
    
    NotificationRegistration getRegistration(String healthCode, String guid);

    /** Create a push notification registration. This involves creating an endpoint ARN for the registration. */
    NotificationRegistration createPushNotificationRegistration(String platformARN, NotificationRegistration registration);

    /** General API for creating a notification registration. Generally used for SMS notifications. */
    NotificationRegistration createRegistration(NotificationRegistration registration);

    NotificationRegistration updateRegistration(NotificationRegistration registration);
    
    void deleteRegistration(String healthCode, String guid);
    
}
