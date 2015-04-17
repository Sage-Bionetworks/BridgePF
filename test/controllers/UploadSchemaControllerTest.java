package controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.UserSession;
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
        Http.Context.current.set(TestUtils.mockPlayContextWithJson(TEST_SCHEMA_JSON));

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaArgCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createOrUpdateUploadSchema(eq(studyIdentifier), createdSchemaArgCaptor.capture())).thenReturn(
                makeUploadSchema());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(mockSvc);
        doReturn(mockSession).when(controller).getAuthenticatedResearcherOrAdminSession();

        // execute and validate
        Result result = controller.createOrUpdateUploadSchema();
        assertEquals(200, Helpers.status(result));

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
        doReturn(mockSession).when(controller).getAuthenticatedResearcherOrAdminSession();

        // execute and validate
        Result result = controller.deleteUploadSchemaByIdAndRev("delete-schema", 1);
        assertEquals(200, Helpers.status(result));
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
        doReturn(mockSession).when(controller).getAuthenticatedResearcherOrAdminSession();

        // execute and validate
        Result result = controller.deleteUploadSchemaById("delete-schema");
        assertEquals(200, Helpers.status(result));
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
        doReturn(mockSession).when(controller).getAuthenticatedResearcherOrAdminSession();

        // execute and validate
        Result result = controller.getUploadSchema(TEST_SCHEMA_ID);
        assertEquals(200, Helpers.status(result));

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
        doReturn(mockSession).when(controller).getAuthenticatedResearcherOrAdminSession();

        // execute and validate
        Result result = controller.getUploadSchemasForStudy();
        assertEquals(200, Helpers.status(result));

        String resultJson = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultJson);
        assertEquals("ResourceList", resultNode.get("type").textValue());
        assertEquals(1, resultNode.get("total").intValue());

        JsonNode itemListNode = resultNode.get("items");
        assertEquals(1, itemListNode.size());

        UploadSchema resultSchema = BridgeObjectMapper.get().treeToValue(itemListNode.get(0), UploadSchema.class);
        assertEquals(TEST_SCHEMA_ID, resultSchema.getSchemaId());
    }

    private static UploadSchema makeUploadSchema() throws Exception {
        return BridgeObjectMapper.get().readValue(TEST_SCHEMA_JSON, UploadSchema.class);
    }
}
