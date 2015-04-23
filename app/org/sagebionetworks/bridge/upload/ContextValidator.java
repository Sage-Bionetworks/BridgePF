package org.sagebionetworks.bridge.upload;

/**
 * This interface is used with the TestingHandler to validate the production context against the (cloned) testing
 * context. This exists as an interface since different TestingHandlers might want different validation logic.
 * Subclasses should log a warning if validation fails.
 */
public interface ContextValidator {
    /**
     * Validates the production context against the test context. Subclasses should log a warning if validation fails.
     *
     * @param productionContext
     *         upload validation context used by the production handler, affects the production output stream
     * @param testContext
     *         upload validation context used by the test context, will be thrown away after the TestHandler
     */
    void validate(UploadValidationContext productionContext, UploadValidationContext testContext);
}
