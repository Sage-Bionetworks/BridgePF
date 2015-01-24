package org.sagebionetworks.bridge.upload;

import javax.annotation.Resource;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

@Component
public class UploadValidationTaskFactory {
    private UploadDao uploadDao;
    private List<UploadValidationHandler> handlerList;

    @Autowired
    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    @Resource(name = "UploadValidationHandlerList")
    public void setHandlerList(List<UploadValidationHandler> handlerList) {
        this.handlerList = handlerList;
    }

    public UploadValidationTask newTask(Study study, Upload upload) {
        // context
        UploadValidationContext context = new UploadValidationContext();
        context.setStudy(study);
        context.setUpload(upload);

        UploadValidationTask task = new UploadValidationTask(context);
        task.setUploadDao(uploadDao);
        task.setHandlerList(handlerList);
        return task;
    }
}
