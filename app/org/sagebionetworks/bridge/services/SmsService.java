package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import javax.annotation.Resource;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutRequest;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutResult;
import com.amazonaws.services.sns.model.OptInPhoneNumberRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.upload.UploadValidationException;
import org.sagebionetworks.bridge.validators.SmsMessageValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service for handling SMS metadata (opt-outs, message logging) and for handling webhooks for receiving SMS. */
@Component
public class SmsService {
    private static final Logger LOG = LoggerFactory.getLogger(SmsService.class);

    static final String BRIDGE_SERVER_APP_VERSION = "Bridge Server";
    static final String BRIDGE_SERVER_PHONE_INFO = "Bridge Server";
    static final String FIELD_NAME_SENT_ON = "sentOn";
    static final String FIELD_NAME_SMS_TYPE = "smsType";
    static final String FIELD_NAME_MESSAGE_BODY = "messageBody";
    static final String MESSAGE_LOG_SCHEMA_ID = "sms-messages-sent-from-bridge";
    static final String MESSAGE_LOG_SCHEMA_NAME = "SMS Messages Sent From Bridge";
    static final int MESSAGE_LOG_SCHEMA_REV = 1;

    // mPower 2.0 study burst notifications can be fairly long. The longest one has 230 chars of fixed content, an app
    // url that's 53 characters long, and some freeform text that can be potentially 255 characters long, for a total
    // of 538 characters. Round to a nice round 600 characters (about 4.5 SMS messages, if broken up).
    private static final int SMS_CHARACTER_LIMIT = 600;

    private HealthDataService healthDataService;
    private SmsMessageDao messageDao;
    private ParticipantService participantService;
    private UploadSchemaService schemaService;
    private AmazonSNSClient snsClient;

    /** Health data service, used to submit SMS message logs as health data. */
    @Autowired
    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /** Message DAO, for writing to and reading from the SMS message log. */
    @Autowired
    public final void setMessageDao(SmsMessageDao messageDao) {
        this.messageDao = messageDao;
    }

    /** Participant service. */
    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /** Schema service, used to initialize the SMS Message Long schema. */
    @Autowired
    public final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /** SNS client, to send SMS through AWS. */
    @Resource(name = "snsClient")
    public final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }

    /**
     * Sends an SMS message using the given message provider. User ID is used to fetch the account, so we can get
     * health code and time zone and other relevant attributes to log and record as health data. If the recipient
     * doesn't have an account (for example, for Intent-to-Participate), this can be left null.
     */
    public void sendSmsMessage(String userId, SmsMessageProvider provider) {
        checkNotNull(provider);
        Study study = provider.getStudy();
        Phone recipientPhone = provider.getPhone();
        String message = provider.getFormattedMessage();

        // Check max SMS length.
        if (message.getBytes(Charset.forName("US-ASCII")).length > SMS_CHARACTER_LIMIT) {
            throw new BridgeServiceException("SMS message cannot be longer than 600 UTF-8/ASCII characters.");
        }

        // Send SMS.
        String messageId;
        PublishResult result = snsClient.publish(provider.getSmsRequest());
        messageId = result.getMessageId();

        LOG.debug("Sent SMS message, study=" + study.getIdentifier() + ", message ID=" + messageId + ", request ID=" +
                BridgeUtils.getRequestContext().getId());

        // Log SMS message.
        DateTime sentOn = DateTime.now();
        SmsMessage smsMessage = SmsMessage.create();
        smsMessage.setPhoneNumber(recipientPhone.getNumber());
        smsMessage.setSentOn(sentOn.getMillis());
        smsMessage.setMessageBody(message);
        smsMessage.setMessageId(messageId);
        smsMessage.setSmsType(provider.getSmsTypeEnum());
        smsMessage.setStudyId(study.getIdentifier());

        // Fetch participant, if it exists.
        StudyParticipant participant = null;
        if (userId != null) {
            participant = participantService.getParticipant(study, userId, false);
        }

        // Finish logging SMS message.
        if (participant != null) {
            smsMessage.setHealthCode(participant.getHealthCode());
        }
        Validate.entityThrowingException(SmsMessageValidator.INSTANCE, smsMessage);
        messageDao.logMessage(smsMessage);

        // If we have a participant, make a health data.
        if (participant != null) {
            initMessageLogSchema(study.getStudyIdentifier());

            // Set sentOn w/ user's time zone, if it exists.
            DateTime sentOnWithTimeZone;
            if (participant.getTimeZone() != null) {
                sentOnWithTimeZone = sentOn.withZone(participant.getTimeZone());
            } else {
                sentOnWithTimeZone = sentOn.withZone(DateTimeZone.UTC);
            }

            // Create health data.
            ObjectNode healthDataNode = BridgeObjectMapper.get().createObjectNode();
            healthDataNode.put(FIELD_NAME_SENT_ON, sentOnWithTimeZone.toString());
            healthDataNode.put(FIELD_NAME_SMS_TYPE, provider.getSmsType());
            healthDataNode.put(FIELD_NAME_MESSAGE_BODY, message);

            // Health Data Service requires app version and phone info. However, this health data is submitted by
            // Bridge, not by the app, so fill those in with artificial values.
            HealthDataSubmission healthData = new HealthDataSubmission.Builder()
                    .withAppVersion(BRIDGE_SERVER_APP_VERSION).withPhoneInfo(BRIDGE_SERVER_PHONE_INFO)
                    .withCreatedOn(sentOnWithTimeZone).withSchemaId(MESSAGE_LOG_SCHEMA_ID)
                    .withSchemaRevision(MESSAGE_LOG_SCHEMA_REV).withData(healthDataNode).build();
            try {
                healthDataService.submitHealthData(study.getStudyIdentifier(), participant, healthData);
            } catch (IOException | UploadValidationException ex) {
                throw new BridgeServiceException(ex);
            }
        }
    }

    // Helper method to init the SMS log schema for the study.
    private void initMessageLogSchema(StudyIdentifier studyId) {
        // See if schema already exists.
        UploadSchema existingSchema = null;
        try {
            existingSchema = schemaService.getUploadSchemaByIdAndRev(studyId, MESSAGE_LOG_SCHEMA_ID,
                    MESSAGE_LOG_SCHEMA_REV);
        } catch (EntityNotFoundException ex) {
            // Suppress exception. If get throws, messageLogSchema will be null.
        }
        if (existingSchema != null) {
            return;
        }

        // No schema. Create new schema.
        UploadSchema schemaToCreate = UploadSchema.create();
        schemaToCreate.setSchemaId(MESSAGE_LOG_SCHEMA_ID);
        schemaToCreate.setRevision(MESSAGE_LOG_SCHEMA_REV);
        schemaToCreate.setName(MESSAGE_LOG_SCHEMA_NAME);
        schemaToCreate.setSchemaType(UploadSchemaType.IOS_DATA);

        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName(FIELD_NAME_SENT_ON).withType(UploadFieldType.TIMESTAMP)
                        .build(),
                new UploadFieldDefinition.Builder().withName(FIELD_NAME_SMS_TYPE).withType(UploadFieldType.STRING)
                        .withMaxLength(SmsType.VALUE_MAX_LENGTH).build(),
                new UploadFieldDefinition.Builder().withName(FIELD_NAME_MESSAGE_BODY).withType(UploadFieldType.STRING)
                        .withUnboundedText(true).build());
        schemaToCreate.setFieldDefinitions(fieldDefList);

        schemaService.createSchemaRevisionV4(studyId, schemaToCreate);
    }

    /** Gets the message we most recently sent to the given phone number. */
    public SmsMessage getMostRecentMessage(String number) {
        if (StringUtils.isBlank(number)) {
            throw new BadRequestException("number is required");
        }
        return messageDao.getMostRecentMessage(number);
    }

    /**
     * Opt a phone number back in if it is opted out. This is only used when a new account is created, generally in
     * a new study. User ID is used for logging.
     */
    public void optInPhoneNumber(String userId, Phone phone) {
        if (StringUtils.isBlank(userId)) {
            throw new BadRequestException("userId is required");
        }
        if (phone == null) {
            throw new BadRequestException("phone is required");
        }
        if (!Phone.isValid(phone)) {
            throw new BadRequestException("phone is invalid");
        }

        // Check if phone number is opted out.
        CheckIfPhoneNumberIsOptedOutRequest checkRequest = new CheckIfPhoneNumberIsOptedOutRequest()
                .withPhoneNumber(phone.getNumber());
        CheckIfPhoneNumberIsOptedOutResult checkResult = snsClient.checkIfPhoneNumberIsOptedOut(checkRequest);

        if (Boolean.TRUE.equals(checkResult.isOptedOut())) {
            LOG.info("Opting in user " + userId + " for SMS messages");

            // User was previously opted out. They created a new account (almost certainly in a new study). We need
            // to opt them back in. Note that according to AWS, this can only be done once every 30 days to prevent
            // abuse.
            OptInPhoneNumberRequest optInRequest = new OptInPhoneNumberRequest().withPhoneNumber(
                    phone.getNumber());
            snsClient.optInPhoneNumber(optInRequest);
        }
    }
}
