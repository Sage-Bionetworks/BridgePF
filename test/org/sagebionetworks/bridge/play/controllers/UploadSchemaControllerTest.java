package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadSchemaControllerTest {
    private static final String TEST_SCHEMA_ID = "controller-test-schema";
    private static final String TEST_SCHEMA_JSON = "{\n" +
                    "   \"name\":\"Controller Test Schema\",\n" +
                    "   \"revision\":3,\n" +
                    "   \"schemaId\":\"controller-test-schema\",\n" +
                    "   \"schemaType\":\"ios_data\",\n" +
                    "   \"fieldDefinitions\":[\n" +
                    "       {\n" +
                    "           \"name\":\"field-name\",\n" +
                    "           \"required\":true,\n" +
                    "           \"type\":\"STRING\"\n" +
                    "       }\n" +
                    "   ]\n" +
                    "}";

    @Test
    public void createV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), createdSchemaCaptor.capture())).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.createSchemaRevisionV4();
        assertEquals(201, result.status());
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaCaptor);
    }

    @Test
    public void createSchema() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaArgCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createOrUpdateUploadSchema(eq(TestConstants.TEST_STUDY), createdSchemaArgCaptor.capture()))
                .thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.createOrUpdateUploadSchema();
        assertEquals(200, result.status());
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaArgCaptor);
    }

    @Test
    public void deleteSchemaByIdAndRevSuccess() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.deleteUploadSchemaByIdAndRev("delete-schema", 1);
        assertEquals(200, result.status());
        verify(mockSvc).deleteUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "delete-schema", 1);
    }

    @Test
    public void deleteSchemaById() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.deleteUploadSchemaById("delete-schema");
        assertEquals(200, result.status());
        verify(mockSvc).deleteUploadSchemaById(TestConstants.TEST_STUDY, "delete-schema");
    }

    @Test
    public void getSchemaById() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchema(TestConstants.TEST_STUDY, TEST_SCHEMA_ID)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.getUploadSchema(TEST_SCHEMA_ID);
        assertEquals(200, result.status());
        assertSchemaInResult(result);
    }

    @Test
    public void getSchemaByIdAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.getUploadSchemaByIdAndRev(TEST_SCHEMA_ID, 1);
        assertEquals(200, result.status());
        assertSchemaInResult(result);
    }

    @Test
    public void getByStudyAndSchemaAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.getUploadSchemaByStudyAndSchemaAndRev(TestConstants.TEST_STUDY_IDENTIFIER,
                TEST_SCHEMA_ID, 1);
        assertEquals(200, result.status());

        // Unlike the other methods, this also returns study ID
        String resultJson = Helpers.contentAsString(result);
        UploadSchema resultSchema = BridgeObjectMapper.get().readValue(resultJson, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, resultSchema.getStudyId());
    }

    @Test
    public void getSchemasForStudy() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForStudy(TestConstants.TEST_STUDY)).thenReturn(ImmutableList.of(
                makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.getUploadSchemasForStudy();
        assertEquals(200, result.status());

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(1, resultNode.get("total").intValue());

        JsonNode itemListNode = resultNode.get("items");
        assertEquals(1, itemListNode.size());

        UploadSchema resultSchema = BridgeObjectMapper.get().treeToValue(itemListNode.get(0), UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
        assertNull(resultSchema.getStudyId());
    }

    @Test
    public void getAllRevisionsOfASchema() throws Exception {
        String schemaId = "controller-test-schema";

        // Create a couple of revisions
        UploadSchema schema1 = makeUploadSchemaForOutput(1);
        UploadSchema schema2 = makeUploadSchemaForOutput(2);
        UploadSchema schema3 = makeUploadSchemaForOutput(3);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TestConstants.TEST_STUDY, schemaId)).thenReturn(ImmutableList.of(
                schema3, schema2, schema1));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.getUploadSchemaAllRevisions(schemaId);
        assertEquals(200, result.status());

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(3, resultNode.get("total").intValue());

        JsonNode itemsNode = resultNode.get("items");
        assertEquals(3, itemsNode.size());

        // Schemas are returned in reverse order.
        UploadSchema returnedSchema3 = BridgeObjectMapper.get().treeToValue(itemsNode.get(0), UploadSchema.class);
        assertEquals(3, returnedSchema3.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema3.getSchemaId());
        assertNull(returnedSchema3.getStudyId());

        UploadSchema returnedSchema2 = BridgeObjectMapper.get().treeToValue(itemsNode.get(1), UploadSchema.class);
        assertEquals(2, returnedSchema2.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema2.getSchemaId());
        assertNull(returnedSchema2.getStudyId());

        UploadSchema returnedSchema1 = BridgeObjectMapper.get().treeToValue(itemsNode.get(2), UploadSchema.class);
        assertEquals(1, returnedSchema1.getRevision());
        assertEquals(TEST_SCHEMA_ID, returnedSchema1.getSchemaId());
        assertNull(returnedSchema1.getStudyId());
    }

    @Test
    public void updateV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> updatedSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.updateSchemaRevisionV4(eq(TestConstants.TEST_STUDY), eq(TEST_SCHEMA_ID), eq(1),
                updatedSchemaCaptor.capture())).thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc);
        Result result = controller.updateSchemaRevisionV4(TEST_SCHEMA_ID, 1);
        assertEquals(200, result.status());
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(updatedSchemaCaptor);
    }

    @Test
    public void invalidSchemaThrowsCompleteValidationException() throws Exception {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("create-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock request JSON; this is pretty bad JSON. We want an error message back 
        // that should practically tell the caller how to construct this object.
        TestUtils.mockPlayContextWithJson("{\"fieldDefinitions\":[{\"name\":\"foo\"}]}");

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        // We need the real service because it throws the InvalidEntityException we're testing here.
        controller.setUploadSchemaService(new UploadSchemaService());
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        try {
            controller.createOrUpdateUploadSchema();
        } catch(InvalidEntityException e) {
            assertEquals("schemaId is required", e.getErrors().get("schemaId").get(0));
            assertEquals("name is required", e.getErrors().get("name").get(0));
            assertEquals("schemaType is required", e.getErrors().get("schemaType").get(0));
            assertEquals("fieldDefinitions[0].type is required", e.getErrors().get("fieldDefinitions[0].type").get(0));
        }
    }

    private static UploadSchemaController setupControllerWithService(UploadSchemaService svc) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);

        // mock request JSON
        TestUtils.mockPlayContextWithJson(TEST_SCHEMA_JSON);

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));
        doReturn(mockSession).when(controller).getAuthenticatedSession(anySetOf(Roles.class));
        return controller;
    }

    private static UploadSchema makeUploadSchemaForOutput() throws Exception {
        return makeUploadSchemaForOutput(3);
    }
    
    private static UploadSchema makeUploadSchemaForOutput(int revision) throws Exception {
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().readTree(TEST_SCHEMA_JSON);
        node.put("revision", revision);

        // Server returns schemas with study IDs (which are filtered out selectively in some methods).
        node.put("studyId", TestConstants.TEST_STUDY_IDENTIFIER);

        return BridgeObjectMapper.get().convertValue(node, UploadSchema.class);
    }

    private static void assertSchemaInResult(Result result) throws Exception {
        // JSON validation is already tested, so just check obvious things like schema ID
        // Also, (most) method results don't include study ID
        String jsonText = Helpers.contentAsString(result);
        UploadSchema schema = BridgeObjectMapper.get().readValue(jsonText, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, schema.getSchemaId());
        assertNull(schema.getStudyId());
    }

    private static void assertSchemaInArgCaptor(ArgumentCaptor<UploadSchema> argCaptor) {
        // Similarly, just check schema ID
        UploadSchema arg = argCaptor.getValue();
        assertEquals(TEST_SCHEMA_ID, arg.getSchemaId());
    }
}
