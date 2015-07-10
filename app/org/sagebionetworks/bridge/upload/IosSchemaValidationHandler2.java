package org.sagebionetworks.bridge.upload;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Processes iOS data into health data records. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataMap} and
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getJsonDataMap} and writes to
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setHealthDataRecordBuilder} and
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setAttachmentsByFieldName}.
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
    private static final Set<UploadFieldType> ATTACHMENT_TYPE_SET = EnumSet.of(UploadFieldType.ATTACHMENT_BLOB,
            UploadFieldType.ATTACHMENT_CSV, UploadFieldType.ATTACHMENT_JSON_BLOB,
            UploadFieldType.ATTACHMENT_JSON_TABLE);

    private static final String FILENAME_INFO_JSON = "info.json";
    private static final Pattern FILENAME_TIMESTAMP_PATTERN = Pattern.compile("-\\d{8,}");
    private static final String KEY_FILENAME = "filename";
    private static final String KEY_FILES = "files";
    private static final String KEY_IDENTIFIER = "identifier";
    private static final String KEY_ITEM = "item";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final Map<String, String> SURVEY_TYPE_TO_ANSWER_KEY_MAP = ImmutableMap.<String, String>builder()
            .put("Boolean", "booleanAnswer")
            .put("Date", "dateAnswer")
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

    private HealthDataDao healthDataDao;
    private UploadSchemaService uploadSchemaService;

    /** Health Data DAO, used solely to get the record builder. This is configured by Spring. */
    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /** Upload Schema Service, used to get the schema corresponding to the upload. This is configured by Spring. */
    @Autowired
    public void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
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
        Map<String, JsonNode> jsonDataMap = context.getJsonDataMap();
        Map<String, byte[]> unzippedDataMap = context.getUnzippedDataMap();
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();
        StudyIdentifier study = context.getStudy();
        String studyId = study.getIdentifier();

        // Add empty record builder and attachment map to the context. We'll fill these in as we need them.
        HealthDataRecordBuilder recordBuilder = healthDataDao.getRecordBuilder();
        context.setHealthDataRecordBuilder(recordBuilder);
        Map<String, byte[]> attachmentMap = new HashMap<>();
        context.setAttachmentsByFieldName(attachmentMap);

        // health data records fields
        recordBuilder.withHealthCode(upload.getHealthCode());
        recordBuilder.withStudyId(studyId);
        // TODO: If we globalize Bridge, we'll need to make this timezone configurable.
        recordBuilder.withUploadDate(LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE));
        recordBuilder.withUploadId(uploadId);

        // create an empty object node in our record builder, which we'll fill in as we go
        ObjectNode dataMap = BridgeObjectMapper.get().createObjectNode();
        recordBuilder.withData(dataMap);

        // Use info.json verbatim is the metadata.
        JsonNode infoJson = getInfoJsonFile(context, uploadId, jsonDataMap);
        recordBuilder.withMetadata(infoJson);

        // validate and normalize filenames
        validateInfoJsonFileList(context, uploadId, jsonDataMap, unzippedDataMap, infoJson, recordBuilder);
        removeTimestampsFromFilenames(jsonDataMap);
        removeTimestampsFromFilenames(unzippedDataMap);

        // extract other data from info.json
        JsonNode itemNode = infoJson.get(KEY_ITEM);
        if (itemNode == null) {
            // fall back to "identifier"
            itemNode = infoJson.get(KEY_IDENTIFIER);
        }
        if (itemNode == null || itemNode.isNull()) {
            throw new UploadValidationException("info.json is missing \"item\" field");
        }
        String item = itemNode.textValue();
        if (StringUtils.isBlank(item)) {
            throw new UploadValidationException("info.json \"item\" field is blank");
        }

        // schema
        // TODO: once the apps are including schema revision in the upload, we can get the schema by revision too
        UploadSchema schema;
        try {
            schema = uploadSchemaService.getUploadSchema(study, item);
        } catch (InvalidEntityException ex) {
            throw new UploadValidationException(String.format("Schema %s not found", item), ex);
        }
        recordBuilder.withSchemaId(schema.getSchemaId());
        recordBuilder.withSchemaRevision(schema.getRevision());

        UploadSchemaType schemaType = schema.getSchemaType();
        if (schemaType == UploadSchemaType.IOS_SURVEY) {
            // Convert survey format to JSON data format. This means creating a JSON data map where the "filenames" are
            // just the question names (items) and the file data is the answer JSON node.
            Map<String, JsonNode> convertedSurveyMap = convertSurveyToJsonData(context, uploadId, jsonDataMap);
            handleData(context, uploadId, convertedSurveyMap, unzippedDataMap, schema, dataMap, attachmentMap);
        } else if (schemaType == UploadSchemaType.IOS_DATA) {
            handleData(context, uploadId, jsonDataMap, unzippedDataMap, schema, dataMap, attachmentMap);
        } else {
            throw new UploadValidationException(String.format("Invalid schema type %s", schemaType));
        }
    }

    private static JsonNode getInfoJsonFile(UploadValidationContext context, String uploadId,
            Map<String, JsonNode> jsonDataMap) {
        JsonNode infoJson = jsonDataMap.get(FILENAME_INFO_JSON);
        if (infoJson == null) {
            // Recover by replacing this with an empty map
            context.addMessage(String.format("upload ID %s does not contain info.json file", uploadId));
            infoJson = BridgeObjectMapper.get().createObjectNode();

            // Add it back to the jsonDataMap, since all the logic assumes it contains info.json.
            jsonDataMap.put(FILENAME_INFO_JSON, infoJson);
        }
        return infoJson;
    }

    private static void validateInfoJsonFileList(UploadValidationContext context, String uploadId,
            Map<String, JsonNode> jsonDataMap, Map<String, byte[]> unzippedDataMap, JsonNode infoJson,
            HealthDataRecordBuilder recordBuilder) {
        // Make sure all files specified by info.json are accounted for.
        // Because ParseJsonHandler moves files from unzippedDataMap to jsonDataMap, there is no overlap between the
        // two maps.
        Set<String> fileNameSet = new HashSet<>();
        fileNameSet.addAll(jsonDataMap.keySet());
        fileNameSet.addAll(unzippedDataMap.keySet());

        // fileList.size() should be exactly 1 less than fileNameSet.size(), because fileList.size() doesn't include
        // info.json.
        JsonNode fileList = infoJson.get(KEY_FILES);
        if (fileList == null) {
            // Recover by replacing this with an empty list
            context.addMessage(String.format("upload ID %s info.json does not contain file list", uploadId));
            fileList = BridgeObjectMapper.get().createArrayNode();
        } else if (fileList.size() == 0) {
            context.addMessage(String.format("upload ID %s info.json contains empty file list", uploadId));
        } else if (fileList.size() != fileNameSet.size() - 1) {
            context.addMessage(String.format("upload ID %s info.json reports %d files, but we found %d files",
                    uploadId, fileList.size(), fileNameSet.size() - 1));
        }

        DateTime createdOn = null;
        Map<String, JsonNode> infoJsonFilesByName = new HashMap<>();
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
                infoJsonFilesByName.put(filename, oneFileJson);
            }

            // Calculate createdOn timestamp. Each file in the file list has its own timestamp. Canonical createdOn is
            // the latest of these timestamps.
            JsonNode timestampNode = oneFileJson.get(KEY_TIMESTAMP);
            if (timestampNode == null) {
                context.addMessage(String.format("upload ID %s filename %s has no timestamp", uploadId,
                        filename));
            } else {
                DateTime timestamp = parseTimestampHelper(context, uploadId, filename, timestampNode.textValue());
                if (createdOn == null || timestamp.isAfter(createdOn)) {
                    createdOn = timestamp;
                }
            }
        }

        // sanity check filenames with the info.json file list
        for (String oneFilename : fileNameSet) {
            if (!oneFilename.equals(FILENAME_INFO_JSON) && !infoJsonFilesByName.containsKey(oneFilename)) {
                context.addMessage(String.format(
                        "upload ID %s contains filename %s not found in info.json", uploadId, oneFilename));
            }
        }

        if (createdOn == null) {
            // Recover by using current time.
            context.addMessage(String.format("upload ID %s has no timestamps, using current time", uploadId));
            createdOn = DateUtils.getCurrentDateTime();
        }
        recordBuilder.withCreatedOn(createdOn.getMillis());
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

    private static Map<String, JsonNode> convertSurveyToJsonData(UploadValidationContext context, String uploadId,
            Map<String, JsonNode> jsonDataMap) {
        // Currently, the 3rd party iOS apps don't tag surveys or questions with guids. (In fact, some of the
        // surveys aren't even in the Surveys table yet.) So we have to store them in Health Data Records instead of
        // Survey Responses.

        // copy fields to "non-survey" format
        Map<String, JsonNode> convertedSurveyMap = new HashMap<>();
        for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
            String filename = oneJsonFile.getKey();
            if (FILENAME_INFO_JSON.equals(filename)) {
                // Skip info.json. We don't need to add a message, since this is normal.
                continue;
            }

            JsonNode oneAnswerNode = oneJsonFile.getValue();
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
                convertedSurveyMap.put(answerItem + "_unit", unitNode);
            }
        }

        return convertedSurveyMap;
    }

    // Note that handleSurvey() converts the survey format into the data format, treating each answer as its own file
    // with filename equal to the question name and JsonNode equal to the answer.
    private static void handleData(UploadValidationContext context, String uploadId,
            Map<String, JsonNode> jsonDataMap, Map<String, byte[]> unzippedDataMap, UploadSchema schema,
            ObjectNode dataMap, Map<String, byte[]> attachmentMap) {
        // Get flattened JSON data map (key is filename.fieldname), because schemas can reference fields either by
        // filename.fieldname or wholly by filename.
        Map<String, JsonNode> flattenedJsonDataMap = flattenJsonDataMap(jsonDataMap);

        Set<String> nonJsonFilenameSet = unzippedDataMap.keySet();
        Set<String> jsonFilenameSet = jsonDataMap.keySet();
        Set<String> jsonFieldnameSet = flattenedJsonDataMap.keySet();

        // Using schema, copy fields over to data map. Or if it's an attachment, add it to the attachment map.
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();

            if (nonJsonFilenameSet.contains(fieldName)) {
                attachmentMap.put(fieldName, unzippedDataMap.get(fieldName));
            } else if (jsonFilenameSet.contains(fieldName)) {
                copyJsonField(context, uploadId, jsonDataMap.get(fieldName), oneFieldDef, dataMap, attachmentMap);
            } else if (jsonFieldnameSet.contains(fieldName)) {
                copyJsonField(context, uploadId, flattenedJsonDataMap.get(fieldName), oneFieldDef, dataMap,
                        attachmentMap);
            } else if (oneFieldDef.isRequired()) {
                // log a message only if the field is required (but missing)
                context.addMessage(String.format("Upload ID %s is missing required field %s", uploadId, fieldName));
            }
        }
    }

    private static Map<String, JsonNode> flattenJsonDataMap(Map<String, JsonNode> jsonDataMap) {
        Map<String, JsonNode> dataFieldMap = new HashMap<>();
        for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
            String filename = oneJsonFile.getKey();
            if (filename.equals(FILENAME_INFO_JSON)) {
                // Not info.json. Skip.
                continue;
            }

            JsonNode oneJsonFileNode = oneJsonFile.getValue();
            Iterator<String> fieldNameIter = oneJsonFileNode.fieldNames();
            while (fieldNameIter.hasNext()) {
                // Pre-pend file name with field name, so if there are duplicate filenames, they get disambiguated.
                String oneFieldName = fieldNameIter.next();
                dataFieldMap.put(filename + "." + oneFieldName, oneJsonFileNode.get(oneFieldName));
            }
        }

        return dataFieldMap;
    }

    private static void copyJsonField(UploadValidationContext context, String uploadId, JsonNode fieldValue,
            UploadFieldDefinition fieldDef, ObjectNode dataMap, Map<String, byte[]> attachmentMap) {
        String fieldName = fieldDef.getName();
        if (fieldValue == null || fieldValue.isNull()) {
            context.addMessage(String.format("Upload ID %s field %s is null", uploadId, fieldName));
            return;
        }

        if (ATTACHMENT_TYPE_SET.contains(fieldDef.getType())) {
            try {
                attachmentMap.put(fieldName, BridgeObjectMapper.get().writeValueAsBytes(fieldValue));
            } catch (JsonProcessingException ex) {
                context.addMessage(String.format(
                        "Upload ID %s field %s could not be converted from JSON: %s", uploadId, fieldName,
                        ex.getMessage()));
            }
        } else {
            dataMap.set(fieldName, fieldValue);
        }
    }

    // For some reason, the iOS apps are sending timestamps in form "YYYY-MM-DD hh:mm:ss +ZZZZ", which is
    // non-ISO-compliant and can't be parsed by JodaTime. We'll need to convert these to ISO format, generally
    // "YYYY-MM-DDThh:mm:ss+ZZZZ".
    // TODO: Remove this hack when it's no longer needed.
    private static DateTime parseTimestampHelper(UploadValidationContext context, String uploadId, String filename,
            String timestampStr) {
        if (StringUtils.isBlank(timestampStr)) {
            context.addMessage(String.format("upload ID %s filename %s has blank time stamp", uploadId,
                    filename));
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            context.addMessage(String.format("upload ID %s filename %s has non-standard timestamp format %s",
                    uploadId, filename, timestampStr));

            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (RuntimeException ex) {
            context.addMessage(String.format("upload ID %s filename %s has invalid timestamp %s", uploadId,
                    filename, timestampStr));
            return null;
        }
    }
}
