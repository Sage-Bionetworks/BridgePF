package org.sagebionetworks.bridge.services;

import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class NotificationsService {
    
    private StudyService studyService;
    
    private NotificationRegistrationDao notificationRegistrationDao;
    
    @Resource
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Resource
    final void setNotificationRegistrationDao(NotificationRegistrationDao notificationRegistrationDao) {
        this.notificationRegistrationDao = notificationRegistrationDao;
    }
    
    public List<NotificationRegistration> listRegistrations(String healthCode) {
        return notificationRegistrationDao.listRegistrations(healthCode);
    }
    
    public NotificationRegistration getRegistration(String healthCode, String guid) {
        return notificationRegistrationDao.getRegistration(healthCode, guid);
    }
    
    public GuidHolder createRegistration(StudyIdentifier studyId, String healthCode, NotificationRegistration registration) {
        Study study = studyService.getStudy(studyId);
        
        String platformARN = study.getPushNotificationARNs().get(registration.getOsName());
        if (StringUtils.isBlank(platformARN)) {
            throw new NotImplementedException("Notifications not enabled for '"+registration.getOsName()+"' platform.");
        }
        
        return notificationRegistrationDao.createRegistration(platformARN, healthCode, registration);
    }
    
    public GuidHolder updateRegistration(StudyIdentifier studyId, String healthCode, NotificationRegistration registration) {
        Study study = studyService.getStudy(studyId);
        
        String platformARN = study.getPushNotificationARNs().get(registration.getOsName());
        if (StringUtils.isBlank(platformARN)) {
            throw new NotImplementedException("Notifications not enabled for '"+registration.getOsName()+"' platform.");
        }
        
        return notificationRegistrationDao.updateRegistration(platformARN, healthCode, registration);
    }
    
    public void deleteRegistration(String healthCode, String guid) {
        notificationRegistrationDao.deleteRegistration(healthCode, guid);
    }
}
