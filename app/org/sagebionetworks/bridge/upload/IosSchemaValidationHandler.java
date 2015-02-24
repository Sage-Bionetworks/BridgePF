package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

// TODO: Currently, all apps are iOS-based. However, when we start having non-iOS apps, we'll need to restructure this
// so that it only runs in the iOS context.
@Component
public class IosSchemaValidationHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(IosSchemaValidationHandler.class);

    private static final Set<UploadFieldType> ATTACHMENT_TYPE_SET = EnumSet.of(UploadFieldType.ATTACHMENT_BLOB,
            UploadFieldType.ATTACHMENT_CSV, UploadFieldType.ATTACHMENT_JSON_BLOB,
            UploadFieldType.ATTACHMENT_JSON_TABLE);

    // Note: some surveys have both questionType and questionTypeName. Some surveys only have questionType as a string.
    // To maximize compatibility, we only check for questionType.
    private static final Set<String> SURVEY_KEY_SET = ImmutableSet.of("endDate", "item", "questionType", "startDate");

    private HealthDataDao healthDataDao;
    private UploadSchemaService uploadSchemaService;

    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    @Autowired
    public void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    // iOS data comes from a third party, and we have no control over the data format. So our data validation needs to
    // be as flexible as possible. Which means our error handling strategy is to write a warning to the logs, and then
    // attempt to recover. This will, however, cause cascading errors further down the validation chain.
    @Override
    public void handle(@Nonnull UploadValidationContext context)
            throws UploadValidationException {
        Map<String, JsonNode> jsonDataMap = context.getJsonDataMap();
        Map<String, byte[]> unzippedDataMap = context.getUnzippedDataMap();
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();
        Study study = context.getStudy();
        String studyId = study.getIdentifier();

        // Add empty record builder and attachment map to the context. We'll fill these in as we need them.
        HealthDataRecordBuilder recordBuilder = healthDataDao.getRecordBuilder();
        context.setHealthDataRecordBuilder(recordBuilder);
        Map<String, byte[]> attachmentMap = new HashMap<>();
        context.setAttachmentsByFieldName(attachmentMap);

        // health data records fields
        recordBuilder.withHealthCode(upload.getHealthCode());
        recordBuilder.withStudyId(studyId);
        recordBuilder.withUploadDate(upload.getUploadDate());

        // create an empty object node in our record builder, which we'll fill in as we go
        ObjectNode dataMap = BridgeObjectMapper.get().createObjectNode();
        recordBuilder.withData(dataMap);

        JsonNode infoJson = jsonDataMap.get("info.json");
        if (infoJson == null) {
            // Recover by replacing this with an empty map
            addMessageAndWarn(context, String.format("upload ID %s does not contain info.json file", uploadId));
            infoJson = BridgeObjectMapper.get().createObjectNode();

            // Add it back to the jsonDataMap, since all the logic assumes it contains info.json.
            jsonDataMap.put("info.json", infoJson);
        }

        // Use info.json verbatim is the metadata.
        recordBuilder.withMetadata(infoJson);

        // extract other data from info.json
        JsonNode taskRunNode = infoJson.get("taskRun");
        String taskRunId = taskRunNode != null ? taskRunNode.textValue() : null;
        JsonNode itemNode = infoJson.get("item");
        String item = itemNode != null ? itemNode.textValue() : null;
        
        // Make sure all files specified by info.json are accounted for.
        // Because ParseJsonHandler moves files from unzippedDataMap to jsonDataMap, there is no overlap between the
        // two maps.
        Set<String> fileNameSet = new HashSet<>();
        fileNameSet.addAll(jsonDataMap.keySet());
        fileNameSet.addAll(unzippedDataMap.keySet());

        // fileList.size() should be exactly 1 less than fileNameSet.size(), because fileList.size() doesn't include
        // info.json.
        JsonNode fileList = infoJson.get("files");
        if (fileList == null) {
            // Recover by replacing this with an empty list
            addMessageAndWarn(context,
                    String.format("upload ID %s info.json does not contain file list", uploadId));
            fileList = BridgeObjectMapper.get().createArrayNode();
        } else if (fileList.size() == 0) {
            addMessageAndWarn(context, String.format("upload ID %s info.json contains empty file list", uploadId));
        } else if (fileList.size() != fileNameSet.size() - 1) {
            addMessageAndWarn(context, String.format("upload ID %s info.json reports %d files, but we found %d files",
                    uploadId, fileList.size(), fileNameSet.size() - 1));
        }

        DateTime createdOn = null;
        Map<String, JsonNode> infoJsonFilesByName = new HashMap<>();
        for (JsonNode oneFileJson : fileList) {
            // validate filename
            JsonNode filenameNode = oneFileJson.get("filename");
            String filename = null;
            if (filenameNode == null) {
                addMessageAndWarn(context, String.format("upload ID %s info.json contains file with no name",
                        uploadId));
            } else {
                filename = filenameNode.textValue();
                if (!fileNameSet.contains(filename)) {
                    addMessageAndWarn(context, String.format(
                            "upload ID %s info.json contains filename %s, not found in the archive", uploadId,
                            filename));
                }
                infoJsonFilesByName.put(filename, oneFileJson);
            }

            // Calculate createdOn timestamp. Each file in the file list has its own timestamp. Canonical createdOn is
            // the latest of these timestamps.
            JsonNode timestampNode = oneFileJson.get("timestamp");
            if (timestampNode == null) {
                addMessageAndWarn(context, String.format("upload ID %s filename %s has no timestamp", uploadId,
                        filename));
            } else {
                DateTime timestamp = parseTimestampHelper(context, uploadId, filename, timestampNode.textValue());
                if (createdOn == null || timestamp.isAfter(createdOn)) {
                    createdOn = timestamp;
                }
            }
        }

        if (createdOn == null) {
            // Recover by using current time.
            addMessageAndWarn(context, String.format("upload ID %s has no timestamps, using current time", uploadId));
            createdOn = DateUtils.getCurrentDateTime();
        }
        recordBuilder.withCreatedOn(createdOn.getMillis());

        // get schemas
        // TODO: cache this
        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId);

        if (jsonDataMap.size() == 1) {
            // This means the only JSON file we have is info.json. So we should expect exactly one non-JSON file to
            // upload with it.
            if (unzippedDataMap.isEmpty()) {
                // info.json with no actual data? We can't do anything with this. Game over.
                throw new UploadValidationException("No data files other than info.json");
            } else if (unzippedDataMap.size() > 1) {
                // Multiple data files? We don't have any way of distinguishing between two data files in the same
                // upload. Game over.
                throw new UploadValidationException(String.format("Multiple non-JSON files in upload: %s",
                        Joiner.on(", ").join(unzippedDataMap.keySet())));
            } else if (unzippedDataMap.size() == 1) {
                // Even though there's only 1 item in the unzippedDataMap, the only way to get it is to iterate
                // through it.
                String filename = null;
                byte[] fileData = null;
                for (Map.Entry<String, byte[]> oneFile : unzippedDataMap.entrySet()) {
                    filename = oneFile.getKey();
                    fileData = oneFile.getValue();
                }

                // sanity check with the info.json file list
                if (!infoJsonFilesByName.containsKey(filename)) {
                    addMessageAndWarn(context, String.format(
                            "upload ID %s contains filename %s not found in info.json", uploadId, filename));
                }

                // Attempting to parse into the non-JSON data is an exercise in madness. Our best strategy here is to
                // match the "item" field in info.json with one of the schema names, and pick the one with the latest
                // revision.
                if (StringUtils.isBlank(item)) {
                    // No "item" field means we have no one of identifying this. Game over.
                    throw new UploadValidationException(
                            "info.json in non-JSON upload has blank \"item\" field to identify the schema with.");
                }

                // Try to find the schema.
                UploadSchema latestSchema = null;
                for (UploadSchema oneSchema : schemaList) {
                    if (oneSchema.getName().equals(item)) {
                        if (latestSchema == null || oneSchema.getRevision() > latestSchema.getRevision()) {
                            latestSchema = oneSchema;
                        }
                    }
                }
                if (latestSchema == null) {
                    // No schema, no health data record. Game over.
                    throw new UploadValidationException(String.format("No schema found for item %s", item));
                }

                // We found the schema.
                String schemaId = latestSchema.getSchemaId();
                int schemaRev = latestSchema.getRevision();
                recordBuilder.withSchemaId(schemaId);
                recordBuilder.withSchemaRevision(schemaRev);

                // Schema should have a field that's in ATTACHMENT_TYPE_SET, to store the attachment ref in.
                List<UploadFieldDefinition> fieldDefList = latestSchema.getFieldDefinitions();
                if (fieldDefList.isEmpty()) {
                    // No fields at all? Game over.
                    throw new UploadValidationException(String.format("Identified schema ID %s rev %d has no fields",
                            schemaId, schemaRev));
                }
                if (fieldDefList.size() > 1) {
                    // We expect exactly one field (since we don't know how to fill in the other fields). But if
                    // there's more than one (as long as one of them is a ATTACHMENT_TYPE_SET), we should be okay.
                    addMessageAndWarn(context, String.format(
                            "upload ID %s filename %s identified schema ID %s rev %d has multiple fields", uploadId,
                            filename, schemaId, schemaRev));
                }

                UploadFieldDefinition attachmentFieldDef = null;
                for (UploadFieldDefinition oneFieldDef : fieldDefList) {
                    if (ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                        // just the first one
                        attachmentFieldDef = oneFieldDef;
                        break;
                    }
                }
                if (attachmentFieldDef == null) {
                    // We have no field to write this into. Game over.
                    throw new UploadValidationException(String.format(
                            "Identified schema ID %s rev %d has no field for non-JSON data", schemaId, schemaRev));
                }

                // Write this to the attachment map. UploadArtifactsHandler will take care of the rest.
                attachmentMap.put(attachmentFieldDef.getName(), fileData);
            }
        } else {
            // This means our data is in JSON format, so we can look inside it to figure out what it is.

            if (!unzippedDataMap.isEmpty()) {
                // We don't expect a mix of JSON and non-JSON, but we can just ignore the non-JSON data.
                addMessageAndWarn(context, String.format(
                         "upload ID %s contains both JSON data and non-JSON data; ignoring non-JSON data", uploadId));
            }

            // Check if it's a survey. We can tell if it's a survey by looking at one of the JSON files (other than
            // info.json) and looking for specific survey keys, as listed in SURVEY_KEY_SET.
            boolean isSurvey = false;
            for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
                if (oneJsonFile.getKey().equals("info.json")) {
                    // Not info.json. Skip.
                    continue;
                }

                // There may be fields beyond what's specified in SURVEY_KEY_SET. This is normal, since every question
                // type has its own special fields.
                JsonNode oneJsonFileNode = oneJsonFile.getValue();
                Set<String> fieldNameSet = ImmutableSet.copyOf(oneJsonFileNode.fieldNames());
                isSurvey = fieldNameSet.containsAll(SURVEY_KEY_SET);

                // We only need to look at one (other than info.json). Either they're all surveys, or none of them are.
                break;
            }

            if (isSurvey) {
                // Currently, the 3rd party iOS apps don't tag surveys or questions with guids. (In fact, some of the
                // surveys aren't even in the Surveys table yet.) So we can't store survey answers in the Survey
                // Responses table. Instead, let's take all the answers, create a big ATTACHMENT_JSON_TABLE out of
                // them.
                ArrayNode answerArray = BridgeObjectMapper.get().createArrayNode();
                for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
                    if (oneJsonFile.getKey().equals("info.json")) {
                        // Not info.json. Skip.
                        continue;
                    }

                    // add the JSON directly to our object. We're making a table.
                    answerArray.add(oneJsonFile.getValue());
                }

                // answers should be treated as an attachment
                try {
                    attachmentMap.put("answers", BridgeObjectMapper.get().writeValueAsBytes(answerArray));
                } catch (JsonProcessingException ex) {
                    addMessageAndWarn(context, String.format(
                            "Upload ID %s could not convert survey answers to JSON: %s", uploadId, ex.getMessage()));
                }

                // also, add item and taskRun to dataMap
                dataMap.put("item", item);
                dataMap.put("taskRunId", taskRunId);

                // get the survey schema
                // TODO: cache this
                UploadSchema surveySchema = uploadSchemaService.getUploadSchema(study, "ios-survey");
                recordBuilder.withSchemaId(surveySchema.getSchemaId());
                recordBuilder.withSchemaRevision(surveySchema.getRevision());
            } else {
                // JSON data may contain more than one JSON file. However, Health Data Records stores a single map.
                // Flatten all the JSON maps together (other than info.json). We may have duplicate keys. If there are,
                // remember those keys and log a warning afterwards.
                Set<String> keySet = new HashSet<>();
                Set<String> dupKeySet = new HashSet<>();
                Map<String, JsonNode> dataFieldMap = new HashMap<>();
                for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
                    if (oneJsonFile.getKey().equals("info.json")) {
                        // Not info.json. Skip.
                        continue;
                    }

                    JsonNode oneJsonFileNode = oneJsonFile.getValue();
                    Iterator<String> fieldNameIter = oneJsonFileNode.fieldNames();
                    while (fieldNameIter.hasNext()) {
                        String oneFieldName = fieldNameIter.next();

                        // dupe field check
                        if (keySet.contains(oneFieldName)) {
                            dupKeySet.add(oneFieldName);
                        } else {
                            keySet.add(oneFieldName);
                        }

                        // If it's a dupe, the last one wins, based on JsonNode.fieldNames() iterator.
                        dataFieldMap.put(oneFieldName, oneJsonFileNode.get(oneFieldName));
                    }
                }

                if (!dupKeySet.isEmpty()) {
                    addMessageAndWarn(context, String.format("Upload ID %s contains duplicate keys (%s)", uploadId,
                            Joiner.on(", ").join(dupKeySet)));
                }

                // cross-ref our data map with our schema list to see which schema matches
                UploadSchema latestSchema = null;
                for (UploadSchema oneSchema : schemaList) {
                    Set<String> schemaKeySet = new HashSet<>();

                    // strategy: assume the schema matches, unless we find a field that doesn't match
                    boolean isMatch = true;
                    for (UploadFieldDefinition oneFieldDef : oneSchema.getFieldDefinitions()) {
                        String fieldName = oneFieldDef.getName();
                        schemaKeySet.add(fieldName);

                        JsonNode fieldValue = dataFieldMap.get(fieldName);
                        if (fieldValue == null) {
                            if (oneFieldDef.isRequired()) {
                                // field is required but not present, not a match
                                isMatch = false;
                            }
                        } else {
                            switch (oneFieldDef.getType()) {
                                case ATTACHMENT_BLOB:
                                case ATTACHMENT_CSV:
                                    // Attachment blob and csv expect non-JSON data, and we don't mix JSON with
                                    // non-JSON data, so this is not a match.
                                    isMatch = false;
                                    break;
                                case ATTACHMENT_JSON_BLOB:
                                    // JSON blobs are always JSON blobs. We don't need to do any special validation.
                                    break;
                                case ATTACHMENT_JSON_TABLE:
                                    // Basic sanity check: the outermost layer of the blob should be an array.
                                    if (!fieldValue.isArray()) {
                                        isMatch = false;
                                    } else {
                                        // Basic sanity check 2: The first element of the array is an object node.
                                        JsonNode firstRow = fieldValue.get(0);
                                        if (firstRow == null || !firstRow.isObject()) {
                                            isMatch = false;
                                        }
                                    }
                                    break;
                                case BOOLEAN:
                                    isMatch = fieldValue.isBoolean();
                                    break;
                                case CALENDAR_DATE:
                                    // We expect a string. Also, the string should be parseable by Joda LocalDate.
                                    if (!fieldValue.isTextual()) {
                                        isMatch = false;
                                    } else {
                                        try {
                                            // DateUtils calls through to Joda parseLocalDate(), which is documented as
                                            // never returning null. So we don't need to null check here.
                                            DateUtils.parseCalendarDate(fieldValue.textValue());
                                        } catch (RuntimeException ex) {
                                            isMatch = false;
                                        }
                                    }
                                    break;
                                case FLOAT:
                                    // includes floats, doubles, and decimals
                                    isMatch = fieldValue.isFloatingPointNumber();
                                    break;
                                case INT:
                                    // includes ints, longs, and big ints
                                    isMatch = fieldValue.isIntegralNumber();
                                    break;
                                case STRING:
                                    isMatch = fieldValue.isTextual();
                                    break;
                                case TIMESTAMP:
                                    // either it's a string in ISO format, or it's a long in epoch milliseconds
                                    if (fieldValue.isTextual()) {
                                        try {
                                            DateTime dateTime = parseTimestampHelper(context, uploadId, null,
                                                    fieldValue.textValue());
                                            isMatch = (dateTime != null);
                                        } catch (RuntimeException ex) {
                                            isMatch = false;
                                        }
                                    } else if (fieldValue.isIntegralNumber()) {
                                        try {
                                            new DateTime(fieldValue.longValue());
                                        } catch (RuntimeException ex) {
                                            isMatch = false;
                                        }
                                    } else {
                                        isMatch = false;
                                    }
                                    break;
                                default:
                                    // This should never happen, but just in case we add a new field to UploadFieldType
                                    // but forget to upload this switch.
                                    isMatch = false;
                                    break;
                            }
                        }

                        if (!isMatch) {
                            // we already know it's not a match, so we can short-circuit
                            break;
                        }
                    }

                    if (isMatch && !schemaKeySet.containsAll(keySet)) {
                        // There are keys in the JSON that aren't present in the schema. This may refer to a different
                        // revision of the schema, or to a different schema entirely. Mark it as not a match.
                        // (Only check if we haven't already flagged it as non-match. Otherwise, this check may be
                        // meaningless.)
                        isMatch = false;
                    }

                    if (isMatch) {
                        // If we have more than one match, choose the one with the highest revision, as that's the one
                        // that's newest. (This assumes that while schema revisions may be the same, entirely different
                        // schemas will be different.(
                        if (latestSchema == null || oneSchema.getRevision() > latestSchema.getRevision()) {
                            latestSchema = oneSchema;
                        }
                    }
                }

                if (latestSchema == null) {
                    // No schema, no health data record. Game over.
                    throw new UploadValidationException(String.format("No schema found for keys (%s)",
                            Joiner.on(", ").join(keySet)));
                }

                // We found the schema.
                recordBuilder.withSchemaId(latestSchema.getSchemaId());
                recordBuilder.withSchemaRevision(latestSchema.getRevision());

                // Using schema, copy fields over to data map. Or if it's an attachment, add it to the attachment map.
                for (UploadFieldDefinition oneFieldDef : latestSchema.getFieldDefinitions()) {
                    String fieldName = oneFieldDef.getName();
                    JsonNode fieldValue = dataFieldMap.get(fieldName);

                    if (ATTACHMENT_TYPE_SET.contains(oneFieldDef.getType())) {
                        try {
                            attachmentMap.put(fieldName, BridgeObjectMapper.get().writeValueAsBytes(fieldValue));
                        } catch (JsonProcessingException ex) {
                            addMessageAndWarn(context, String.format(
                                    "Upload ID %s field %s could not be converted to JSON: %s", uploadId, fieldName,
                                    ex.getMessage()));
                        }
                    } else {
                        dataMap.set(fieldName, dataFieldMap.get(fieldName));
                    }
                }
            }
        }

        // debug messages to help debug
        if (logger.isDebugEnabled()) {
            try {
                String recordJson = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(
                        context.getHealthDataRecordBuilder().build());
                logger.debug(String.format("Health Data Record: %s", recordJson));
                logger.debug(String.format("Attachments: %s", Joiner.on(", ").join(attachmentMap.keySet())));
            } catch (JsonProcessingException ex) {
                logger.debug("Couldn't convert record builder into JSON", ex);
            }
        }
    }

    // For some reason, the iOS apps are sending timestamps in form "YYYY-MM-DD hh:mm:ss +ZZZZ", which is
    // non-ISO-compliant and can't be parsed by JodaTime. We'll need to convert these to ISO format, generally
    // "YYYY-MM-DDThh:mm:ss+ZZZZ".
    // TODO: Remove this hack when it's no longer needed.
    private static DateTime parseTimestampHelper(UploadValidationContext context, String uploadId, String filename,
            String timestampStr) {
        if (StringUtils.isBlank(timestampStr)) {
            addMessageAndWarn(context, String.format("upload ID %s filename %s has blank time stamp", uploadId,
                    filename));
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            addMessageAndWarn(context, String.format("upload ID %s filename %s has non-standard timestamp format %s",
                    uploadId, filename, timestampStr));

            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (RuntimeException ex) {
            addMessageAndWarn(context, String.format("upload ID %s filename %s has invalid timestamp %s", uploadId,
                    filename, timestampStr));
            return null;
        }
    }

    private static void addMessageAndWarn(UploadValidationContext context, String message) {
        context.addMessage(message);
        logger.warn(message);
    }
}
