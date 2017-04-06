package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.services.SharedModuleMetadataService;

public class SharedModuleMetadataControllerTest {
    private static final TypeReference<ResourceList<SharedModuleMetadata>> METADATA_RESOURCE_LIST_TYPE =
            new TypeReference<ResourceList<SharedModuleMetadata>>() {};
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private static final String METADATA_JSON_TEXT = "{\n" +
            "   \"id\":\"" + MODULE_ID + "\",\n" +
            "   \"name\":\"" + MODULE_NAME + "\",\n" +
            "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
            "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
            "   \"version\":" + MODULE_VERSION + "\n" +
            "}";

    private SharedModuleMetadataController controller;
    private SharedModuleMetadataService mockSvc;
    private UserSession mockSession;

    @Before
    public void before() {
        // mock service
        mockSvc = mock(SharedModuleMetadataService.class);

        // spy controller and set dependencies
        controller = spy(new SharedModuleMetadataController());
        controller.setMetadataService(mockSvc);

        // mock controller with session with shared study
        mockSession = new UserSession();
        mockSession.setStudyIdentifier(BridgeConstants.SHARED_STUDY_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void create() throws Exception {
        // mock service
        ArgumentCaptor<SharedModuleMetadata> svcInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        when(mockSvc.createMetadata(svcInputMetadataCaptor.capture())).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        TestUtils.mockPlayContextWithJson(METADATA_JSON_TEXT);
        Result result = controller.createMetadata();
        assertEquals(201, result.status());
        assertMetadataInResult(result);
        assertMetadataInArgCaptor(svcInputMetadataCaptor);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void deleteByIdAllVersions() throws Exception {
        // setup, execute, and validate
        Result result = controller.deleteMetadataByIdAllVersions(MODULE_ID);
        assertEquals(200, result.status());

        // verify backend
        verify(mockSvc).deleteMetadataByIdAllVersions(MODULE_ID);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void deleteByIdAndVersion() throws Exception {
        // setup, execute, and validate
        Result result = controller.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertEquals(200, result.status());

        // verify backend
        verify(mockSvc).deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void byIdAndVersion() throws Exception {
        // mock service
        when(mockSvc.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        Result result = controller.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertEquals(200, result.status());
        assertMetadataInResult(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void byIdLatestVersion() throws Exception {
        // mock service
        when(mockSvc.getMetadataByIdLatestVersion(MODULE_ID)).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        Result result = controller.getMetadataByIdLatestVersion(MODULE_ID);
        assertEquals(200, result.status());
        assertMetadataInResult(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void queryAll() throws Exception {
        // mock service
        when(mockSvc.queryAllMetadata(true, true, "foo='bar'", ImmutableSet.of("foo", "bar", "baz"))).thenReturn(
                ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        Result result = controller.queryAllMetadata("true", "true", "foo='bar'", "foo,bar,baz");
        assertEquals(200, result.status());
        assertMetadataListInResult(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void queryById() throws Exception {
        // mock service
        when(mockSvc.queryMetadataById(MODULE_ID, true, true, "foo='bar'", ImmutableSet.of("foo", "bar", "baz")))
                .thenReturn(ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        Result result = controller.queryMetadataById(MODULE_ID, "true", "true", "foo='bar'", "foo,bar,baz");
        assertEquals(200, result.status());
        assertMetadataListInResult(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void parseTags() {
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags(null));
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags(""));
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags("   "));
        assertEquals(ImmutableSet.of("foo"), SharedModuleMetadataController.parseTags("foo"));
        assertEquals(ImmutableSet.of("foo", "bar", "baz"), SharedModuleMetadataController.parseTags("foo,bar,baz"));
    }

    @Test
    public void update() throws Exception {
        // mock service
        ArgumentCaptor<SharedModuleMetadata> svcInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        when(mockSvc.updateMetadata(eq(MODULE_ID), eq(MODULE_VERSION), svcInputMetadataCaptor.capture())).thenReturn(
                makeValidMetadata());

        // setup, execute, and validate
        TestUtils.mockPlayContextWithJson(METADATA_JSON_TEXT);
        Result result = controller.updateMetadata(MODULE_ID, MODULE_VERSION);
        assertEquals(200, result.status());
        assertMetadataInResult(result);
        assertMetadataInArgCaptor(svcInputMetadataCaptor);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    private static void assertMetadataInArgCaptor(ArgumentCaptor<SharedModuleMetadata> argCaptor) {
        // JSON validation is already tested, so just check obvious things like module ID
        SharedModuleMetadata arg = argCaptor.getValue();
        assertMetadata(arg);
    }

    private static void assertMetadataInResult(Result result) throws Exception {
        // Similarly, just check module ID
        String jsonText = Helpers.contentAsString(result);
        SharedModuleMetadata metadata = BridgeObjectMapper.get().readValue(jsonText, SharedModuleMetadata.class);
        assertMetadata(metadata);
    }

    private static void assertMetadataListInResult(Result result) throws Exception {
        // Similarly, just check module ID
        String jsonText = Helpers.contentAsString(result);

        ResourceList<SharedModuleMetadata> metadataResourceList = BridgeObjectMapper.get().readValue(jsonText,
                METADATA_RESOURCE_LIST_TYPE);
        assertEquals(1, metadataResourceList.getTotal());

        List<SharedModuleMetadata> metadataList = metadataResourceList.getItems();
        assertEquals(1, metadataList.size());
        assertMetadata(metadataList.get(0));
    }

    private static void assertMetadata(SharedModuleMetadata metadata) {
        assertEquals(MODULE_ID, metadata.getId());
        assertEquals(MODULE_NAME, metadata.getName());
        assertEquals(MODULE_VERSION, metadata.getVersion());
        assertEquals(SCHEMA_ID, metadata.getSchemaId());
        assertEquals(SCHEMA_REV, metadata.getSchemaRevision().intValue());
    }

    @Test(expected = UnauthorizedException.class)
    public void nonSharedStudyCantCreate() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        controller.createMetadata();
    }

    @Test(expected = UnauthorizedException.class)
    public void nonSharedStudyCantDeleteByIdAllVersions() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        controller.deleteMetadataByIdAllVersions(MODULE_ID);
    }

    @Test(expected = UnauthorizedException.class)
    public void nonSharedStudyCantDeleteByIdAndVersion() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        controller.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
    }

    @Test(expected = UnauthorizedException.class)
    public void nonSharedStudyUpdate() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        controller.updateMetadata(MODULE_ID, MODULE_VERSION);
    }

    private static SharedModuleMetadata makeValidMetadata() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }
}
