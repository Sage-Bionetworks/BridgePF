package org.sagebionetworks.bridge.upload;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

/** This class encapsulates data read and generated during the process of upload validation. */
public class UploadValidationContext {
    private Study study;
    private Upload upload;
    private boolean success = true;
    private List<String> messageList = new ArrayList<>();
    private byte[] data;

    /**
     * This is the study that the upload lives in and is validated against. This is made available by the upload
     * validation service and is initially set by the upload validation task factory.
     */
    public Study getStudy() {
        return study;
    }

    /** @see #getStudy */
    public void setStudy(Study study) {
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

    /**
     * Raw upload data as bytes. This is set by the S3DownloadHandler.
     */
    public byte[] getData() {
        return data;
    }

    /** @see #getData */
    public void setData(byte[] data) {
        this.data = data;
    }
}
