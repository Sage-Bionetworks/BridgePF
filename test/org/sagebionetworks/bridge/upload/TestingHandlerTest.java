package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;

public class TestingHandlerTest {
    @Test
    public void productionHandlerFails() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();
        UploadValidationContext mockTestContext = mockContext();

        ContextCloner mockCloner = mock(ContextCloner.class);
        when(mockCloner.clone(mockProdContext)).thenReturn(mockTestContext);

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        doThrow(UploadValidationException.class).when(mockProdHandler).handle(mockProdContext);

        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextCloner(mockCloner);
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute
        Exception thrownEx = null;
        try {
            testTestingHandler.handle(mockProdContext);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate
        verify(mockProdHandler).handle(mockProdContext);
        verifyZeroInteractions(mockTestHandler, mockValidator);
    }

    @Test
    public void testHandlerFails() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();
        UploadValidationContext mockTestContext = mockContext();

        ContextCloner mockCloner = mock(ContextCloner.class);
        when(mockCloner.clone(mockProdContext)).thenReturn(mockTestContext);

        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        doThrow(UploadValidationException.class).when(mockTestHandler).handle(mockTestContext);

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextCloner(mockCloner);
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute - Test handler throws, but the exception is squelched because it's not supposed to impact
        // production.
        testTestingHandler.handle(mockProdContext);

        // validate
        verify(mockProdHandler).handle(mockProdContext);
        verify(mockTestHandler).handle(mockTestContext);
        verifyZeroInteractions(mockValidator);
    }

    @Test
    public void validation() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();
        UploadValidationContext mockTestContext = mockContext();

        ContextCloner mockCloner = mock(ContextCloner.class);
        when(mockCloner.clone(mockProdContext)).thenReturn(mockTestContext);

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextCloner(mockCloner);
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute
        testTestingHandler.handle(mockProdContext);

        // validate
        verify(mockProdHandler).handle(mockProdContext);
        verify(mockTestHandler).handle(mockTestContext);
        verify(mockValidator).validate(mockProdContext, mockTestContext);
    }

    private static UploadValidationContext mockContext() {
        // Testing handler may call context.getStudy().getIdentifier(), context.getUpload().getUploadId(), and
        // context.getUpload().getFilename(), so get those ready

        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test-study");

        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId("test-upload");
        upload.setFilename("test-filename");

        UploadValidationContext mockContext = new UploadValidationContext();
        mockContext.setStudy(study);
        mockContext.setUpload(upload);
        return mockContext;
    }
}
