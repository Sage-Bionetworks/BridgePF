package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;

/**
 * This handler initializes the Health Data Record and fills it in with various attributes, including userMetadata
 * (read from {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataFileMap} and a blank JsonNode. It
 * sets this record to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setHealthDataRecord}.
 */
@Component
public class InitRecordHandler implements UploadValidationHandler {
    private FileHelper fileHelper;

    /** File helper, used to check the existence of info,json. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        Map<String, File> unzippedDataFileMap = context.getUnzippedDataFileMap();
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();

        // Add empty record to the context. We'll fill these in as we need them.
        HealthDataRecord record = HealthDataRecord.create();
        context.setHealthDataRecord(record);

        // health data records fields
        record.setHealthCode(upload.getHealthCode());
        record.setStudyId(context.getStudy().getIdentifier());
        // TODO: If we globalize Bridge, we'll need to make this timezone configurable.
        record.setUploadDate(LocalDate.now(BridgeConstants.LOCAL_TIME_ZONE));
        record.setUploadId(uploadId);
        record.setUploadedOn(DateUtils.getCurrentMillisFromEpoch());

        // create an empty object node in our record builder, which we'll fill in as we go
        ObjectNode dataMap = BridgeObjectMapper.get().createObjectNode();
        record.setData(dataMap);

        // Transcribe data from info.json. appVersion and phoneInfo are top-level attributes. For backwards
        // compatibility, metadata is info.json verbatim.
        JsonNode infoJson = parseFileAsJson(uploadId, unzippedDataFileMap, UploadUtil.FILENAME_INFO_JSON);
        record.setAppVersion(JsonUtils.asText(infoJson, UploadUtil.FIELD_APP_VERSION));
        record.setPhoneInfo(JsonUtils.asText(infoJson, UploadUtil.FIELD_PHONE_INFO));
        record.setMetadata(infoJson);

        // Add info.json to the context, since we reference it a lot.
        context.setInfoJsonNode(infoJson);

        // Copy metadata.json to record.userMetadata. (The names are due to an old feature conflicting with the name of
        // a new feature.) Lightly validate that metadata.json is a JSON object. BridgeEX will handle the rest.
        JsonNode metadataJson = parseFileAsJson(uploadId, unzippedDataFileMap, UploadUtil.FILENAME_METADATA_JSON);
        if (metadataJson.isObject()) {
            record.setUserMetadata(metadataJson);
        } else {
            context.addMessage("upload " + uploadId + " contains metadata.json, but it is not a JSON object");
        }
    }

    // todo doc
    // todo Always returns a non-null JsonNode
    public JsonNode parseFileAsJson(String uploadId, Map<String, File> fileMap, String filename)
            throws UploadValidationException {
        File file = fileMap.get(filename);
        if (file == null || !fileHelper.fileExists(file)) {
            throw new UploadValidationException("Upload ID " + uploadId + " must contain file " + filename);
        }

        JsonNode jsonNode;
        try (InputStream fileInputStream = fileHelper.getInputStream(file)) {
            jsonNode = BridgeObjectMapper.get().readTree(fileInputStream);
        } catch (IOException ex) {
            throw new UploadValidationException("Couldn't parse " + filename + " for upload " + uploadId);
        }

        if (jsonNode == null || jsonNode.isNull()) {
            // info.json is required. If it doesn't exist, fast-fail now.
            throw new UploadValidationException("Upload ID " + uploadId + " contains null value for file " + filename);
        }

        return jsonNode;
    }
}
