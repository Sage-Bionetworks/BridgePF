package org.sagebionetworks.bridge.upload;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class UploadValidationTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(UploadValidationTask.class);

    private final UploadValidationContext context;

    private List<UploadValidationHandler> handlerList;
    private UploadDao uploadDao;

    /* package-scoped */ UploadValidationTask(UploadValidationContext context) {
        this.context = context;
    }

    public void setHandlerList(List<UploadValidationHandler> handlerList) {
        this.handlerList = handlerList;
    }

    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    @Override
    public void run() {
        for (UploadValidationHandler oneHandler : handlerList) {
            try {
                oneHandler.handle(context);
            } catch (UploadValidationException ex) {
                // unwrap to find cause, if any
                Throwable inner = ex.getCause() != null ? ex.getCause() : ex;

                // log stuff, set the success flag, and break out of the loop
                String handlerName = oneHandler.getClass().getName();
                context.setSuccess(false);
                context.addMessage(String.format("Error running upload validation handler %s: %s", handlerName,
                        inner.getMessage()));
                LOG.warn(String.format("Error running upload validation handler %s for study %s, upload %s",
                        handlerName, context.getStudy().getIdentifier(), context.getUpload().getUploadId()), inner);
                break;
            }
        }

        // write validation status to the upload DAO
        UploadStatus status = context.getSuccess() ? UploadStatus.SUCCEEDED : UploadStatus.VALIDATION_FAILED;
        uploadDao.writeValidationStatus(context.getUpload(), status, context.getMessageList());
        LOG.info(String.format("Upload validation for study %s, upload %s, with status %s",
                context.getStudy().getIdentifier(), context.getUpload().getUploadId(), status));

        // TODO: if validation fails, wipe the files from S3
    }
}
