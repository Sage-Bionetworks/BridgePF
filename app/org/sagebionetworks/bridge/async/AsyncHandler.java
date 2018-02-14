package org.sagebionetworks.bridge.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common async handler Runnable. Since Play framework doesn't hook up our async threads to the ExceptionInterceptor,
 * we use this class to ensure that errors in async threads are logged.
 */
public abstract class AsyncHandler implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncHandler.class);

    @Override
    public void run() {
        try {
            handle();
        } catch (Throwable t) {
            LOG.error("Error in " + this.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }

    /** Async threads should override this method with the logic they want to run asynchronously. */
    protected abstract void handle() throws Exception;
}
