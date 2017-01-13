package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationRegistrationValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Service for managing client registration to receive push notifications, integrated into the 
 * Bridge platform.
 */
@Component
public class NotificationsService {
    
    private StudyService studyService;
    
    private NotificationRegistrationDao notificationRegistrationDao;
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setNotificationRegistrationDao(NotificationRegistrationDao notificationRegistrationDao) {
        this.notificationRegistrationDao = notificationRegistrationDao;
    }
    
    /**
     * Return all the registrations for this user. There may be more than one, if a user installs 
     * the application on different devices. It is possible there may be multiple registrations on 
     * one device, but we attempt to prevent this. 
     */
    public List<NotificationRegistration> listRegistrations(String healthCode) {
        checkNotNull(healthCode);
        
        return notificationRegistrationDao.listRegistrations(healthCode);
    }
    
    /**
     * Get a single push notification registration by its GUID. This request is scoped to the user through 
     * a healthCode.
     */
    public NotificationRegistration getRegistration(String healthCode, String guid) {
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        return notificationRegistrationDao.getRegistration(healthCode, guid);
    }
    
    /**
     * Create a new registration. The client will retrieve an identifying token (called different things and 
     * in a different format on different platforms), and register it with Bridge. Bridge will return a 
     * Bridge-specific GUID to track this registration to retrieve notifications, which the client can 
     * delete at a later time. If the token already exists in an existing registration record, then that 
     * registration record will be returned in lieu of creating a redundant record.
     */
    public NotificationRegistration createRegistration(StudyIdentifier studyId, NotificationRegistration registration) {
        checkNotNull(studyId);
        checkNotNull(registration);
        
        adjustToCanonicalOsNameIfNeeded(registration);
        Study study = studyService.getStudy(studyId);
        String platformARN = getPlatformARN(study, registration);
        
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
        
        return notificationRegistrationDao.createRegistration(platformARN, registration);
    }
    
    /**
     * Update an existing device registration with a new token that has been assigned by the client operating 
     * system. At least on iOS, the device token can change over the lifetime of the app, and it is considered 
     * best practice to re-send this token to the server on every start-up of the app. The registration record 
     * that is returned should always have the GUID that was used to submit the update.
     */
    public NotificationRegistration updateRegistration(StudyIdentifier studyId, NotificationRegistration registration) {
        checkNotNull(studyId);
        checkNotNull(registration);
        
        adjustToCanonicalOsNameIfNeeded(registration);
        
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
        
        return notificationRegistrationDao.updateRegistration(registration);
    }
    
    /**
     * Delete a registration record. User can no longer be sent push notifications by the server.
     */
    public void deleteRegistration(String healthCode, String guid) {
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        notificationRegistrationDao.deleteRegistration(healthCode, guid);
    }

    private String getPlatformARN(Study study, NotificationRegistration registration) {
        String platformARN = study.getPushNotificationARNs().get(registration.getOsName());
        if (StringUtils.isBlank(platformARN)) {
            throw new NotImplementedException("Notifications not enabled for '"+registration.getOsName()+"' platform.");
        }
        return platformARN;
    }

    /**
     * If the client is using a known OS name synonym, use the canonical value for the OS name in 
     * the registration object.
     */
    private void adjustToCanonicalOsNameIfNeeded(NotificationRegistration registration) {
        String resolvedOsName = OperatingSystem.SYNONYMS.get(registration.getOsName());
        if (resolvedOsName != null) {
            registration.setOsName(resolvedOsName);
        }
    }
}
