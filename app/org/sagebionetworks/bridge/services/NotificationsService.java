package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.SEMICOLON_SPACE_JOINER;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationMessageValidator;
import org.sagebionetworks.bridge.validators.NotificationRegistrationValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Service for managing client registration to receive push notifications, integrated into the 
 * Bridge platform.
 */
@Component
public class NotificationsService {
    private static Logger LOG = LoggerFactory.getLogger(NotificationsService.class);
        
    static final String SMS_TYPE_TRANSACTIONAL = "Transactional";
    /**
     * 11 character label as to who sent the SMS message. Only in some supported countries (not US):
     * https://support.twilio.com/hc/en-us/articles/223133767-International-support-for-Alphanumeric-Sender-ID
     */
    static final String SENDER_ID = "AWS.SNS.SMS.SenderID";
    /**
     * SMS type (Promotional or Transactional).
     */
    static final String SMS_TYPE = "AWS.SNS.SMS.SMSType";
    
    private StudyService studyService;
    
    private NotificationRegistrationDao notificationRegistrationDao;

    private AmazonSNSClient snsClient;

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setNotificationRegistrationDao(NotificationRegistrationDao notificationRegistrationDao) {
        this.notificationRegistrationDao = notificationRegistrationDao;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
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
    
    /**
     * Send a push notification to an individual participant (assuming they have registered for push notification). 
     * This mechanism is intended to message specific individuals, <i>and should not be used to send out notifications 
     * to many accounts.</i> Create a topic, ask your users to subscribe to that topic in your application, and message 
     * them via that topic.
     */
    public void sendNotificationToUser(StudyIdentifier studyId, String healthCode, NotificationMessage message) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(message);
        
        Validate.entityThrowingException(NotificationMessageValidator.INSTANCE, message);
        
        List<NotificationRegistration> registrations = notificationRegistrationDao.listRegistrations(healthCode);
        if (registrations.isEmpty()) {
            throw new BadRequestException("Participant has not registered to receive push notifications.");
        }
        
        List<String> errorMessages = Lists.newArrayListWithCapacity(registrations.size());
        for (NotificationRegistration registration : registrations) {
            String endpointARN = registration.getEndpointARN();
            
            PublishRequest request = new PublishRequest().withTargetArn(endpointARN)
                    .withSubject(message.getSubject()).withMessage(message.getMessage());
            
            try {
                PublishResult result = snsClient.publish(request);
                LOG.debug("Sent message to participant, study=" + studyId.getIdentifier() + ", endpointARN="
                        + endpointARN + ", message ID=" + result.getMessageId());
            } catch(AmazonServiceException e) {
                LOG.warn("Error publishing SNS message to participant", e);
                errorMessages.add(e.getErrorMessage());
            }
        }
        if (!errorMessages.isEmpty()) {
            throw new BadRequestException("Error sending push notification: " + 
                SEMICOLON_SPACE_JOINER.join(errorMessages) + ".");
        }
    }
    
    public void sendSMSMessage(StudyIdentifier studyId, Phone phone, String message) {
        checkNotNull(studyId);
        checkNotNull(phone);
        checkNotNull(message);
        
        // Limited to 140 bytes in GSM. We can test the length in ASCII (GSM is not a supported encoding in the 
        // JDK) and this is a rough approximation as both are 7-bit encodings.
        if (message.getBytes(Charset.forName("US-ASCII")).length > BridgeConstants.SMS_CHARACTER_LIMIT) {
            throw new BridgeServiceException("SMS message cannot be longer than 140 UTF-8/ASCII characters.");
        }
        
        Map<String, MessageAttributeValue> smsAttributes = Maps.newHashMap();
        smsAttributes.put(SENDER_ID, attribute("Bridge"));
        smsAttributes.put(SMS_TYPE, attribute(SMS_TYPE_TRANSACTIONAL));
        // Costs seem too low to worry about this, but if need be, this is how we'd cap it.
        // smsAttributes.put("AWS.SNS.SMS.MaxPrice", attribute("0.50")); max price set to $.50
        
        PublishResult result = snsClient.publish(new PublishRequest()
                .withMessage(message)
                .withPhoneNumber(phone.getNumber())
                .withMessageAttributes(smsAttributes));

        LOG.debug("Sent SMS message, study=" + studyId.getIdentifier() + ", message ID=" + result.getMessageId());
    }

    private MessageAttributeValue attribute(String value) {
        return new MessageAttributeValue().withStringValue(value).withDataType("String");
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
