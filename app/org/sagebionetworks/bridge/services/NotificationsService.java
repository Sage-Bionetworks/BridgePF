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
    
    public List<NotificationRegistration> listRegistrations(String healthCode) {
        checkNotNull(healthCode);
        
        return notificationRegistrationDao.listRegistrations(healthCode);
    }
    
    public NotificationRegistration getRegistration(String healthCode, String guid) {
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        return notificationRegistrationDao.getRegistration(healthCode, guid);
    }
    
    public NotificationRegistration createRegistration(StudyIdentifier studyId, NotificationRegistration registration) {
        checkNotNull(studyId);
        checkNotNull(registration);
        
        adjustToCanonicalOsNameIfNeeded(registration);
        Study study = studyService.getStudy(studyId);
        String platformARN = getPlatformARN(study, registration);
        
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
        
        return notificationRegistrationDao.createRegistration(platformARN, registration);
    }
    
    public NotificationRegistration updateRegistration(StudyIdentifier studyId, NotificationRegistration registration) {
        checkNotNull(studyId);
        checkNotNull(registration);
        
        adjustToCanonicalOsNameIfNeeded(registration);
        
        Validate.entityThrowingException(NotificationRegistrationValidator.INSTANCE, registration);
        
        return notificationRegistrationDao.updateRegistration(registration);
    }
    
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
        String resolvedOsName = OperatingSystem.PLATFORM_SYNONYMS.get(registration.getOsName());
        if (resolvedOsName != null) {
            registration.setOsName(resolvedOsName);
        }
    }
}
