package org.sagebionetworks.bridge.play.controllers;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AndroidAppSiteAssociation;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationControllerMockTest {

    @Mock
    StudyService studyService;
    
    @Mock
    AuthenticationService authenticationService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Spy
    ApplicationController controller;
    
    Study study;
    
    @Before
    public void before() {
        ViewCache viewCache = new ViewCache();
        viewCache.setCachePeriod(BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS);
        viewCache.setObjectMapper(new ObjectMapper());
        viewCache.setCacheProvider(cacheProvider);
        
        controller = new ApplicationController();
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authenticationService);
        controller.setViewCache(viewCache);
        
        study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setSupportEmail("support@email.com");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        doReturn(study).when(studyService).getStudy("test-study");
        doReturn(Lists.newArrayList(study)).when(studyService).getStudies();
    }
    
    @Test
    public void verifyEmailWorks() {
        Result result = controller.verifyEmail("test-study");
        
        verify(studyService).getStudy("test-study");
        String html = Helpers.contentAsString(result);
        assertTrue(html.contains("Your email address has now been verified."));
    }
    
    @Test
    public void resetPasswordWorks() {
        Result result = controller.resetPassword("test-study");
        
        verify(studyService).getStudy("test-study");
        String html = Helpers.contentAsString(result);
        assertTrue(html.contains("Password is required and must be entered twice."));
    }
    
    @Test
    public void startSessionWorks() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("Accept-Language", new String[]{"en-US"}).build();
        TestUtils.mockPlayContextWithJson("{}", headers);
        
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        doReturn(session).when(authenticationService).emailSignIn(any(), any());
        
        Result result = controller.startSession("test-study", "email", "token");

        verify(authenticationService).emailSignIn(contextCaptor.capture(), signInCaptor.capture());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("UserSessionInfo", node.get("type").asText());
        assertEquals("ABC", node.get("sessionToken").asText());

        CriteriaContext context = contextCaptor.getValue();
        assertEquals("en", context.getLanguages().iterator().next());

        SignIn signIn = signInCaptor.getValue();
        assertEquals("email", signIn.getEmail());
        assertEquals("token", signIn.getToken());
    }
    
    @Test
    public void androidAppLinks() throws Exception {
        DynamoStudy study2 = new DynamoStudy();
        study2.setIdentifier("test-study2");
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(study, study2)).when(studyService).getStudies();
        
        study.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK);
        study.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_2);
        study2.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_3);
        study2.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_4);
        
        Result result = controller.androidAppLinks();
        assertEquals(200, result.status());
        
        JsonNode node = TestUtils.getJson(result);
        assertEquals(TestConstants.ANDROID_APP_LINK, getLinkAtIndex(node, 0));
        assertEquals(TestConstants.ANDROID_APP_LINK_2, getLinkAtIndex(node, 1));
        assertEquals(TestConstants.ANDROID_APP_LINK_3, getLinkAtIndex(node, 2));
        assertEquals(TestConstants.ANDROID_APP_LINK_4, getLinkAtIndex(node, 3));
    }
    
    private AndroidAppLink getLinkAtIndex(JsonNode node, int index) throws Exception {
        return BridgeObjectMapper.get().treeToValue(node.get(index).get("target"), AndroidAppLink.class);
    }

    @Test
    public void appleAppLinks() throws Exception {
        DynamoStudy study2 = new DynamoStudy();
        study2.setIdentifier("test-study2");
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(study, study2)).when(studyService).getStudies();
        
        study.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK);
        study.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_2);
        study2.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_3);
        study2.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_4);

        Result result = controller.appleAppLinks();
        assertEquals(200, result.status());
        
        JsonNode node = TestUtils.getJson(result);
        JsonNode applinks = node.get("applinks");
        JsonNode details = applinks.get("details");
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        AppleAppLink link0 = mapper.readValue(details.get(0).toString(), AppleAppLink.class);
        AppleAppLink link1 = mapper.readValue(details.get(1).toString(), AppleAppLink.class);
        AppleAppLink link2 = mapper.readValue(details.get(2).toString(), AppleAppLink.class);
        AppleAppLink link3 = mapper.readValue(details.get(3).toString(), AppleAppLink.class);
        assertEquals(TestConstants.APPLE_APP_LINK, link0);
        assertEquals(TestConstants.APPLE_APP_LINK_2, link1);
        assertEquals(TestConstants.APPLE_APP_LINK_3, link2);
        assertEquals(TestConstants.APPLE_APP_LINK_4, link3);        
    }
}
