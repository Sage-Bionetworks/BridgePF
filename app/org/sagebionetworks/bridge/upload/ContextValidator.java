package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

/**
 * This interface is used with the TestingHandler to validate the production context against the (cloned) testing
 * context. This exists as an interface since different TestingHandlers might want different validation logic.
 * Subclasses should throw an UploadValidationException if validation fails. This will be automatically logged by the
 * TestingHandler.
 */
public interface ContextValidator {
    /**
     * Validates the production context against the test context. Subclasses should throw an UploadValidationException
     * if validation fails.
     *
     * @param productionContext
     *         upload validation context used by the production handler, affects the production output stream
     * @param testContext
     *         upload validation context used by the test context, will be thrown away after the TestHandler
     * @throws UploadValidationException
     *         if validation fails
     */
    void validate(@Nonnull UploadValidationContext productionContext, @Nonnull UploadValidationContext testContext)
            throws UploadValidationException;
}
