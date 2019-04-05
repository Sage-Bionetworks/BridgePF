package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.schema.SchemaUtils;

/**
 * Utility helper class for uploads, which wraps FileHelper and S3Helper and handles various tasks of parsing files and
 * uploading attachments.
 */
@Component
public class UploadFileHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UploadFileHelper.class);

    // Package-scoped for unit tests.
    static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");

    private FileHelper fileHelper;
    private int inlineFileSizeLimit = UploadUtil.FILE_SIZE_LIMIT_INLINE_FIELD;
    private int parsedJsonFileSizeLimit = UploadUtil.FILE_SIZE_LIMIT_PARSED_JSON;
    private int parsedJsonWarningLimit = UploadUtil.WARNING_LIMIT_PARSED_JSON;
    private S3Helper s3Helper;

    /** File helper, used to check file sizes before parsing them into memory. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** Sets the file size limit for inline files. This setter is to allow unit tests to override. */
    final void setInlineFileSizeLimit(@SuppressWarnings("SameParameterValue") int inlineFileSizeLimit) {
        this.inlineFileSizeLimit = inlineFileSizeLimit;
    }

    /** Sets the file size limit for parsed JSON files. This setter is to allow unit tests to override. */
    final void setParsedJsonFileSizeLimit(@SuppressWarnings("SameParameterValue") int parsedJsonFileSizeLimit) {
        this.parsedJsonFileSizeLimit = parsedJsonFileSizeLimit;
    }

    /**
     * Sets the threshold on parsed JSON files before we log a warning. This setter is to allow unit tests to override.
     */
    final void setParsedJsonWarningLimit(@SuppressWarnings("SameParameterValue") int parsedJsonWarningLimit) {
        this.parsedJsonWarningLimit = parsedJsonWarningLimit;
    }

    /** S3 Helper, used to upload attachments. */
    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    /**
     * Given some upload parameters and a list of files, find the value that matches the given upload schema field. The
     * field definition could refer to a file, or it can refer to the top-level key within a JSON file.
     *
     * @param uploadId
     *         upload ID, used for logging and to generate attachment IDs
     * @param sanitizedUnzippedDataFileMap
     *         map of upload files by name; the file names should be sanitized
     * @param fieldDef
     *         field definition to find the value for
     * @param parsedSanitizedJsonFileCache
     *         a cache of parsed sanitized JSON nodes, so that we don't have to parse and sanitize any JSON file more
     *         than once; the caller should initially pass in an empty writable map and reuse the same map for
     *         subsequent calls in a single upload
     * @return the JSON node that matches field, or a TextNode with the attachment ID if it's an attachment
     * @throws UploadValidationException
     *         if parsing JSON files or uploading attachments fails
     */
    public JsonNode findValueForField(String uploadId, Map<String, File> sanitizedUnzippedDataFileMap,
            UploadFieldDefinition fieldDef, Map<String, Map<String, JsonNode>> parsedSanitizedJsonFileCache)
            throws UploadValidationException {
        String fieldName = fieldDef.getName();
        boolean isAttachment = UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldDef.getType());
        JsonNode fieldNode;

        if (sanitizedUnzippedDataFileMap.containsKey(fieldName)) {
            // Case 1: The field refers to the whole file.
            File fieldFile = sanitizedUnzippedDataFileMap.get(fieldName);

            if (isAttachment) {
                if (fileHelper.fileSize(fieldFile) != 0) {
                    // Case 1a: The whole file is an attachment. Upload the file. Field JSON is attachment filename.
                    String attachmentFilename = uploadId + '-' + fieldName;
                    fieldNode = TextNode.valueOf(attachmentFilename);
                    
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
                    s3Helper.writeFileToS3(ATTACHMENT_BUCKET, attachmentFilename, fieldFile, metadata);
                } else {
                    // Case 1b: The file is an empty attachment. Skip and return null.
                    fieldNode = null;
                }
            } else {
                // Case 1c: The whole file is an inline JSON field.

                // DynamoDB has a row size limit of 400kb. To make sure we don't go over, we have a limit of 10kb per
                // field.
                long fieldFileSize = fileHelper.fileSize(fieldFile);
                if (fieldFileSize > inlineFileSizeLimit) {
                    LOG.warn("Inline field file exceeds max size, uploadId=" + uploadId + ", fieldname=" +
                            fieldName + ", fileSize=" + fieldFileSize + " bytes");
                    fieldNode = null;
                } else {
                    // Parse field from file.
                    try (InputStream fileInputStream = fileHelper.getInputStream(fieldFile)) {
                        fieldNode = BridgeObjectMapper.get().readTree(fileInputStream);
                    } catch (IOException ex) {
                        throw new UploadValidationException("Error parsing field file, uploadId=" + uploadId +
                                ", fieldName=" + fieldName, ex);
                    }
                }
            }
        } else {
            // Case 2: The field refers to a top-level JSON key in one of the files. The fieldName will have one of
            // the file names as a prefix. Iterate the file map to find that prefix. (Note: If the schema is
            // ambiguously defined, there may be more than one. Hopefully, this never happens in the real world.)
            JsonNode foundValue = null;
            for (Map.Entry<String, File> oneFileEntry : sanitizedUnzippedDataFileMap.entrySet()) {
                String parsedFilename = oneFileEntry.getKey();
                if (!fieldName.startsWith(parsedFilename)) {
                    // Not this file. Skip.
                    continue;
                }

                Map<String, JsonNode> sanitizedParsedJsonNodeMap;
                if (parsedSanitizedJsonFileCache.containsKey(parsedFilename)) {
                    sanitizedParsedJsonNodeMap = parsedSanitizedJsonFileCache.get(parsedFilename);
                } else {
                    // We don't want to load large files into memory, for obvious reasons. Because of the way old
                    // studies are set up, we sometimes end up parsing very large files into Bridge anyway. For now,
                    // warn if the size is >5mb, and skip if the size >20mb.
                    File parsedFile = oneFileEntry.getValue();
                    long parsedFileSize = fileHelper.fileSize(parsedFile);
                    if (parsedFileSize > parsedJsonFileSizeLimit) {
                        LOG.warn("Parsed JSON file exceeds max size, uploadId=" + uploadId + ", filename=" +
                                parsedFilename + ", fileSize=" + parsedFileSize + " bytes");
                        continue;
                    } else if (parsedFileSize > parsedJsonWarningLimit) {
                        LOG.warn("Parsed JSON file exceeds warning threshold, uploadId=" + uploadId + ", filename=" +
                                parsedFilename + ", fileSize=" + parsedFileSize + " bytes");
                    }

                    // Parse file and get the top level key that matches.
                    JsonNode parsedJsonNode;
                    try (InputStream parsedFileInputStream = fileHelper.getInputStream(parsedFile)) {
                        parsedJsonNode = BridgeObjectMapper.get().readTree(parsedFileInputStream);
                    } catch (IOException ex) {
                        // Assume we have the wrong file. Log a warning and proceed.
                        LOG.warn("Error parsing JSON file, uploadId=" + uploadId + ", fileName=" + parsedFilename);
                        continue;
                    }

                    // Sanitize the top-level key names.
                    sanitizedParsedJsonNodeMap = new HashMap<>();
                    parsedJsonNode.fields().forEachRemaining(
                            jsonEntry -> {
                                String sanitizedKey = SchemaUtils.sanitizeFieldName(jsonEntry.getKey());
                                sanitizedParsedJsonNodeMap.put(sanitizedKey, jsonEntry.getValue());
                            });

                    // Add the parsed sanitized JSON to the cache, so we don't have to parse it again.
                    parsedSanitizedJsonFileCache.put(parsedFilename, sanitizedParsedJsonNodeMap);
                }

                // Determine the top-level key name and see if that key exists. Add one to the substring start
                // index because the fieldName is "[fileName].[keyName]";
                String keyName = fieldName.substring(parsedFilename.length() + 1);
                if (sanitizedParsedJsonNodeMap.containsKey(keyName)) {
                    foundValue = sanitizedParsedJsonNodeMap.get(keyName);
                    break;
                }
            }

            if (foundValue == null) {
                // Case 2a: We couldn't find any value. Just set fieldNode to null.
                fieldNode = null;
            } else if (isAttachment) {
                // Case 2b: This is an attachment. Write the found value as bytes and upload it.
                fieldNode = uploadJsonNodeAsAttachment(foundValue, uploadId, fieldName);
            } else {
                // Case 2c: Not an attachment. The field value is just the value we found.
                fieldNode = foundValue;
            }
        }

        return fieldNode;
    }

    /**
     * Uploads a JSON node as an upload attachment, then returns a JsonNode containing the attachment's filename in S3,
     * ready for use in a health data record.
     */
    public JsonNode uploadJsonNodeAsAttachment(JsonNode node, String uploadId, String fieldName)
            throws UploadValidationException {
        String filename = uploadId + '-' + fieldName;
        String jsonText = node.toString();
        
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        try {
            s3Helper.writeBytesToS3(ATTACHMENT_BUCKET, filename, jsonText.getBytes(Charsets.UTF_8), metadata);
        } catch (IOException ex) {
            throw new UploadValidationException("Error writing attachment to S3, uploadId=" + uploadId +
                    ", fieldName=" + fieldName, ex);
        }
        return TextNode.valueOf(filename);
    }
}
