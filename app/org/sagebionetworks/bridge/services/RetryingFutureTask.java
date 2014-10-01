package org.sagebionetworks.bridge.services;

import java.util.concurrent.Callable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Twitter commons code:
// http://twitter.github.io/commons/apidocs/src-html/com/twitter/common/util/concurrent/RetryingFutureTask.html
public class RetryingFutureTask extends FutureTask<Boolean> {
    
    private static Logger logger = LoggerFactory.getLogger(RetryingFutureTask.class);

    protected final ExecutorService executor;
    protected final int maxRetries;
    protected int numRetries;
    protected final Callable<Boolean> callable;

    public RetryingFutureTask(ExecutorService executor, Callable<Boolean> callable, int maxRetries) {
        super(callable);
        this.callable = callable;
        this.executor = executor;
        this.maxRetries = maxRetries;
    }

    protected void retry() {
        executor.execute(this);
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            success = callable.call();
        } catch (Exception e) {
            logger.warn("Exception while executing task.", e);
        }
        if (!success) {
            numRetries++;
            if (numRetries > maxRetries) {
                logger.error("Task did not complete after " + maxRetries + " retries, giving up.");
            } else {
                logger.error("Task was not successful, resubmitting (num retries: " + numRetries + ")");
                try {
                    long interval = (long)(3 * (numRetries ^ 2));
                    Thread.sleep(interval);
                    retry();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            set(true);
        }
    }

}
