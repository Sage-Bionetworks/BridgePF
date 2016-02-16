package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
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
    public void createSchema() throws Exception {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("create-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock request JSON
        TestUtils.mockPlayContextWithJson(TEST_SCHEMA_JSON);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaArgCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createOrUpdateUploadSchema(eq(studyIdentifier), createdSchemaArgCaptor.capture())).thenReturn(
                        makeUploadSchema());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.createOrUpdateUploadSchema();
        assertEquals(200, result.status());

        // JSON validation is already tested, so just check obvious things like schema
        String resultJson = Helpers.contentAsString(result);
        UploadSchema resultSchema = BridgeObjectMapper.get().readValue(resultJson, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());

        // validate intermediate args
        UploadSchema createdSchemaArg = createdSchemaArgCaptor.getValue();
        assertEquals(TEST_SCHEMA_ID, createdSchemaArg.getSchemaId());
    }

    @Test
    public void deleteSchemaByIdAndRevSuccess() {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("delete-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.deleteUploadSchemaByIdAndRev("delete-schema", 1);
        assertEquals(200, result.status());
        verify(mockSvc).deleteUploadSchemaByIdAndRev(studyIdentifier, "delete-schema", 1);
    }

    @Test
    public void deleteSchemaById() {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("delete-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.deleteUploadSchemaById("delete-schema");
        assertEquals(200, result.status());
        verify(mockSvc).deleteUploadSchemaById(studyIdentifier, "delete-schema");
    }

    @Test
    public void getSchemaById() throws Exception {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("get-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchema(studyIdentifier, TEST_SCHEMA_ID)).thenReturn(makeUploadSchema());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.getUploadSchema(TEST_SCHEMA_ID);
        assertEquals(200, result.status());

        // JSON validation is already tested, so just check obvious things like schema
        String resultJson = Helpers.contentAsString(result);
        UploadSchema resultSchema = BridgeObjectMapper.get().readValue(resultJson, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
    }

    @Test
    public void getSchemaByIdAndRev() throws Exception {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("get-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(studyIdentifier, TEST_SCHEMA_ID, 1)).thenReturn(makeUploadSchema());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.getUploadSchemaByIdAndRev(TEST_SCHEMA_ID, 1);
        assertEquals(200, result.status());

        // JSON validation is already tested, so just check obvious things like schema
        String resultJson = Helpers.contentAsString(result);
        UploadSchema resultSchema = BridgeObjectMapper.get().readValue(resultJson, UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
    }

    @Test
    public void getSchemasForStudy() throws Exception {
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("get-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForStudy(studyIdentifier)).thenReturn(ImmutableList.of(makeUploadSchema()));

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
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
    }
    
    @Test
    public void getAllRevisionsOfASchema() throws Exception {
        String schemaId = "controller-test-schema";
        
        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("get-schema-study");
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(studyIdentifier);

        // Create a couple of revisions
        UploadSchema schema1 = makeUploadSchema(1);
        UploadSchema schema2 = makeUploadSchema(2);
        UploadSchema schema3 = makeUploadSchema(3);
        
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(studyIdentifier, schemaId)).thenReturn(ImmutableList.of(schema3, schema2, schema1));

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any(Roles.class));

        // execute and validate
        Result result = controller.getUploadSchemaAllRevisions(schemaId);
        assertEquals(200, result.status());

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(3, resultNode.get("total").intValue());

        JsonNode itemsNode = resultNode.get("items");
        assertEquals(3, itemsNode.size());

        assertEquals(3, itemsNode.get(0).get("revision").asInt());
        assertEquals(2, itemsNode.get(1).get("revision").asInt());
        assertEquals(1, itemsNode.get(2).get("revision").asInt());
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

    private static UploadSchema makeUploadSchema() throws Exception {
        return makeUploadSchema(3);
    }
    
    private static UploadSchema makeUploadSchema(int revision) throws Exception {
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().readTree(TEST_SCHEMA_JSON);
        node.put("revision", revision);
        return BridgeObjectMapper.get().convertValue(node, UploadSchema.class);
    }
}
