package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This handler is used to validate test code against production code. It takes in a production handler, a test
 * handler, and a validator. It runs the production handler against the real context, the test handler against a cloned
 * context, and then runs the validator to compare the two contexts. For caveats about cloned contexts, see
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#shallowCopy}.
 * </p>
 * <p>
 * This is useful for testing candidate changes against production using a real upload stream without impacting the
 * real upload stream.
 * </p>
 */
public class TestingHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(TestingHandler.class);

    private ContextValidator contextValidator;
    private UploadValidationHandler productionHandler;
    private UploadValidationHandler testHandler;

    /** Object to validate the production and test contexts. This is configured by Spring. */
    public void setContextValidator(ContextValidator contextValidator) {
        this.contextValidator = contextValidator;
    }

    /** Handler to use for real production data. This is configured by Spring. */
    public void setProductionHandler(UploadValidationHandler productionHandler) {
        this.productionHandler = productionHandler;
    }

    /** Handler to use on a cloned context, for test purposes. This is configured by Spring. */
    public void setTestHandler(UploadValidationHandler testHandler) {
        this.testHandler = testHandler;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext productionContext) throws UploadValidationException {
        // clone
        UploadValidationContext testContext = productionContext.shallowCopy();

        // production handler - This might throw. If it does, that's fine. It's not very useful to call the test
        // handler if the production handler would throw anyway.
        productionHandler.handle(productionContext);

        // test handler - If this fails, then the test handler has a problem that the production handler doesn't. This
        // is a test failure, and we should log a warning.
        try {
            testHandler.handle(testContext);
        } catch (RuntimeException | UploadValidationException ex) {
            logger.warn(String.format(
                    "Test handler %s failed for study %s, upload %s, filename %s: %s",
                    testHandler.getClass().getName(), productionContext.getStudy().getIdentifier(),
                    productionContext.getUpload().getUploadId(), productionContext.getUpload().getFilename(),
                    ex.getMessage()), ex);
            return;
        }

        // validate
        try {
            contextValidator.validate(productionContext, testContext);
        } catch (RuntimeException | UploadValidationException ex) {
            logger.warn(String.format(
                    "Test validation failed for validator %s, study %s, upload %s, filename %s: %s",
                    contextValidator.getClass().getName(), productionContext.getStudy().getIdentifier(),
                    productionContext.getUpload().getUploadId(), productionContext.getUpload().getFilename(),
                    ex.getMessage()), ex);
        }
    }
}
