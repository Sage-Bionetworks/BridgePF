package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.services.AppConfigElementService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigElementsControllerTest {
    
    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private static final List<AppConfigElement> APP_CONFIG_ELEMENTS = ImmutableList.of(AppConfigElement.create(),
            AppConfigElement.create());
    private static final TypeReference<ResourceList<AppConfigElement>> APP_CONFIG_TYPEREF = 
            new TypeReference<ResourceList<AppConfigElement>>() {};
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);
    
    @Spy
    private AppConfigElementsController controller;
    
    @Captor
    private ArgumentCaptor<AppConfigElement> elementCaptor;
    
    @Captor
    private ArgumentCaptor<CacheKey> cacheKeyCaptor;
    
    @Mock
    private AppConfigElementService service;
    
    @Mock
    private CacheProvider cacheProvider;
    
    private UserSession session;
    
    @Before
    public void before() { 
        controller.setAppConfigElementService(service);
        controller.setCacheProvider(cacheProvider);
        
        session = new UserSession(new StudyParticipant.Builder().build());
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void getMostRecentElementsIncludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getMostRecentElements(TestConstants.TEST_STUDY, true)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getMostRecentElements("true");
        
        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertTrue((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getMostRecentElements(TestConstants.TEST_STUDY, true);
    }
    
    @Test
    public void getMostRecentElementsExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getMostRecentElements(TestConstants.TEST_STUDY, false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getMostRecentElements("false");

        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertFalse((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getMostRecentElements(TestConstants.TEST_STUDY, false);
    }
    
    @Test
    public void getMostRecentElementsDefaultToExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getMostRecentElements(TestConstants.TEST_STUDY, false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getMostRecentElements(null);
        
        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertFalse((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getMostRecentElements(TestConstants.TEST_STUDY, false);
    }
    
    @Test
    public void createElement() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        TestUtils.mockPlay().withBody(element).mock();
        
        when(service.createElement(eq(TestConstants.TEST_STUDY), any())).thenReturn(VERSION_HOLDER);
        
        Result result = controller.createElement();
        assertEquals(201, result.status());
        VersionHolder returnedHolder = TestUtils.getResponsePayload(result, VersionHolder.class);
        assertEquals(new Long(1), returnedHolder.getVersion());
        
        verify(cacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(service).createElement(eq(TestConstants.TEST_STUDY), elementCaptor.capture());
        assertEquals("element-id", elementCaptor.getValue().getId());
    }

    @Test
    public void getElementRevisionsIncludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getElementRevisions(TestConstants.TEST_STUDY, "id", true)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getElementRevisions("id", "true");
        
        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertTrue((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(service).getElementRevisions(TestConstants.TEST_STUDY, "id", true);
    }
    
    @Test
    public void getElementRevisionsExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getElementRevisions(TestConstants.TEST_STUDY, "id", false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getElementRevisions("id", "false");
        
        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertFalse((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(service).getElementRevisions(TestConstants.TEST_STUDY, "id", false);
    }
    
    @Test
    public void getElementRevisionsDefaultsToExcludeDeleted() throws Exception {
        TestUtils.mockPlay().mock();
        when(service.getElementRevisions(TestConstants.TEST_STUDY, "id", false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        Result result = controller.getElementRevisions("id", null);
        
        ResourceList<AppConfigElement> returnedElements = TestUtils.getResponsePayload(result, APP_CONFIG_TYPEREF);
        assertEquals(200, result.status());
        assertEquals(2, returnedElements.getItems().size());
        assertFalse((Boolean)returnedElements.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(service).getElementRevisions(TestConstants.TEST_STUDY, "id", false);
    }
    
    @Test
    public void getMostRecentElement() throws Exception {
        TestUtils.mockPlay().mock();
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        when(service.getMostRecentElement(TestConstants.TEST_STUDY, "element-id")).thenReturn(element);
        
        Result result = controller.getMostRecentElement("element-id");
        AppConfigElement returnedElement = TestUtils.getResponsePayload(result, AppConfigElement.class);
        assertEquals(200, result.status());
        assertEquals("element-id", returnedElement.getId());
        
        verify(service).getMostRecentElement(TestConstants.TEST_STUDY, "element-id");
    }

    @Test
    public void getElementRevision() throws Exception {
        TestUtils.mockPlay().mock();
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        when(service.getElementRevision(TestConstants.TEST_STUDY, "id", 3L)).thenReturn(element);
        
        Result result = controller.getElementRevision("id", "3");
        AppConfigElement returnedElement = TestUtils.getResponsePayload(result, AppConfigElement.class);
        assertEquals(200, result.status());
        assertEquals("element-id", returnedElement.getId());
        
        verify(service).getElementRevision(TestConstants.TEST_STUDY, "id", 3L);
    }
    
    @Test(expected = BadRequestException.class)
    public void getElementRevisionBadRevisionNumber() throws Exception {
        controller.getElementRevision("id", "three");
    }
    
    @Test
    public void updateElementRevision() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        // These values should be overwritten by the values in the URL
        element.setId("element-id");
        element.setRevision(3L);
        TestUtils.mockPlay().withBody(element).mock();
        when(service.updateElementRevision(eq(TestConstants.TEST_STUDY), any())).thenReturn(VERSION_HOLDER);
        
        Result result = controller.updateElementRevision("id", "1");
        VersionHolder returnedHolder = TestUtils.getResponsePayload(result, VersionHolder.class);
        assertEquals(200, result.status());
        assertEquals(new Long(1), returnedHolder.getVersion());
        
        verify(cacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(service).updateElementRevision(eq(TestConstants.TEST_STUDY), elementCaptor.capture());
        assertEquals("id", elementCaptor.getValue().getId());
        assertEquals(new Long(1), elementCaptor.getValue().getRevision());
    }
    
    @Test(expected = BadRequestException.class)
    public void updateElementRevisionBadRevisionNumber() {
        controller.updateElementRevision("id", "one");
    }
    
    @Test
    public void deleteElementAllRevisions() throws Exception {
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementAllRevisions("id", "false");
        TestUtils.assertResult(result, 200, "App config element deleted.");
     
        verify(cacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(service).deleteElementAllRevisions(TestConstants.TEST_STUDY, "id");
    }
    
    @Test
    public void deleteElementAllRevisionsPermanently() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementAllRevisions("id", "true");
        TestUtils.assertResult(result, 200, "App config element deleted.");
        
        verify(cacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());

        verify(service).deleteElementAllRevisionsPermanently(TestConstants.TEST_STUDY, "id");
    }
    
    @Test
    public void deleteElementAllRevisionsDefaultsToLogical() throws Exception {
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementAllRevisions("id", "true");
        TestUtils.assertResult(result, 200, "App config element deleted.");
     
        verify(service).deleteElementAllRevisions(TestConstants.TEST_STUDY, "id");
    }
    
    @Test
    public void deleteElementRevision() throws Exception {
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementRevision("id", "3", "false");
        TestUtils.assertResult(result, 200, "App config element revision deleted.");

        verify(cacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());

        verify(service).deleteElementRevision(TestConstants.TEST_STUDY, "id", 3L);
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteElementRevisionBadRevisionNumber() throws Exception {
        TestUtils.mockPlay().mock();
        
        controller.deleteElementRevision("id", "three", "false");
    }
    
    @Test
    public void deleteElementRevisionPermanently() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementRevision("id", "3", "true");
        TestUtils.assertResult(result, 200, "App config element revision deleted.");
     
        verify(service).deleteElementRevisionPermanently(TestConstants.TEST_STUDY, "id", 3L);
    }
    
    @Test
    public void deleteElementRevisionDefaultsToLogical() throws Exception {
        TestUtils.mockPlay().mock();
        
        Result result = controller.deleteElementRevision("id", "3", "true");
        TestUtils.assertResult(result, 200, "App config element revision deleted.");
     
        verify(service).deleteElementRevision(TestConstants.TEST_STUDY, "id", 3L);
    }
}
