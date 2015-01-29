package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.upload.UploadValidationTask;
import org.sagebionetworks.bridge.upload.UploadValidationTaskFactory;

public class UploadValidationServiceTest {
    @Test
    public void test() {
        // UploadValidationService is a simple call-through to the task factory and the async thread pool. As such, our
        // test strategy is to verify that execution flows through to these dependencies.

        // inputs
        Study study = new DynamoStudy();
        Upload upload = new DynamoUpload2();

        // mock task
        UploadValidationTask mockTask = mock(UploadValidationTask.class);

        // mock task factory
        UploadValidationTaskFactory mockTaskFactory = mock(UploadValidationTaskFactory.class);
        when(mockTaskFactory.newTask(study, upload)).thenReturn(mockTask);

        // mock async thread pool
        ExecutorService mockExecutor = mock(ExecutorService.class);

        // set up service
        UploadValidationService svc = new UploadValidationService();
        svc.setAsyncExecutorService(mockExecutor);
        svc.setTaskFactory(mockTaskFactory);

        // execute
        svc.validateUpload(study, upload);

        // validate
        verify(mockExecutor).execute(mockTask);
    }
}
