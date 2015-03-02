package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDao;
import org.sagebionetworks.bridge.models.User;

public class UploadValidationTaskFactoryTest {
    @Test
    public void test() {
        // test dao and handlers
        List<UploadValidationHandler> handlerList = Collections.emptyList();
        DynamoUploadDao dao = new DynamoUploadDao();

        // set up task factory
        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(dao);

        // inputs
        DynamoStudy study = new DynamoStudy();
        DynamoUpload2 upload2 = new DynamoUpload2();
        User user = new User();

        // execute and validate
        UploadValidationTask task = taskFactory.newTask(study, user, upload2);
        assertSame(study, task.getContext().getStudy());
        assertSame(upload2, task.getContext().getUpload());
        assertSame(handlerList, task.getHandlerList());
        assertSame(dao, task.getUploadDao());
    }
}
