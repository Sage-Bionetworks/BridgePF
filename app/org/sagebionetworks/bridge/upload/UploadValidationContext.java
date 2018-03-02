package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

/** This class encapsulates data read and generated during the process of upload validation. */
public class UploadValidationContext {
    private String healthCode;
    private StudyIdentifier study;
    private Upload upload;
    private boolean success = true;
    private List<String> messageList = new ArrayList<>();
    private File tempDir;
    private File dataFile;
    private File decryptedDataFile;
    private Map<String, File> unzippedDataFileMap;
    private JsonNode infoJsonNode;
    private HealthDataRecord healthDataRecord;
    private String recordId;

    /** Health code of the user contributing the health data. */
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

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
     * Helper method which returns the upload ID. Returns null if there is no upload. This is generally used of the
     * health data is created through the synchronous API instead of the upload API.
     */
    public String getUploadId() {
        return upload != null ? upload.getUploadId() : null;
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

    /** Temporary directory in which we process the upload. */
    public File getTempDir() {
        return tempDir;
    }

    /** @see #getTempDir */
    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    /** Raw upload data file. This is created by S3DownloadHandler and read by the DecryptHandler. */
    public File getDataFile() {
        return dataFile;
    }

    /** @see #getDataFile */
    public void setDataFile(File dataFile) {
        this.dataFile = dataFile;
    }

    /** Decrypted upload data file. This is created by DecryptHandler and read by UnzipHandler. */
    public File getDecryptedDataFile() {
        return decryptedDataFile;
    }

    /** @see #getDecryptedDataFile */
    public void setDecryptedDataFile(File decryptedDataFile) {
        this.decryptedDataFile = decryptedDataFile;
    }

    /**
     * Unzipped data files, keyed by filename. This is created by the UnzipHandler and read by InitRecordHandler, as
     * well as UploadFormatHandler, and its inner handlers.
     */
    public Map<String, File> getUnzippedDataFileMap() {
        return unzippedDataFileMap;
    }

    /** @see #getUnzippedDataFileMap */
    public void setUnzippedDataFileMap(Map<String, File> unzippedDataFileMap) {
        this.unzippedDataFileMap = unzippedDataFileMap;
    }

    /**
     * info.json is a special file, which we parse and place here. This is created by InitRecordHandler and read by
     * UploadFormatHandler and its inner handlers.
     */
    public JsonNode getInfoJsonNode() {
        return infoJsonNode;
    }

    /** @see #getInfoJsonNode */
    public void setInfoJsonNode(JsonNode infoJsonNode) {
        this.infoJsonNode = infoJsonNode;
    }

    /**
     * Health Data Record, created from the uploaded data. This is initially created by InitRecordHandler, is further
     * updated by UploadFormatHandler and its inner handlers and by TranscribeConsentHandler, and is finalized and
     * persisted by UploadArtifactsHandler.
     */
    public HealthDataRecord getHealthDataRecord() {
        return healthDataRecord;
    }

    /** @see #getHealthDataRecord */
    public void setHealthDataRecord(HealthDataRecord healthDataRecord) {
        this.healthDataRecord = healthDataRecord;
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
     * modifying the unzippedDataMap (which is what ParseJsonHandler does) or the healthDataRecord (which is
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
        copy.healthCode = this.healthCode;
        copy.study = this.study;
        copy.upload = this.upload;
        copy.success = this.success;
        copy.tempDir = this.tempDir;
        copy.dataFile = this.dataFile;
        copy.decryptedDataFile = this.decryptedDataFile;
        copy.unzippedDataFileMap = this.unzippedDataFileMap;
        copy.infoJsonNode = this.infoJsonNode;
        copy.healthDataRecord = this.healthDataRecord;
        copy.recordId = this.recordId;

        // messageList is the only field that gets deep copied
        copy.messageList = new ArrayList<>(this.messageList);

        return copy;
    }
}
