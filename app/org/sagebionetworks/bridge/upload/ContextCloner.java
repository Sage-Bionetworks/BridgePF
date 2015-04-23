package org.sagebionetworks.bridge.upload;

/**
 * This interface is used with the TestingHandler to clone the upload validation context for use in the test handler.
 * This exists as an interface since different TestingHandlers might want different cloning logic.
 */
public interface ContextCloner {
    /**
     * Clones the production context. Subclasses can implement cloning logic specific to the needs of their tests.
     *
     * @param productionContext
     *         production context to clone from
     * @return cloned test context
     */
    UploadValidationContext clone(UploadValidationContext productionContext);
}
