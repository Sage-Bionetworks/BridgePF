package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
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
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Processes iOS data into health data records. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataFileMap} and updates the existing record in
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecord}.
 * </p>
 * <p>
 * Currently, all apps are iOS-based. However, when we start having non-iOS apps, we'll need to restructure this
 * so that it only runs in the iOS context.
 * </p>
 * <p>
 * Note that large swaths of this code are copied directly from the original IosSchemaValidatorHandler. The idea is to
 * create a copy of IosSchemaValidationHandler that we can modify at will without touching the original, so we can run
 * them side-by-side in production (using the TestingHandler) to validate our code changes.
 * </p>
 */
@Component
public class IosSchemaValidationHandler2 implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(IosSchemaValidationHandler2.class);

    private static final Pattern FILENAME_TIMESTAMP_PATTERN = Pattern.compile("-\\d{8,}");
    private static final String KEY_FILENAME = "filename";
    private static final String KEY_FILES = "files";
    private static final String KEY_IDENTIFIER = "identifier";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final Map<String, String> SURVEY_TYPE_TO_ANSWER_KEY_MAP = ImmutableMap.<String, String>builder()
            .put("Boolean", "booleanAnswer")
            .put("Date", "dateAnswer")
            .put("DateAndTime", "dateAnswer")
            .put("Decimal", "numericAnswer")
            .put("Integer", "numericAnswer")
            .put("MultipleChoice", "choiceAnswers")
                    // yes, None really gets the answer from scaleAnswer
            .put("None", "scaleAnswer")
            .put("Scale", "scaleAnswer")
            .put("SingleChoice", "choiceAnswers")
            .put("Text", "textAnswer")
            .put("TimeInterval", "intervalAnswer")
            .put("TimeOfDay", "dateComponentsAnswer")
            .build();

    private Map<String, Map<String, Integer>> defaultSchemaRevisionMap;
    private FileHelper fileHelper;
    private SurveyService surveyService;
    private UploadFileHelper uploadFileHelper;
    private UploadSchemaService uploadSchemaService;

    @Resource(name = "defaultSchemaRevisionMap")
    public final void setDefaultSchemaRevisionMap(Map<String, Map<String, Integer>> defaultSchemaRevisionMap) {
        this.defaultSchemaRevisionMap = defaultSchemaRevisionMap;
    }

    /** File helper, used to check file sizes before parsing them into memory. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** Survey service, to get the survey if this upload is a survey. Configured by Spring. */
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /**
     * Upload file helper, used to find upload fields in a list of files, and parse files and upload attachments as
     * needed.
     */
    @Autowired
    public final void setUploadFileHelper(UploadFileHelper uploadFileHelper) {
        this.uploadFileHelper = uploadFileHelper;
    }

    /** Upload Schema Service, used to get the schema corresponding to the upload. This is configured by Spring. */
    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /**
     * Processes iOS data into health data records. iOS data comes from a third party, and we have no control over the
     * data format. So our data validation needs to be as flexible as possible. Which means our error handling strategy
     * is to write a validation message, and then attempt to recover. This may cause cascading errors further down the
     * validation chain.
     *
     * @see org.sagebionetworks.bridge.upload.UploadValidationHandler#handle
     */
    @Override
    public void handle(@Nonnull UploadValidationContext context)
            throws UploadValidationException {
        HealthDataRecord record = context.getHealthDataRecord();
        ObjectNode dataMap = (ObjectNode) record.getData();
        JsonNode infoJson = context.getInfoJsonNode();
        Map<String, File> unzippedDataFileMap = context.getUnzippedDataFileMap();
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();

        // validate and normalize filenames
        validateInfoJsonFileList(context, uploadId, unzippedDataFileMap, infoJson, record);
        removeTimestampsFromFilenames(unzippedDataFileMap);

        // schema
        UploadSchema schema = getUploadSchema(context.getStudy(), infoJson);
        record.setSchemaId(schema.getSchemaId());
        record.setSchemaRevision(schema.getRevision());

        UploadSchemaType schemaType = schema.getSchemaType();
        if (schemaType == UploadSchemaType.IOS_SURVEY) {
            // Convert survey format to JSON data format. This means creating a JSON data map where the "filenames" are
            // just the question names (items) and the file data is the answer JSON node.
            Map<String, JsonNode> convertedSurveyMap = convertSurveyToJsonData(context, uploadId, unzippedDataFileMap);
            handleData(context, uploadId, convertedSurveyMap, unzippedDataFileMap, schema, dataMap);
        } else if (schemaType == UploadSchemaType.IOS_DATA) {
            handleData(context, uploadId, ImmutableMap.of(), unzippedDataFileMap, schema, dataMap);
        } else {
            throw new UploadValidationException(String.format("Invalid schema type %s", schemaType));
        }
    }

    // Determines the UploadSchema from the info.json. The schema ID is the "item" field and must be specified. The
    // schema revision is the "schemaRevision" field, which defaults to 1 if not specified.
    // Alternatively, surveyGuid and surveyCreatedOn are used to map the upload to a survey.
    //
    // This is package-scoped to facilitate unit tests.
    UploadSchema getUploadSchema(StudyIdentifier study, JsonNode infoJson) throws UploadValidationException {
        // get relevant params from info.json
        String item = JsonUtils.asText(infoJson, UploadUtil.FIELD_ITEM);
        Integer schemaRev = JsonUtils.asInt(infoJson, UploadUtil.FIELD_SCHEMA_REV);
        String surveyGuid = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_GUID);
        String surveyCreatedOn = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_CREATED_ON);

        // Old versions of YML apps sometimes send "identifier" instead of "item". This isn't officially supported,
        // but we have to do it anyway for backwards compatibility.
        String identifier = JsonUtils.asText(infoJson, KEY_IDENTIFIER);

        if (StringUtils.isNotBlank(surveyGuid) && StringUtils.isNotBlank(surveyCreatedOn)) {
            // First try getting the schema by survey.
            return getUploadSchemaBySurvey(study, surveyGuid, surveyCreatedOn);
        } else if (StringUtils.isNotBlank(item)) {
            // Fall back to item field.
            return getUploadSchemaByItemAndRev(study, item, schemaRev);
        } else if (StringUtils.isNotBlank(identifier)) {
            // Fall back to identifier field. Log a warning that we're using this non-standard field.
            logger.warn("info.json missing item field, falling back to identifier field, identifier=" + identifier);
            return getUploadSchemaByItemAndRev(study, identifier, schemaRev);
        } else {
            throw new UploadValidationException(
                    "info.json must contain either item or surveyGuid and surveyCreatedOn");
        }
    }

    private UploadSchema getUploadSchemaBySurvey(StudyIdentifier study, String surveyGuid, String surveyCreatedOn)
            throws UploadValidationException {
        // surveyCreatedOn is a timestamp. SurveyService takes long epoch millis. Convert.
        long surveyCreatedOnMillis= DateUtils.convertToMillisFromEpoch(surveyCreatedOn);

        // Get survey. We use the survey identifier as the schema ID and the schema revision. Both of these must be
        // specified.
        GuidCreatedOnVersionHolder surveyKeys = new GuidCreatedOnVersionHolderImpl(surveyGuid, surveyCreatedOnMillis);
        Survey survey = surveyService.getSurvey(study, surveyKeys, false, true);
        String schemaId = survey.getIdentifier();
        Integer schemaRev = survey.getSchemaRevision();
        if (StringUtils.isBlank(schemaId) || schemaRev == null) {
            throw new UploadValidationException("Schema not found for survey " + surveyGuid + ":" +
                    surveyCreatedOnMillis);
        }

        // Get the schema with the schema ID and rev.
        return uploadSchemaService.getUploadSchemaByIdAndRev(study, schemaId, schemaRev);
    }

    private UploadSchema getUploadSchemaByItemAndRev(StudyIdentifier study, String item, Integer schemaRev) {
        if (schemaRev == null && defaultSchemaRevisionMap != null) {
            // Fall back to the legacy default schema rev map. This map exists because some schemas had their versions
            // bumped before the apps started sending schemaRevision.
            Map<String, Integer> studySchemaRevMap = defaultSchemaRevisionMap.get(study.getIdentifier());
            if (studySchemaRevMap != null) {
                schemaRev = studySchemaRevMap.get(item);
            }
        }

        if (schemaRev == null) {
            // fall back to revision 1
            schemaRev = 1;
        }

        // get schema
        return uploadSchemaService.getUploadSchemaByIdAndRev(study, item, schemaRev);
    }

    private static void validateInfoJsonFileList(UploadValidationContext context, String uploadId,
            Map<String, File> unzippedDataFileMap, JsonNode infoJson,
            HealthDataRecord record) {
        // Make sure all files specified by info.json are accounted for.
        Set<String> fileNameSet = new HashSet<>(unzippedDataFileMap.keySet());

        JsonNode fileList = infoJson.get(KEY_FILES);
        if (fileList == null) {
            // Recover by replacing this with an empty list
            context.addMessage(String.format("upload ID %s info.json does not contain file list", uploadId));
            fileList = BridgeObjectMapper.get().createArrayNode();
        } else if (fileList.size() == 0) {
            context.addMessage(String.format("upload ID %s info.json contains empty file list", uploadId));
        }

        DateTime createdOnFromFileList = null;
        for (JsonNode oneFileJson : fileList) {
            // validate filename
            JsonNode filenameNode = oneFileJson.get(KEY_FILENAME);
            String filename = null;
            if (filenameNode == null) {
                context.addMessage(String.format("upload ID %s info.json contains file with no name",
                        uploadId));
            } else {
                filename = filenameNode.textValue();
                if (!fileNameSet.contains(filename)) {
                    context.addMessage(String.format(
                            "upload ID %s info.json contains filename %s, not found in the archive", uploadId,
                            filename));
                }
            }

            // Calculate createdOn timestamp. Each file in the file list has its own timestamp. Canonical createdOn is
            // the latest of these timestamps.
            JsonNode timestampNode = oneFileJson.get(KEY_TIMESTAMP);
            if (timestampNode == null) {
                context.addMessage(String.format("upload ID %s filename %s has no timestamp", uploadId,
                        filename));
            } else {
                DateTime timestamp = UploadUtil.parseIosTimestamp(timestampNode.textValue());
                //noinspection ConstantConditions
                if (createdOnFromFileList == null || timestamp.isAfter(createdOnFromFileList)) {
                    createdOnFromFileList = timestamp;
                }
            }
        }

        // While we're at it, calculate createdOn. createdOn in info.json takes top priority, then fall back to file
        // list, then fall back to current time.
        String createdOnFromInfoJsonString = JsonUtils.asText(infoJson, UploadUtil.FIELD_CREATED_ON);
        DateTime createdOnFromInfoJson = null;
        if (StringUtils.isNotBlank(createdOnFromInfoJsonString)) {
            try {
                createdOnFromInfoJson = DateTime.parse(createdOnFromInfoJsonString);
            } catch (IllegalArgumentException ex) {
                // Write a message to the validation context, but there's no need to log or throw.
                context.addMessage("info.json.createdOn is invalid: " + createdOnFromInfoJsonString);
            }
        }

        if (createdOnFromInfoJson != null) {
            record.setCreatedOn(createdOnFromInfoJson.getMillis());
            record.setCreatedOnTimeZone(HealthDataRecord.TIME_ZONE_FORMATTER.print(createdOnFromInfoJson));
        } else if (createdOnFromFileList != null) {
            record.setCreatedOn(createdOnFromFileList.getMillis());
            record.setCreatedOnTimeZone(HealthDataRecord.TIME_ZONE_FORMATTER.print(createdOnFromFileList));
        } else {
            // Recover by using current time. Don't set a timezone, since it's indeterminate.
            context.addMessage(String.format("upload ID %s has no timestamps, using current time", uploadId));
            record.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        }
    }

    private static <T> void removeTimestampsFromFilenames(Map<String, T> fileMap) {
        // Sometimes filenames include timestamps. This breaks parsing, since we use filenames as key prefixes.
        // Normalize the filenames by removing timestamps. Assume any string of 8 or more digit is a timestamps. Also
        // remove the dash at the start of a timestamp. Filenames are generally unique even without timestamps, so we
        // don't have to worry about duplicate filenames.

        // Make a copy of the file map key set. This way, we can iterate over the filenames and modify the map without
        // hitting concurrent modification exceptions.
        ImmutableSet<String> filenameSet = ImmutableSet.copyOf(fileMap.keySet());
        for (String oneFilename : filenameSet) {
            Matcher filenameMatcher = FILENAME_TIMESTAMP_PATTERN.matcher(oneFilename);
            if (filenameMatcher.find()) {
                T fileData = fileMap.remove(oneFilename);
                String newFilename = filenameMatcher.replaceAll("");
                fileMap.put(newFilename, fileData);
            }
        }
    }

    private Map<String, JsonNode> convertSurveyToJsonData(UploadValidationContext context, String uploadId,
            Map<String, File> unzippedDataFileMap) {
        // Currently, the 3rd party iOS apps don't tag surveys or questions with guids. (In fact, some of the
        // surveys aren't even in the Surveys table yet.) So we have to store them in Health Data Records instead of
        // Survey Responses.

        // copy fields to "non-survey" format
        Map<String, JsonNode> convertedSurveyMap = new HashMap<>();
        for (Map.Entry<String, File> oneFileEntry : unzippedDataFileMap.entrySet()) {
            String filename = oneFileEntry.getKey();
            File file = oneFileEntry.getValue();
            if (UploadUtil.FILENAME_INFO_JSON.equals(filename) || UploadUtil.FILENAME_METADATA_JSON.equals(filename)) {
                // Skip info.json and metadata.json. We don't need to add a message, since this is normal.
                continue;
            }

            // Parse file into JSON nodes. Survey answer files should be very small. If they exceed the size, log a
            // warning and skip.
            long fileSize = fileHelper.fileSize(file);
            if (fileSize > UploadUtil.FILE_SIZE_LIMIT_SURVEY_ANSWER) {
                logger.warn("Survey file exceeds max size, uploadId=" + uploadId + ", filename=" + filename +
                        ", fileSize=" + fileSize + " bytes");
                continue;
            }
            JsonNode oneAnswerNode;
            try (InputStream fileInputStream = fileHelper.getInputStream(file)) {
                oneAnswerNode = BridgeObjectMapper.get().readTree(fileInputStream);
            } catch (IOException ex) {
                context.addMessage("Error parsing survey file, uploadId=" + uploadId + ", filename=" + filename);
                continue;
            }

            if (oneAnswerNode == null || oneAnswerNode.isNull()) {
                context.addMessage(String.format("Upload ID %s file %s is null", uploadId, filename));
                continue;
            }

            // question name ("item")
            JsonNode answerItemNode = oneAnswerNode.get("item");
            if (answerItemNode == null || answerItemNode.isNull()) {
                context.addMessage(String.format("Upload ID %s file %s has no question name (item)", uploadId,
                        filename));
                continue;
            }
            String answerItem = answerItemNode.textValue();
            if (StringUtils.isBlank(answerItem)) {
                context.addMessage(String.format("Upload ID %s file %s has blank question name(item)", uploadId,
                        filename));
                continue;
            }

            // question type
            JsonNode questionTypeNameNode = oneAnswerNode.get("questionTypeName");
            if (questionTypeNameNode == null || questionTypeNameNode.isNull()) {
                // fall back to questionType
                questionTypeNameNode = oneAnswerNode.get("questionType");
            }
            if (questionTypeNameNode == null || questionTypeNameNode.isNull()) {
                context.addMessage(String.format("Upload ID %s file %s has no question type", uploadId, filename));
                continue;
            }
            String questionTypeName = questionTypeNameNode.textValue();
            if (StringUtils.isBlank(questionTypeName)) {
                context.addMessage(String.format("Upload ID %s file %s has blank question type", uploadId, filename));
                continue;
            }

            // answer
            String answerKey = SURVEY_TYPE_TO_ANSWER_KEY_MAP.get(questionTypeName);
            if (answerKey == null) {
                context.addMessage(String.format("Upload ID %s file %s has unknown question type %s", uploadId,
                        filename, questionTypeName));
                continue;
            }
            JsonNode answerAnswerNode = oneAnswerNode.get(answerKey);
            if (answerAnswerNode != null && !answerAnswerNode.isNull()) {
                // Don't worry about attachment types for now. First, convert it to JSON data format. Then,
                // handleData() will take care of uploading attachments.
                convertedSurveyMap.put(answerItem, answerAnswerNode);
            }

            // if there's a unit, add it as well
            JsonNode unitNode = oneAnswerNode.get("unit");
            if (unitNode != null && !unitNode.isNull()) {
                convertedSurveyMap.put(answerItem + UploadUtil.UNIT_FIELD_SUFFIX, unitNode);
            }
        }

        // New survey format is to have key-value pairs in a top-level field called "answers". For backwards
        // compatibility, we'll do both. Add all the JsonNodes to a separate ObjectNode, then add the ObjectNode back
        // to the survey map.
        ObjectNode answersNode = BridgeObjectMapper.get().createObjectNode();
        convertedSurveyMap.forEach(answersNode::set);
        convertedSurveyMap.put(UploadUtil.FIELD_ANSWERS, answersNode);

        return convertedSurveyMap;
    }

    private void handleData(UploadValidationContext context, String uploadId,
            Map<String, JsonNode> surveyAnswerMap, Map<String, File> unzippedDataFileMap, UploadSchema schema,
            ObjectNode dataMap) throws UploadValidationException {
        Map<String, File> sanitizedUnzippedDataFileMap = UploadUtil.sanitizeFieldNames(unzippedDataFileMap);
        Map<String, Map<String, JsonNode>> parsedSanitizedJsonFileCache = new HashMap<>();

        // Using schema, copy fields over to data map. Or if it's an attachment, add it to the attachment map.
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();
            JsonNode fieldNode;

            if (surveyAnswerMap.containsKey(fieldName)) {
                // The field has already been parsed as a survey.
                JsonNode surveyAnswerNode = surveyAnswerMap.get(fieldName);

                if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                    // Attachments in a survey. This is unusual, but there's nothing in our schema system that prevents
                    // this. We should handle it just to be safe.
                    fieldNode = uploadFileHelper.uploadJsonNodeAsAttachment(surveyAnswerNode, uploadId, fieldName);
                } else {
                    fieldNode = surveyAnswerNode;
                }
            } else {
                fieldNode = uploadFileHelper.findValueForField(uploadId, sanitizedUnzippedDataFileMap, oneFieldDef,
                        parsedSanitizedJsonFileCache);
            }

            // Copy the field to the record.
            copyJsonField(context, uploadId, fieldNode, oneFieldDef, dataMap);
        }
    }

    private static void copyJsonField(UploadValidationContext context, String uploadId, JsonNode fieldValue,
            UploadFieldDefinition fieldDef, ObjectNode dataMap) {
        String fieldName = fieldDef.getName();
        if (fieldValue == null || fieldValue.isNull()) {
            return;
        }

        if (fieldDef.getType().equals(UploadFieldType.CALENDAR_DATE)) {
            // Older iOS apps submit a timestamp instead of a calendar date. Use this hack to convert it back.
            String dateStr = fieldValue.textValue();
            LocalDate parsedDate = UploadUtil.parseIosCalendarDate(dateStr);
            if (parsedDate != null) {
                dataMap.put(fieldName, parsedDate.toString());
            } else {
                String warnMsg = "Upload ID " + uploadId + " field " + fieldName + " has invalid calendar date " +
                        dateStr;
                logger.warn(warnMsg);
                context.addMessage(warnMsg);
            }
        } else if (fieldDef.getType().equals(UploadFieldType.STRING) && !fieldValue.isTextual()) {
            // Research Kit "helpfully" converts strings that look like ints into actual ints (example: "80" into 80).
            // This breaks Strict Validation later down the line, so we need to un-convert them back strings.
            // Note that we do it here, as this is an iOS-specific behavior, rather than in StrictValidation, which is
            // intended to be more global.
            dataMap.put(fieldName, fieldValue.toString());
        } else {
            dataMap.set(fieldName, fieldValue);
        }
    }
}
