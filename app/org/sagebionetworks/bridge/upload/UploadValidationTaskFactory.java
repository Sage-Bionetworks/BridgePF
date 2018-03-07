package org.sagebionetworks.bridge.upload;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class creates UploadValidationTask objects. It exists primarily so that we can avoid using context.getBean(),
 * which forces unit tests to be Spring aware. With a factory instead of Spring context.getBean(), we can significantly
 * simplify unit tests.
 */
@Component
public class UploadValidationTaskFactory {
    private FileHelper fileHelper;
    private List<UploadValidationHandler> handlerList;
    private UploadDao uploadDao;
    private HealthDataService healthDataService;

    /** File helper, used to create and delete the temp directory in which we process uploads. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** Validation handler list. This is configured by Spring. */
    @Resource(name = "uploadValidationHandlerList")
    public final void setHandlerList(List<UploadValidationHandler> handlerList) {
        this.handlerList = handlerList;
    }

    /** Upload DAO, used to write validation status. This is configured by Spring. */
    @Autowired
    public final void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    @Autowired
    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /**
     * Factory method for creating a validation task instance, for validating a single upload.
     *
     * @param study
     *         study this upload lives in
     * @param upload
     *         upload metadata object for the upload
     * @return upload validation task, which will validate the upload
     */
    public UploadValidationTask newTask(@Nonnull StudyIdentifier study, @Nonnull Upload upload) {
        // context
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthCode(upload.getHealthCode());
        context.setStudy(study);
        context.setUpload(upload);

        // task
        UploadValidationTask task = new UploadValidationTask(context);
        task.setFileHelper(fileHelper);
        task.setHandlerList(handlerList);
        task.setUploadDao(uploadDao);
        task.setHealthDataService(healthDataService);
        return task;
    }
}
