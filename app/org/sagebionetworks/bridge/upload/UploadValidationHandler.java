package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

/**
 * <p>
 * This interface represents a handler for a sub-task in upload validation. This allows us to break down our back-end
 * dependencies individually into validation handlers. It also allows us to configure our handler chain in Spring
 * configuration instead of in code.
 * </p>
 * <p>
 * Over time, handlers will write additional data to the UploadValidationContext. Implementing handlers should clearly
 * document which data they read and write from the validation context.
 * </p>
 */
public interface UploadValidationHandler {
    /**
     * Invoke this handle to perform its validation sub-task on the given validation context.
     *
     * @param context
     *         Upload validation context. This method will read from and write to the validation context. See
     *         specific sub-classes for more details on which values are read and written
     * @throws UploadValidationException
     *         if upload validation fails
     */
    void handle(@Nonnull UploadValidationContext context) throws UploadValidationException;
}
