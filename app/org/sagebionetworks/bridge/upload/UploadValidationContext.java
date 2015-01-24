package org.sagebionetworks.bridge.upload;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadValidationContext {
    private Study study;
    private Upload upload;
    private boolean success = true;
    private List<String> messageList = new ArrayList<>();
    private byte[] data;

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getMessageList() {
        return ImmutableList.copyOf(messageList);
    }

    public void addMessage(String msg) {
        messageList.add(msg);
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
