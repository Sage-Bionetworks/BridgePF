package org.sagebionetworks.bridge.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

/** This class encapsulates data read and generated during the process of upload validation. */
public class UploadValidationContext {
    private StudyIdentifier study;
    private Upload upload;
    private boolean success = true;
    private List<String> messageList = new ArrayList<>();
    private byte[] data;
    private byte[] decryptedData;
    private Map<String, byte[]> unzippedDataMap;
    private Map<String, JsonNode> jsonDataMap;
    private HealthDataRecordBuilder healthDataRecordBuilder;
    private Map<String, byte[]> attachmentsByFieldName;
    private String recordId;

    /**
     * This is the study that the upload lives in and is validated against. This is made available by the upload
     * validation service and is initially set by the upload validation task factory.
     */
    public StudyIdentifier getStudy() {
        return study;
    }

    /** @see #getStudy */
    public void setStudy(StudyIdentifier study) {
        this.study = study;
    }

    /**
     * This is the upload metadata object of the upload we're validating. This is made available by the upload
     * validation service and is initially set by the upload validation task factory.
     */
    public Upload getUpload() {
        return upload;
    }

    /** @see #getUpload */
    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    /**
     * True if the validation is successful so far. False if validation has failed. This is initially set to true, as
     * validation tasks start off vacuously successful until they have failed. Once a validation handler has failed,
     * the error handling code in UploadValidationTask will flip the success flag to false. Only UploadValidationTask
     * will write to this field.
     */
    public boolean getSuccess() {
        return success;
    }

    /** @see #getSuccess */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Validation messages for this task, such as error messages. This is initially empty, and messages can be appended
     * by calling {@link #addMessage}. Messages are generally added by the error handling code in UploadValidationTask,
     * but validation handlers can add messages for other reasons.
     */
    public List<String> getMessageList() {
        return ImmutableList.copyOf(messageList);
    }

    /** @see #getMessageList */
    public void addMessage(String msg) {
        messageList.add(msg);
    }

    /** Raw upload data as bytes. This is created by S3DownloadHandler and read by the DecryptHandler. */
    public byte[] getData() {
        return data;
    }

    /** @see #getData */
    public void setData(byte[] data) {
        this.data = data;
    }

    /** Decrypted upload data as bytes. This is created by DecryptHandler and read by UnzipHandler. */
    public byte[] getDecryptedData() {
        return decryptedData;
    }

    /** @see #getDecryptedData */
    public void setDecryptedData(byte[] decryptedData) {
        this.decryptedData = decryptedData;
    }

    /**
     * Unzipped data as bytes, keyed by filename. This is initially created by the UnzipHandler. The ParseJsonHandler
     * will read this and remove entries that can be parsed into JSON. Non-JSON entries will still remain in this map.
     * This is also read by the IosSchemaValidationHandler.
     */
    public Map<String, byte[]> getUnzippedDataMap() {
        return unzippedDataMap;
    }

    /** @see #getUnzippedDataMap */
    public void setUnzippedDataMap(Map<String, byte[]> unzippedDataMap) {
        this.unzippedDataMap = unzippedDataMap;
    }

    /**
     * Parsed JSON data, keyed by filename. This is created by the ParseJsonHandler and read by the
     * IosSchemaValidationHandler.
     */
    public Map<String, JsonNode> getJsonDataMap() {
        return jsonDataMap;
    }

    /** @see #getJsonDataMap */
    public void setJsonDataMap(Map<String, JsonNode> jsonDataMap) {
        this.jsonDataMap = jsonDataMap;
    }

    /**
     * Health Data Record Builder, used to build a health data record that will be written to the health data record
     * table. This is initially created by IosSchemaValidationHandler, is further updated by the
     * TranscribeConsentHandler, and is finalized and persisted by UploadArtifactsHandler.
     */
    public HealthDataRecordBuilder getHealthDataRecordBuilder() {
        return healthDataRecordBuilder;
    }

    /** @see #getHealthDataRecordBuilder */
    public void setHealthDataRecordBuilder(HealthDataRecordBuilder healthDataRecordBuilder) {
        this.healthDataRecordBuilder = healthDataRecordBuilder;
    }

    /**
     * Map of health data attachments, keyed off the field name in the health data record. These files will be uploaded
     * to external storage (most likely S3) with metadata stored in Health Data Attachments table and field references
     * in the health data record. This is created by IosSchemaValidationHandler and is uploaded by
     * UploadArtifactsHandler.
     */
    public Map<String, byte[]> getAttachmentsByFieldName() {
        return attachmentsByFieldName;
    }

    /** @see #getAttachmentsByFieldName */
    public void setAttachmentsByFieldName(Map<String, byte[]> attachmentsByFieldName) {
        this.attachmentsByFieldName = attachmentsByFieldName;
    }

    /** ID of the health data record created from the upload. This is created by the UploadArtifactsHandler. */
    public String getRecordId() {
        return recordId;
    }

    /** @see #getRecordId */
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /**
     * <p>
     * Makes a shallow copy of this object. The fields of the returned copy can be get and set without affecting the
     * original. However, the field value themselves are shared between the original and the copy. Most notably,
     * modifying the unzippedDataMap (which is what ParseJsonHandler does) or the healthDataRecordBuilder (which is
     * what TranscribeConsentHandler does) in the copy will affect the original, and vice versa.
     * </p>
     * <p>
     * The one notable exception is the message list, which will be deep copied. This is because the message list is
     * always treated as mutable and any handler may write to it.
     * </p>
     * <p>
     * This is most useful for testing new and old versions of handlers, provided that handlers either treat the field
     * values as immutable or make deep copies of the fields they modify.
     * </p>
     */
    public UploadValidationContext shallowCopy() {
        UploadValidationContext copy = new UploadValidationContext();
        copy.study = this.study;
        copy.upload = this.upload;
        copy.success = this.success;
        copy.data = this.data;
        copy.decryptedData = this.decryptedData;
        copy.unzippedDataMap = this.unzippedDataMap;
        copy.jsonDataMap = this.jsonDataMap;
        copy.healthDataRecordBuilder = this.healthDataRecordBuilder;
        copy.attachmentsByFieldName = this.attachmentsByFieldName;
        copy.recordId = this.recordId;

        // messageList is the only field that gets deep copied
        copy.messageList = new ArrayList<>(this.messageList);

        return copy;
    }
}
