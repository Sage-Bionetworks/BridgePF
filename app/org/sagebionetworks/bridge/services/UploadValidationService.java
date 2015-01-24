package org.sagebionetworks.bridge.services;

import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.upload.UploadValidationTask;
import org.sagebionetworks.bridge.upload.UploadValidationTaskFactory;

@Component
public class UploadValidationService {
    private ExecutorService asyncExecutorService;
    private UploadValidationTaskFactory taskFactory;

    @Autowired
    public void setAsyncExecutorService(ExecutorService asyncExecutorService) {
        this.asyncExecutorService = asyncExecutorService;
    }

    @Autowired
    public void setTaskFactory(UploadValidationTaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public void validateUpload(Study study, Upload upload) {
        UploadValidationTask task = taskFactory.newTask(study, upload);
        asyncExecutorService.execute(task);
    }
}
