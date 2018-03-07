package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadValidationTaskFactoryTest {
    private static final String HEALTH_CODE = "health-code";

    @Test
    public void test() {
        // test dao and handlers
        List<UploadValidationHandler> handlerList = Collections.emptyList();
        UploadDao dao = mock(UploadDao.class);
        FileHelper fileHelper = new FileHelper();
        HealthDataService healthDataService = new HealthDataService();

        // set up task factory
        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setFileHelper(fileHelper);
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(dao);
        taskFactory.setHealthDataService(healthDataService);

        // inputs
        Study study = TestUtils.getValidStudy(UploadValidationTaskFactoryTest.class);
        Upload upload = Upload.create();
        upload.setHealthCode(HEALTH_CODE);

        // execute and validate
        UploadValidationTask task = taskFactory.newTask(study, upload);
        assertEquals(HEALTH_CODE, task.getContext().getHealthCode());
        assertSame(study, task.getContext().getStudy());
        assertSame(upload, task.getContext().getUpload());

        assertSame(fileHelper, task.getFileHelper());
        assertSame(handlerList, task.getHandlerList());
        assertSame(dao, task.getUploadDao());
        assertSame(healthDataService, task.getHealthDataService());
    }
}
