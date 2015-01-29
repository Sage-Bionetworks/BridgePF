package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

/**
 * This class represents an asynchronous upload validation task, corresponding with exactly one upload. It implements
 * the Runnable interface, so we can run it as asynchronous code.
 */
public class UploadValidationTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(UploadValidationTask.class);

    private final UploadValidationContext context;

    private List<UploadValidationHandler> handlerList;
    private UploadDao uploadDao;

    /**
     * Constructs an upload validation task instance with the given context. This should only be called by the
     * factory, or by unit tests.
     * @param context context for this specific task, must be non-null
     */
    /* package-scoped */ UploadValidationTask(@Nonnull UploadValidationContext context) {
        this.context = context;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped */ UploadValidationContext getContext() {
        return context;
    }

    /** List of validation handlers. This is configured by Spring through the task factory. */
    public void setHandlerList(List<UploadValidationHandler> handlerList) {
        this.handlerList = handlerList;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped*/ List<UploadValidationHandler> getHandlerList() {
        return handlerList;
    }

    /** Upload DAO, for writing upload validation status. This is configured by Spring through the task factory. */
    public void setUploadDao(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /** This is package-scoped to facilitate unit tests. */
    /* package-scoped*/ UploadDao getUploadDao() {
        return uploadDao;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        for (UploadValidationHandler oneHandler : handlerList) {
            try {
                oneHandler.handle(context);
            } catch (RuntimeException | UploadValidationException ex) {
                String handlerName = oneHandler.getClass().getName();
                context.setSuccess(false);
                context.addMessage(String.format("Error running upload validation handler %s: %s", handlerName,
                        ex.getMessage()));
                logger.warn(String.format("Error running upload validation handler %s for study %s, upload %s",
                        handlerName, context.getStudy().getIdentifier(), context.getUpload().getUploadId()), ex);
                break;
            }
        }

        // write validation status to the upload DAO
        UploadStatus status = context.getSuccess() ? UploadStatus.SUCCEEDED : UploadStatus.VALIDATION_FAILED;
        uploadDao.writeValidationStatus(context.getUpload(), status, context.getMessageList());
        logger.info(String.format("Upload validation for study %s, upload %s, with status %s",
                context.getStudy().getIdentifier(), context.getUpload().getUploadId(), status));

        // TODO: if validation fails, wipe the files from S3
    }
}
