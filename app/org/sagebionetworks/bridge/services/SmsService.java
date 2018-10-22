package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.Charset;

import javax.annotation.Resource;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.dao.SmsOptOutSettingsDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.sms.TwilioHelper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.SmsMessageValidator;
import org.sagebionetworks.bridge.validators.SmsOptOutSettingsValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service for handling SMS metadata (opt-outs, message logging) and for handling webhooks for receiving SMS. */
@Component
public class SmsService {
    private static final Logger LOG = LoggerFactory.getLogger(SmsService.class);

    private static final String OPT_OUT_TEXT = "Text STOP to unsubscribe.";

    // mPower 2.0 study burst notifications can be fairly long. The longest one has 230 chars of fixed content, an app
    // url that's 53 characters long, and some freeform text that can be potentially 255 characters long, for a total
    // of 538 characters. Round to a nice round 600 characters (about 4.5 SMS messages, if broken up).
    private static final int SMS_CHARACTER_LIMIT = 600;

    private SmsMessageDao messageDao;
    private SmsOptOutSettingsDao optOutSettingsDao;
    private ParticipantService participantService;
    private AmazonSNSClient snsClient;
    private StudyService studyService;
    private TwilioHelper twilioHelper;

    /** Message DAO, for writing to and reading from the SMS message log. */
    @Autowired
    public final void setMessageDao(SmsMessageDao messageDao) {
        this.messageDao = messageDao;
    }

    /** Opt-out settings dao, for getting and updating the participant's SMS opt-out settings. */
    @Autowired
    public final void setOptOutSettingsDao(SmsOptOutSettingsDao optOutSettingsDao) {
        this.optOutSettingsDao = optOutSettingsDao;
    }

    /** Participant service, used to get a phone number for an account. */
    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /** SNS client, to send SMS through AWS. */
    @Resource(name = "snsClient")
    public final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }

    /** Study service, to get basic study attributes like short name. */
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    /** Twilio helper, used for sending SMS through Twilio. */
    @Autowired
    public final void setTwilioHelper(TwilioHelper twilioHelper) {
        this.twilioHelper = twilioHelper;
    }

    /** Sends an SMS message using the given message provider. */
    public void sendSmsMessage(SmsMessageProvider provider) {
        checkNotNull(provider);
        StudyIdentifier studyId = provider.getStudy().getStudyIdentifier();
        Phone recipientPhone = provider.getPhone();
        String message = provider.getFormattedMessage();

        // Check max SMS length.
        if (message.getBytes(Charset.forName("US-ASCII")).length > SMS_CHARACTER_LIMIT) {
            throw new BridgeServiceException("SMS message cannot be longer than 600 UTF-8/ASCII characters.");
        }

        // Check SMS opt-out.
        SmsOptOutSettings smsOptOutSettings = getOptOutSettings(recipientPhone.getNumber());
        if (smsOptOutSettings != null) {
            switch (provider.getSmsTypeEnum()) {
                case PROMOTIONAL:
                    if (smsOptOutSettings.getPromotionalOptOutForStudy(studyId.getIdentifier())) {
                        return;
                    }
                    break;
                case TRANSACTIONAL:
                    if (smsOptOutSettings.getTransactionalOptOutForStudy(studyId.getIdentifier())) {
                        return;
                    }
                    break;
                default:
                    LOG.error("Unexpected SMS type " + provider.getSmsType());
                    break;
            }
        }

        // Send SMS.
        String messageId;
        Study study = studyService.getStudy(studyId);
        switch (study.getSmsServiceProvider()) {
            case AWS:
                PublishResult result = snsClient.publish(provider.getSmsRequest());
                messageId = result.getMessageId();
                break;
            case TWILIO:
                message = formatMessageForTwilio(study, message);
                messageId = sendSmsViaTwilio(recipientPhone, message);
                break;
            default:
                throw new BridgeServiceException("Unexpected SMS service provider " + study.getSmsServiceProvider() +
                        " for study " + studyId.getIdentifier());
        }

        // Log SMS message.
        SmsMessage smsMessage = SmsMessage.create();
        smsMessage.setPhoneNumber(recipientPhone.getNumber());
        smsMessage.setSentOn(DateUtils.getCurrentMillisFromEpoch());
        smsMessage.setMessageBody(message);
        smsMessage.setMessageId(messageId);
        smsMessage.setSmsType(provider.getSmsTypeEnum());
        smsMessage.setStudyId(studyId.getIdentifier());
        logMessage(smsMessage);

        LOG.debug("Sent SMS message, study=" + studyId.getIdentifier() + ", message ID=" + messageId);
    }

    private String formatMessageForTwilio(Study study, String message) {
        // Message must include study short name.
        String studyShortName = study.getShortName();
        if (!message.contains(studyShortName)) {
            message = studyShortName + ": " + message;
        }

        // Message must contain "Text STOP to unsubscribe."
        if (!message.contains(OPT_OUT_TEXT)) {
            message = message + " " + OPT_OUT_TEXT;
        }

        return message;
    }

    private String sendSmsViaTwilio(Phone recipientPhone, String message) {
        // We currently only support sending to US numbers.
        if (!BridgeConstants.PHONE_REGION_US.equals(recipientPhone.getRegionCode())) {
            throw new BadRequestException("SMS is not supported for non-US phone numbers");
        }

        // Send message.
        return twilioHelper.sendSms(recipientPhone, message);
    }

    /** Gets the message we most recently sent to the given phone number. */
    public SmsMessage getMostRecentMessage(String number) {
        if (StringUtils.isBlank(number)) {
            throw new BadRequestException("number is required");
        }
        return messageDao.getMostRecentMessage(number);
    }

    /** Gets the message we most recently sent to the phone number associated with the given account. */
    public SmsMessage getMostRecentMessage(Study study, String userId) {
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        if (participant.getPhone() == null) {
            throw new BadRequestException("participant has no phone number");
        }

        return getMostRecentMessage(participant.getPhone().getNumber());
    }

    /** Saves an SMS message that we sent to the SMS message log. */
    public void logMessage(SmsMessage message) {
        if (message == null) {
            throw new BadRequestException("message is required");
        }
        Validate.entityThrowingException(SmsMessageValidator.INSTANCE, message);
        messageDao.logMessage(message);
    }

    /** Gets the SMS opt-out settings for the given number. */
    public SmsOptOutSettings getOptOutSettings(String number) {
        if (StringUtils.isBlank(number)) {
            throw new BadRequestException("number is required");
        }
        return optOutSettingsDao.getOptOutSettings(number);
    }

    /** Gets the SMS opt-out settings for the phone number associated with the given account. */
    public SmsOptOutSettings getOptOutSettings(Study study, String userId) {
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        if (participant.getPhone() == null) {
            throw new BadRequestException("participant has no phone number");
        }

        return getOptOutSettings(participant.getPhone().getNumber());
    }

    /** Saves the given SMS opt-out settings. */
    public void setOptOutSettings(SmsOptOutSettings optOutSettings) {
        if (optOutSettings == null) {
            throw new BadRequestException("optOutSettings is required");
        }
        Validate.entityThrowingException(SmsOptOutSettingsValidator.INSTANCE, optOutSettings);
        optOutSettingsDao.setOptOutSettings(optOutSettings);
    }

    /** Saves the SMS opt-out settings for the phoen number associated with the given account. */
    public void setOptOutSettings(Study study, String userId, SmsOptOutSettings optOutSettings) {
        // Get user.
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        if (participant.getPhone() == null) {
            throw new BadRequestException("participant has no phone number");
        }

        // Ensure the phone number of the settings matches the user's phone number.
        optOutSettings.setPhoneNumber(participant.getPhone().getNumber());

        setOptOutSettings(optOutSettings);
    }
}
