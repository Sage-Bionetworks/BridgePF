package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDao;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadValidationTaskFactoryTest {
    private static final String HEALTH_CODE = "health-code";

    @Test
    public void test() {
        // test dao and handlers
        List<UploadValidationHandler> handlerList = Collections.emptyList();
        DynamoUploadDao dao = new DynamoUploadDao();
        HealthDataService healthDataService = new HealthDataService();

        // set up task factory
        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(dao);
        taskFactory.setHealthDataService(healthDataService);

        // inputs
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskFactoryTest.class);
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setHealthCode(HEALTH_CODE);

        // execute and validate
        UploadValidationTask task = taskFactory.newTask(study, upload2);
        assertEquals(HEALTH_CODE, task.getContext().getHealthCode());
        assertSame(study, task.getContext().getStudy());
        assertSame(upload2, task.getContext().getUpload());
        assertSame(handlerList, task.getHandlerList());
        assertSame(dao, task.getUploadDao());
        assertSame(healthDataService, task.getHealthDataService());
    }
}
