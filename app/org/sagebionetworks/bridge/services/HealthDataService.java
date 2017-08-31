package org.sagebionetworks.bridge.services;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.schema.SchemaUtils;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.upload.UploadValidationContext;
import org.sagebionetworks.bridge.upload.UploadValidationException;
import org.sagebionetworks.bridge.validators.HealthDataSubmissionValidator;
import org.sagebionetworks.bridge.validators.RecordExportStatusRequestValidator;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataAttachmentDao;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;


/** Service handler for health data APIs. */
@Component
public class HealthDataService {
    private HealthDataAttachmentDao healthDataAttachmentDao;
    private HealthDataDao healthDataDao;
    private UploadSchemaService schemaService;

    // Upload Validation Handlers, used by the synchronous Health Data Submission API
    private StrictValidationHandler strictValidationHandler;
    private TranscribeConsentHandler transcribeConsentHandler;
    private UploadArtifactsHandler uploadArtifactsHandler;

    private final static int MAX_NUM_RECORD_IDS = 25;
    private static final Validator exporterStatusValidator = new RecordExportStatusRequestValidator();

    /** Health data attachment DAO. This is configured by Spring. */
    @Autowired
    public final void setHealthDataAttachmentDao(HealthDataAttachmentDao healthDataAttachmentDao) {
        this.healthDataAttachmentDao = healthDataAttachmentDao;
    }

    /** Health data DAO. This is configured by Spring. */
    @Autowired
    public final void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /** Schema Service */
    @Autowired
    public final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
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
            HealthDataSubmission healthDataSubmission) throws JsonProcessingException {
        // validate health data submission
        if (healthDataSubmission == null) {
            throw new InvalidEntityException("Health data submission cannot be null");
        }
        Validate.entityThrowingException(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission);

        // sanitize field names in the data node
        JsonNode sanitizedData = sanitizeFieldNames(healthDataSubmission.getData());

        // Filter data fields and attachments based on schema fields.
        UploadSchema schema = schemaService.getUploadSchemaByIdAndRev(studyId, healthDataSubmission.getSchemaId(),
                healthDataSubmission.getSchemaRevision());
        ObjectNode filteredData = BridgeObjectMapper.get().createObjectNode();
        Map<String, byte[]> attachmentMap = new HashMap<>();
        filterAttachments(schema, sanitizedData, filteredData, attachmentMap);

        // construct health data record
        HealthDataRecord record = makeRecordFromSubmission(studyId, participant, healthDataSubmission, filteredData);

        // Construct UploadValidationContext for the remaining upload handlers. We don't need all the fields, just the
        // ones that these handlers will be using.
        UploadValidationContext uploadValidationContext = new UploadValidationContext();
        uploadValidationContext.setAttachmentsByFieldName(attachmentMap);
        uploadValidationContext.setHealthCode(participant.getHealthCode());
        uploadValidationContext.setHealthDataRecord(record);
        uploadValidationContext.setStudy(studyId);

        // Strict Validation Handler. If this throws, this is an invalid upload (400).
        try {
            strictValidationHandler.handle(uploadValidationContext);
        } catch (UploadValidationException ex) {
            throw new BadRequestException(ex);
        }

        // Transcribe Consent and Upload Artifacts.
        transcribeConsentHandler.handle(uploadValidationContext);
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

    /**
     * Helper method, which goes through the given schema, and splits the input data into field values and attachments.
     * Fields that are not present in the schema are silently dropped.
     *
     * @param schema
     *         schema with which to process this data
     * @param inputData
     *         data, with field names already sanitizied
     * @param outputData
     *         data, with attachment fields removed
     * @param attachmentMap
     *         map of attachment data
     * @throws JsonProcessingException
     *         if serializing attachment fails
     */
    private static void filterAttachments(UploadSchema schema, JsonNode inputData, ObjectNode outputData,
            Map<String, byte[]> attachmentMap) throws JsonProcessingException {
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();
            JsonNode fieldValue = inputData.get(fieldName);

            // Skip non-existent fields.
            if (fieldValue == null || fieldValue.isNull()) {
                continue;
            }

            // filter on fieldType
            if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                attachmentMap.put(fieldName, BridgeObjectMapper.get().writeValueAsBytes(fieldValue));
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
     * @param healthDataSubmission
     *         the data submission
     * @param filteredData
     *         raw data, after being sanitized and filtered
     * @return created health data record
     */
    private static HealthDataRecord makeRecordFromSubmission(StudyIdentifier studyId, StudyParticipant participant,
            HealthDataSubmission healthDataSubmission, JsonNode filteredData) {
        // from submission
        HealthDataRecord record = HealthDataRecord.create();
        record.setAppVersion(healthDataSubmission.getAppVersion());
        record.setPhoneInfo(healthDataSubmission.getPhoneInfo());
        record.setSchemaId(healthDataSubmission.getSchemaId());
        record.setSchemaRevision(healthDataSubmission.getSchemaRevision());

        // from elsewhere
        record.setData(filteredData);
        record.setHealthCode(participant.getHealthCode());
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

    public List<HealthDataRecord> getRecordsByHealthcodeCreatedOnSchemaId(String healthCode, Long createdOn, String schemaId) {
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "healthCode"));
        }
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "schemaId"));
        }
        if (createdOn == null) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_NULL, "createdOn"));
        }

        return healthDataDao.getRecordsByHealthCodeCreatedOnSchemaId(healthCode, createdOn, schemaId);
    }

    /* HEALTH DATA ATTACHMENT APIs */

    /**
     * Creates or updates a health data attachment. If the specified attachment has no ID, this is consider a new
     * attachment will be created. If the specified attachment does have an ID, this is considered updating an existing
     * attachment.
     *
     * @param attachment
     *         health data attachment to create or update
     * @return attachment ID of the created or updated attachment
     */
    public String createOrUpdateAttachment(HealthDataAttachment attachment) {
        // validate attachment
        if (attachment == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "HealthDataAttachment"));
        }
        // TODO: validate fields for non-null-ness and non-emptiness
        // TODO: validate record ID against records

        // call through to DAO
        return healthDataAttachmentDao.createOrUpdateAttachment(attachment);
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
