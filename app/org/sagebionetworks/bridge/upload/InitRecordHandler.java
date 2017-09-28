package org.sagebionetworks.bridge.upload;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;

/**
 * <p>
 * This handler initializes the Health Data Record and fills it in with various attributes, including userMetadata
 * (read from {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getJsonDataMap} and a blank JsonNode. It
 * sets this record to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setHealthDataRecord}.
 * </p>
 * <p>
 * This handler also initializes an empty attachment map and sets it to
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setAttachmentsByFieldName}
 * </p>
 */
@Component
public class InitRecordHandler implements UploadValidationHandler {
    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        Map<String, JsonNode> jsonDataMap = context.getJsonDataMap();
        Upload upload = context.getUpload();
        String uploadId = upload.getUploadId();

        // Add empty record and attachment map to the context. We'll fill these in as we need them.
        HealthDataRecord record = HealthDataRecord.create();
        context.setHealthDataRecord(record);
        context.setAttachmentsByFieldName(new HashMap<>());

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
        JsonNode infoJson = jsonDataMap.get(UploadUtil.FILENAME_INFO_JSON);
        if (infoJson == null || infoJson.isNull()) {
            // info.json is required. If it doesn't exist, fast-fail now.
            throw new UploadValidationException("upload ID " + uploadId + " does not contain info.json file");
        }
        record.setAppVersion(JsonUtils.asText(infoJson, UploadUtil.FIELD_APP_VERSION));
        record.setPhoneInfo(JsonUtils.asText(infoJson, UploadUtil.FIELD_PHONE_INFO));
        record.setMetadata(infoJson);

        // Copy metadata.json to record.userMetadata. (The names are due to an old feature conflicting with the name of
        // a new feature.) Lightly validate that metadata.json is a JSON object. BridgeEX will handle the rest.
        JsonNode metadataJson = jsonDataMap.get(UploadUtil.FILENAME_METADATA_JSON);
        if (metadataJson != null && !metadataJson.isNull()) {
            if (metadataJson.isObject()) {
                record.setUserMetadata(metadataJson);
            } else {
                context.addMessage("upload " + uploadId + " contains metadata.json, but it is not a JSON object");
            }
        }
    }
}
