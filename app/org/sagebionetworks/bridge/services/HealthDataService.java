package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.schema.SchemaUtils;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadFileHelper;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.upload.UploadValidationContext;
import org.sagebionetworks.bridge.upload.UploadValidationException;
import org.sagebionetworks.bridge.validators.HealthDataSubmissionValidator;
import org.sagebionetworks.bridge.validators.RecordExportStatusRequestValidator;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;


/** Service handler for health data APIs. */
@Component
public class HealthDataService {
    private static final int MAX_DATE_RANGE_DAYS = 15;

    // Package-scoped for unit tests.
    static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");
    static final long CREATED_ON_OFFSET_MILLIS = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
    static final String RAW_ATTACHMENT_SUFFIX = "-raw.json";

    private HealthDataDao healthDataDao;
    private S3Helper s3Helper;
    private SurveyService surveyService;
    private UploadSchemaService schemaService;
    private UploadFileHelper uploadFileHelper;

    // Upload Validation Handlers, used by the synchronous Health Data Submission API
    private StrictValidationHandler strictValidationHandler;
    private TranscribeConsentHandler transcribeConsentHandler;
    private UploadArtifactsHandler uploadArtifactsHandler;

    private final static int MAX_NUM_RECORD_IDS = 25;
    private static final Validator exporterStatusValidator = new RecordExportStatusRequestValidator();

    /** Health data DAO. This is configured by Spring. */
    @Autowired
    public final void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /** S3 Helper, used to submit raw JSON as an attachment. */
    @Autowired
    public void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    /** Survey service, if we're submitting a survey response. */
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** Schema Service */
    @Autowired
    public final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /** Upload file helper, used to upload attachments. */
    @Autowired
    public final void setUploadFileHelper(UploadFileHelper uploadFileHelper) {
        this.uploadFileHelper = uploadFileHelper;
    }

    /** Strict Validation Handler, which canonicalizes health data and verifies required fields. */
    @Autowired
    public final void setStrictValidationHandler(StrictValidationHandler strictValidationHandler) {
        this.strictValidationHandler = strictValidationHandler;
    }

    /**
     * Transcribe Consent Handler, which should be called Transcribe Participant Options Handler. This transcribes
     * external ID, data groups, and sharing scope into the health data record.
     */
    @Autowired
    public final void setTranscribeConsentHandler(TranscribeConsentHandler transcribeConsentHandler) {
        this.transcribeConsentHandler = transcribeConsentHandler;
    }

    /** Upload Artifacts Handler, which finalizes the record and attachments and uploads them to DDB and S3. */
    @Autowired
    public final void setUploadArtifactsHandler(UploadArtifactsHandler uploadArtifactsHandler) {
        this.uploadArtifactsHandler = uploadArtifactsHandler;
    }

    /* HEALTH DATA SUBMISSION */

    /**
     * Synchronous health data API. Used to submit small health data payloads (such as survey responses) without
     * incurring the overhead of creating a bunch of small files to upload to S3.
     */
    public HealthDataRecord submitHealthData(StudyIdentifier studyId, StudyParticipant participant,
            HealthDataSubmission healthDataSubmission) throws IOException, UploadValidationException {
        // validate health data submission
        if (healthDataSubmission == null) {
            throw new InvalidEntityException("Health data submission cannot be null");
        }
        Validate.entityThrowingException(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission);

        // sanitize field names in the data node
        JsonNode sanitizedData = sanitizeFieldNames(healthDataSubmission.getData());

        // get schema
        UploadSchema schema = getSchemaForSubmission(studyId, healthDataSubmission);

        // Generate a new uploadId.
        String uploadId = BridgeUtils.generateGuid();

        // Filter data fields and attachments based on schema fields.
        ObjectNode filteredData = BridgeObjectMapper.get().createObjectNode();
        filterAttachments(uploadId, schema, sanitizedData, filteredData);

        // construct health data record
        HealthDataRecord record = makeRecordFromSubmission(studyId, participant, schema, healthDataSubmission, filteredData);

        // Construct UploadValidationContext for the remaining upload handlers. We don't need all the fields, just the
        // ones that these handlers will be using.
        UploadValidationContext uploadValidationContext = new UploadValidationContext();
        uploadValidationContext.setHealthCode(participant.getHealthCode());
        uploadValidationContext.setHealthDataRecord(record);
        uploadValidationContext.setStudy(studyId);

        // For back-compat reasons, we need to make a dummy upload to store the uploadId. This will never be persisted.
        // We just need a way to signal the Upload Validation pipeline to use this uploadId.
        Upload upload = Upload.create();
        upload.setUploadId(uploadId);
        uploadValidationContext.setUpload(upload);

        // Strict Validation Handler. If this throws, this is an invalid upload (400).
        try {
            strictValidationHandler.handle(uploadValidationContext);
        } catch (UploadValidationException ex) {
            throw new BadRequestException(ex);
        }

        // Transcribe Consent.
        transcribeConsentHandler.handle(uploadValidationContext);

        // Upload raw JSON as the raw data attachment. This is different from how the upload validation handles it.
        // Attachment ID is "[uploadId]-raw.json".
        String rawDataAttachmentId = uploadId + RAW_ATTACHMENT_SUFFIX;
        String rawDataValue = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(
                healthDataSubmission.getData());
        byte[] rawDataBytes = rawDataValue.getBytes(Charsets.UTF_8);
        
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        
        s3Helper.writeBytesToS3(ATTACHMENT_BUCKET, rawDataAttachmentId, rawDataBytes, metadata);

        record.setRawDataAttachmentId(rawDataAttachmentId);

        // Upload Artifacts.
        uploadArtifactsHandler.handle(uploadValidationContext);

        // UploadArtifactsHandler doesn't return the record. It also may make potentially two writes to the record
        // (depending on attachments). So we need to use the record ID to fetch the record again and return it.
        return getRecordById(uploadValidationContext.getRecordId());
    }

    // Helper method, which sanitizes all field names in the given JsonNode, as per the rules specified in
    // SchemaUtils.sanitizeFieldName().
    private static JsonNode sanitizeFieldNames(JsonNode data) {
        ObjectNode sanitizedData = BridgeObjectMapper.get().createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> dataFieldIter = data.fields();
        while (dataFieldIter.hasNext()) {
            Map.Entry<String, JsonNode> oneDataField = dataFieldIter.next();
            String fieldName = oneDataField.getKey();
            JsonNode fieldValue = oneDataField.getValue();
            String sanitizedFieldName = SchemaUtils.sanitizeFieldName(fieldName);
            sanitizedData.set(sanitizedFieldName, fieldValue);
        }
        return sanitizedData;
    }

    // Helper method which encapsulates getting the schema, either by schemaId/Revision or by surveyGuid/CreatedOn.
    private UploadSchema getSchemaForSubmission(StudyIdentifier studyId, HealthDataSubmission healthDataSubmission) {
        if (healthDataSubmission.getSchemaId() != null) {
            return schemaService.getUploadSchemaByIdAndRev(studyId, healthDataSubmission.getSchemaId(),
                    healthDataSubmission.getSchemaRevision());
        } else {
            // surveyCreatedOn is a timestamp. SurveyService takes long epoch millis. Convert.
            long surveyCreatedOnMillis = healthDataSubmission.getSurveyCreatedOn().getMillis();

            // Get survey. We use the survey identifier as the schema ID and the schema revision. Both of these must be
            // specified.
            String surveyGuid = healthDataSubmission.getSurveyGuid();
            GuidCreatedOnVersionHolder surveyKeys = new GuidCreatedOnVersionHolderImpl(surveyGuid, surveyCreatedOnMillis);
            Survey survey = surveyService.getSurvey(studyId, surveyKeys, false, true);
            String schemaId = survey.getIdentifier();
            Integer schemaRev = survey.getSchemaRevision();
            if (StringUtils.isBlank(schemaId) || schemaRev == null) {
                throw new EntityNotFoundException(UploadSchema.class, "Schema not found for survey " + surveyGuid +
                        ":" + surveyCreatedOnMillis);
            }

            // Get the schema with the schema ID and rev.
            return schemaService.getUploadSchemaByIdAndRev(studyId, schemaId, schemaRev);
        }
    }

    /**
     * Helper method, which goes through the given schema, and splits the input data into field values and attachments.
     * Fields that are not present in the schema are silently dropped.
     */
    private void filterAttachments(String recordId, UploadSchema schema, JsonNode inputData, ObjectNode outputData)
            throws UploadValidationException {
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();
            JsonNode fieldValue = inputData.get(fieldName);

            if (fieldValue == null || fieldValue.isNull()) {
                if (UploadUtil.FIELD_ANSWERS.equals(fieldName)) {
                    // Special case: This is the auto-generated "answers" field for surveys, which contains raw
                    // key-value pairs for all survey questions.
                    fieldValue = inputData;
                } else {
                    // Skip non-existent fields.
                    continue;
                }
            }

            // filter on fieldType
            if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                JsonNode attachmentIdNode = uploadFileHelper.uploadJsonNodeAsAttachment(fieldValue, recordId,
                        fieldName);
                outputData.set(fieldName, attachmentIdNode);
            } else {
                outputData.set(fieldName, fieldValue);
            }
        }
    }

    /**
     * Helper method, which creates a health data record from the given health data submission.
     *
     * @param studyId
     *         study this health data was submitted to
     * @param participant
     *         participant who submitted the data
     * @param schema
     *         the schema this data is submitted for
     * @param healthDataSubmission
     *         the data submission
     * @param filteredData
     *         raw data, after being sanitized and filtered
     * @return created health data record
     */
    private static HealthDataRecord makeRecordFromSubmission(StudyIdentifier studyId, StudyParticipant participant,
            UploadSchema schema, HealthDataSubmission healthDataSubmission, JsonNode filteredData) {
        // from submission
        HealthDataRecord record = HealthDataRecord.create();
        record.setAppVersion(healthDataSubmission.getAppVersion());
        record.setPhoneInfo(healthDataSubmission.getPhoneInfo());
        record.setUserMetadata(healthDataSubmission.getMetadata());

        // from elsewhere
        record.setData(filteredData);
        record.setHealthCode(participant.getHealthCode());
        record.setSchemaId(schema.getSchemaId());
        record.setSchemaRevision(schema.getRevision());
        record.setStudyId(studyId.getIdentifier());
        record.setUploadDate(DateUtils.getCurrentCalendarDateInLocalTime());
        record.setUploadedOn(DateUtils.getCurrentMillisFromEpoch());

        // For back-compat, add appVersion and phoneInfo into metadata.
        ObjectNode metadata = BridgeObjectMapper.get().createObjectNode();
        metadata.put(UploadUtil.FIELD_APP_VERSION, healthDataSubmission.getAppVersion());
        metadata.put(UploadUtil.FIELD_PHONE_INFO, healthDataSubmission.getPhoneInfo());
        record.setMetadata(metadata);

        // Store createdOn time zone as a string offset (eg "-0700"). This is because Synapse doesn't support ISO
        // timestamps.
        DateTime createdOn = healthDataSubmission.getCreatedOn();
        record.setCreatedOn(createdOn.getMillis());
        record.setCreatedOnTimeZone(HealthDataRecord.TIME_ZONE_FORMATTER.print(createdOn));

        return record;
    }

    /* HEALTH DATA RECORD APIs */

    /**
     * Creates (or updates) and persists a health data record, using the passed in prototype object as the base. This
     * method is generally used by worker apps as part of upload unpacking.
     *
     * @param record
     *         the prototype record to create from and persist, must be non-null and have valid fields
     * @return the record ID of the persisted record
     */
    public String createOrUpdateRecord(HealthDataRecord record) {
        // validate record
        if (record == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "HealthDataRecord"));
        }
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        // TODO: validate health code, schema ID against DDB tables

        // call through to DAO
        return healthDataDao.createOrUpdateRecord(record);
    }

    /**
     * Deletes all records for the given health code (which corresponds to a user in a particular study). This method
     * is used by admin accounts to delete user health data, or by the user admin service to delete health data when a
     * deleting a user account
     *
     * @param healthCode
     *         health code to delete records for, must be non-null and non-empty
     * @return number of records deleted
     */
    public int deleteRecordsForHealthCode(String healthCode) {
        // validate health code
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "healthCode"));
        }
        // TODO: validate health code against health code table

        // call through to DAO
        return healthDataDao.deleteRecordsForHealthCode(healthCode);
    }

    /**
     * Gets the health data record using the record ID.
     *
     * @param id
     *         record ID
     * @return health data record
     */
    public HealthDataRecord getRecordById(String id) {
        // validate ID
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "id"));
        }

        // call through to DAO
        return healthDataDao.getRecordById(id);
    }

    /**
     * Returns a list of all health data records with the given upload date. THis method is generally called by
     * worker apps as part of data export.
     *
     * @param uploadDate
     *         an upload date in YYYY-MM-DD format, must be non-null, non-empty, and represent a valid date
     * @return list of health data records
     */
    public List<HealthDataRecord> getRecordsForUploadDate(String uploadDate) {
        // validate upload date
        if (StringUtils.isBlank(uploadDate)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadDate"));
        }

        // use Joda to parse the upload date, to validate it
        // The DAO takes a string, so we don't ever actually need the LocalDate instance.
        try {
            DateUtils.parseCalendarDate(uploadDate);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(String.format("Expected date format YYYY-MM-DD, received %s", uploadDate));
        }

        // call through to DAO
        return healthDataDao.getRecordsForUploadDate(uploadDate);
    }

    /** Gets a list of records for the given healthCode between the specified createdOn times (inclusive). */
    public List<HealthDataRecord> getRecordsByHealthCodeCreatedOn(String healthCode, DateTime createdOnStart,
            DateTime createdOnEnd) {
        Preconditions.checkArgument(StringUtils.isNotBlank(healthCode));
        if (createdOnStart == null) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_NULL, "createdOnStart"));
        }
        if (createdOnEnd == null) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_NULL, "createdOnEnd"));
        }
        if (createdOnStart.isAfter(createdOnEnd)) {
            throw new BadRequestException("createdOnStart can't be after createdOnEnd");
        }
        if (createdOnStart.plusDays(MAX_DATE_RANGE_DAYS).isBefore(createdOnEnd)) {
            throw new BadRequestException("maximum date range is " + MAX_DATE_RANGE_DAYS + " days");
        }

        return healthDataDao.getRecordsByHealthCodeCreatedOn(healthCode, createdOnStart.getMillis(),
                createdOnEnd.getMillis());
    }

    /** Get a list of records with the same healthCode and schemaId that are within an hour of the createdOn. */
    public List<HealthDataRecord> getRecordsByHealthcodeCreatedOnSchemaId(String healthCode, long createdOn, String schemaId) {
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "healthCode"));
        }
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "schemaId"));
        }

        List<HealthDataRecord> recordList = healthDataDao.getRecordsByHealthCodeCreatedOn(healthCode,
                createdOn - CREATED_ON_OFFSET_MILLIS, createdOn + CREATED_ON_OFFSET_MILLIS);
        return recordList.stream().filter(record -> schemaId.equals(record.getSchemaId())).collect(
                Collectors.toList());
    }

    /**
     * returns received list of record Ids after updating
     * @param recordExportStatusRequest
     *         POJO contains: a lit of health record ids, not upload ids and
     *         an Synapse Exporter Status with value either NOT_EXPORTED or SUCCEEDED
     * @return updated health record ids list
     */
    public List<String> updateRecordsWithExporterStatus(RecordExportStatusRequest recordExportStatusRequest) {
        Validate.entityThrowingException(exporterStatusValidator, recordExportStatusRequest);

        List<String> healthRecordIds = recordExportStatusRequest.getRecordIds();
        HealthDataRecord.ExporterStatus synapseExporterStatus = recordExportStatusRequest.getSynapseExporterStatus();

        if (healthRecordIds.size() > MAX_NUM_RECORD_IDS) {
            throw new BadRequestException("Size of the record ids list exceeds the limit.");
        }

        List<String> updatedRecordIds = healthRecordIds.stream().map(id->{
            HealthDataRecord record = getRecordById(id);
            if (record == null) {
                throw new NotFoundException("The record: " + id + " cannot be found in our database.");
            }
            record.setSynapseExporterStatus(synapseExporterStatus);
            return createOrUpdateRecord(record);
        }).collect(Collectors.toList());

        return updatedRecordIds;
    }
}
