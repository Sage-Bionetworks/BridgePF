package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@SuppressWarnings("unchecked")
public class IosSchemaValidationHandler2GetSchemaTest {
    private static final Map<String, Map<String, Integer>> DEFAULT_SCHEMA_REV_MAP =
            ImmutableMap.<String, Map<String, Integer>>of(TestConstants.TEST_STUDY_IDENTIFIER,
                    ImmutableMap.of("schema-rev-test", 2));

    @Test
    public void itemWithDefaultRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void itemWithLegacyMapRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "schema-rev-test", 2)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void itemWithRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "schema-rev-test", 3)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");
        infoJson.put("schemaRevision", 3);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void fallbackToIdentifier() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("identifier", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test(expected = UploadValidationException.class)
    public void missingItem() throws Exception {
        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY,
                BridgeObjectMapper.get().createObjectNode());
    }

    @Test(expected = UploadValidationException.class)
    public void nullItem() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.putNull("item");

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test(expected = UploadValidationException.class)
    public void itemInvalidType() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", 42);

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test(expected = UploadValidationException.class)
    public void emptyItem() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "");

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test(expected = UploadValidationException.class)
    public void blankItem() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "   ");

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test(expected = UploadValidationException.class)
    public void nullSchemaRev() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");
        infoJson.putNull("schemaRevision");

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test(expected = UploadValidationException.class)
    public void schemaRevInvalidType() throws Exception {
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");
        infoJson.put("schemaRevision", "not an int");

        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test
    public void badRequestException() throws Exception {
        // BadRequestExceptions happen if the inputs are invalid (like null or empty schema IDs or non-positive schema
        // revs). The only way this is possible through our code is a non-positive schema rev. So we can use a real
        // UploadSchemaService and pass it a negative rev to trigger this.

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(new UploadSchemaService());

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");
        infoJson.put("schemaRevision", -1);

        // execute and validate
        Exception thrownEx = null;
        try {
            handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
            fail();
        } catch (UploadValidationException ex) {
            thrownEx = ex;
        }
        assertTrue(thrownEx.getCause() instanceof BadRequestException);
    }

    @Test
    public void entityNotFoundException() throws Exception {
        // mock upload schema service
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "not-found-schema", 1)).thenThrow(
                EntityNotFoundException.class);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "not-found-schema");

        // execute and validate
        Exception thrownEx = null;
        try {
            handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
            fail();
        } catch (UploadValidationException ex) {
            thrownEx = ex;
        }
        assertTrue(thrownEx.getCause() instanceof EntityNotFoundException);
    }
}
