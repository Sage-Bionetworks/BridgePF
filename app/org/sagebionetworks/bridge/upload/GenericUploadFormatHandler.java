package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Processes uploads for the v2_generic format. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataFileMap} and updates the existing record in
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecord}.
 * </p>
 * <p>
 * There is some overlap between this code and IosSchemaValidationHandler. However, we have opted to build this handler
 * as an entirely separate class rather than create a monolithic handler to do both. This is to isolate the v1_legacy
 * stuff from the v2_generic stuff, and to avoid propagating the hacks that were created to address launch day data.
 * Truly shared code has been refactored either into InitRecordHandler and UploadUtil.
 * </p>
 * <p>
 * Name note: The upload format is generic, not the handler.
 * </p>
 */
@Component
public class GenericUploadFormatHandler implements UploadValidationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GenericUploadFormatHandler.class);

    private int dataFileSizeLimit = UploadUtil.FILE_SIZE_LIMIT_DATA_FILE;
    private FileHelper fileHelper;
    private SurveyService surveyService;
    private UploadFileHelper uploadFileHelper;
    private UploadSchemaService uploadSchemaService;

    /** File size limit for data file. Package scoped so that unit tests can override. */
    final void setDataFileSizeLimit(@SuppressWarnings("SameParameterValue") int dataFileSizeLimit) {
        this.dataFileSizeLimit = dataFileSizeLimit;
    }

    /** File helper, used to check file sizes before parsing them into memory. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /**
     * Upload file helper, used to find upload fields in a list of files, and parse files and upload attachments as
     * needed.
     */
    @Autowired
    public final void setUploadFileHelper(UploadFileHelper uploadFileHelper) {
        this.uploadFileHelper = uploadFileHelper;
    }

    /** Survey service, to get the survey if this upload is a survey. Configured by Spring. */
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** Upload Schema Service, used to get the schema corresponding to the upload. This is configured by Spring. */
    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        HealthDataRecord record = context.getHealthDataRecord();
        ObjectNode dataMap = (ObjectNode) record.getData();
        JsonNode infoJson = context.getInfoJsonNode();
        StudyIdentifier studyId = context.getStudy();
        Map<String, File> unzippedDataFileMap = context.getUnzippedDataFileMap();

        // Get schema from info.json
        UploadSchema schema = getUploadSchema(studyId, infoJson);
        record.setSchemaId(schema.getSchemaId());
        record.setSchemaRevision(schema.getRevision());

        // Other parameters from info.json.
        parseCreatedOnToRecord(context, infoJson, record);
        String dataFilename = JsonUtils.asText(infoJson, UploadUtil.FIELD_DATA_FILENAME);

        // Parse data into the health data record, using the schema.
        handleData(context, dataFilename, unzippedDataFileMap, schema, dataMap);
    }

    // Helper method to get a schema based on inputs from info.json.
    // Package-scoped to facilitate unit tests.
    UploadSchema getUploadSchema(StudyIdentifier studyId, JsonNode infoJson) throws UploadValidationException {
        // Try getting by survey first.
        String surveyGuid = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_GUID);
        String surveyCreatedOn = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_CREATED_ON);
        if (StringUtils.isNotBlank(surveyGuid) && StringUtils.isNotBlank(surveyCreatedOn)) {
            // surveyCreatedOn is a timestamp. SurveyService takes long epoch millis. Convert.
            long surveyCreatedOnMillis= DateUtils.convertToMillisFromEpoch(surveyCreatedOn);

            // Get survey. We use the survey identifier as the schema ID and the schema revision. Both of these must be
            // specified.
            GuidCreatedOnVersionHolder surveyKeys = new GuidCreatedOnVersionHolderImpl(surveyGuid, surveyCreatedOnMillis);
            Survey survey = surveyService.getSurvey(studyId, surveyKeys, false, true);
            String surveySchemaId = survey.getIdentifier();
            Integer surveySchemaRev = survey.getSchemaRevision();
            if (StringUtils.isBlank(surveySchemaId) || surveySchemaRev == null) {
                throw new UploadValidationException("Schema not found for survey " + surveyGuid + ":" +
                        surveyCreatedOnMillis);
            }

            // Get the schema with the schema ID and rev.
            return uploadSchemaService.getUploadSchemaByIdAndRev(studyId, surveySchemaId, surveySchemaRev);
        }

        // Fall back to getting by schema.
        String schemaId = JsonUtils.asText(infoJson, UploadUtil.FIELD_ITEM);
        Integer schemaRev = JsonUtils.asInt(infoJson, UploadUtil.FIELD_SCHEMA_REV);
        if (StringUtils.isNotBlank(schemaId) && schemaRev != null) {
            return uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, schemaRev);
        } else {
            throw new UploadValidationException("info.json must contain either item and schemaRevision or " +
                    "surveyGuid and surveyCreatedOn");
        }
    }

    // Helper method to read and parse the createdOn from info.json and add it to the health data record. Handles
    // fall-back logic.
    // Package-scoped to facilitate unit tests.
    static void parseCreatedOnToRecord(UploadValidationContext context, JsonNode infoJson, HealthDataRecord record) {
        // createdOn string from info.json
        String createdOnString = JsonUtils.asText(infoJson, UploadUtil.FIELD_CREATED_ON);

        // Parse into a Joda DateTime.
        DateTime createdOn = null;
        if (StringUtils.isNotBlank(createdOnString)) {
            try {
                createdOn = DateTime.parse(createdOnString);
            } catch (IllegalArgumentException ex) {
                // Write a message to the validation context, but there's no need to log or throw.
                context.addMessage("Invalid date-time: " + createdOnString);
            }
        }

        if (createdOn != null) {
            // Use createdOn and timezone as specified in the upload.
            record.setCreatedOn(createdOn.getMillis());
            record.setCreatedOnTimeZone(HealthDataRecord.TIME_ZONE_FORMATTER.print(createdOn));
        } else {
            // Fall back to current time. Don't set a timezone, since it's indeterminate.
            context.addMessage("Upload has no createdOn; using current time.");
            record.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        }
    }

    // Helper method that, copies health data from the jsonDataMap and unzippedData maps to the dataMap or
    // attachmentMap, based on a schema. Also handles flattening and sanitization.
    private void handleData(UploadValidationContext context, String dataFilename,
            Map<String, File> unzippedDataFileMap, UploadSchema schema, ObjectNode dataMap)
            throws UploadValidationException {
        String uploadId = context.getUploadId();

        JsonNode dataFileNode = NullNode.instance;
        if (StringUtils.isNotBlank(dataFilename) && unzippedDataFileMap.containsKey(dataFilename)) {
            // Parse data file. Avoid parsing large files into memory. If it's larger than 2mb, warn. (In the future,
            // this is a hard limit and will throw.)
            File dataFile = unzippedDataFileMap.get(dataFilename);
            long dataFileSize = fileHelper.fileSize(dataFile);
            if (dataFileSize > dataFileSizeLimit) {
                LOG.warn("Upload data file exceeds max size, uploadId=" + uploadId + ", filename=" + dataFilename +
                        ", fileSize=" + dataFileSize + " bytes");
            } else {
                try (InputStream dataFileInputStream = fileHelper.getInputStream(dataFile)) {
                    dataFileNode = BridgeObjectMapper.get().readTree(dataFileInputStream);
                } catch (IOException ex) {
                    throw new UploadValidationException("Error parsing upload data file, uploadId=" + uploadId +
                            ", fileName=" + dataFilename, ex);
                }
            }
        }

        Map<String, File> sanitizedUnzippedDataFileMap = UploadUtil.sanitizeFieldNames(unzippedDataFileMap);
        Map<String, Map<String, JsonNode>> parsedSanitizedJsonFileCache = new HashMap<>();

        // Using schema, copy fields over to data map. Or if it's an attachment, add it to the attachment map.
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();
            JsonNode fieldNode;

            if (dataFileNode.has(fieldName)) {
                // If it's in the submitted data file, just use it.
                JsonNode fieldNodeFromDataFile = dataFileNode.get(fieldName);

                if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                    fieldNode = uploadFileHelper.uploadJsonNodeAsAttachment(fieldNodeFromDataFile, uploadId,
                            fieldName);
                } else {
                    fieldNode = fieldNodeFromDataFile;
                }
            } else {
                fieldNode = uploadFileHelper.findValueForField(uploadId, sanitizedUnzippedDataFileMap, oneFieldDef,
                        parsedSanitizedJsonFileCache);
            }

            if (fieldNode != null && !fieldNode.isNull()) {
                dataMap.set(fieldName, fieldNode);
            } else if (UploadUtil.FIELD_ANSWERS.equals(fieldName) && !dataFileNode.isNull()) {
                // Special case: This is the auto-generated "answers" field for surveys. Since surveys are usually
                // submitted using the dataFile, this should be populated by just copying over the dataFile.
                if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                    fieldNode = uploadFileHelper.uploadJsonNodeAsAttachment(dataFileNode, uploadId, fieldName);
                } else {
                    fieldNode = dataFileNode;
                }
                dataMap.set(fieldName, fieldNode);
            }
        }
    }
}
