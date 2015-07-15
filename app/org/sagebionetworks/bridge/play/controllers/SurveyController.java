package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.JSON_MIME_TYPE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.google.common.base.Supplier;

@Controller
public class SurveyController extends BaseController {

    private static final String MOSTRECENT_KEY = "mostrecent";
    private static final String PUBLISHED_KEY = "published";

    private SurveyService surveyService;
    
    private ViewCache viewCache;

    @Autowired
    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    @Autowired
    public void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    
    public Result getAllSurveysMostRecentVersionV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    // This is "2V3" because Play Framework cannot route two URLs to the same code.
    public Result getAllSurveysMostRecentVersion2V3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentlyPublishedVersionV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    // You don't need consent but if you don't have consent, you need to be a developer
    public Result getSurveyV3(final String surveyGuid, final String createdOnString) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        canAccessSurvey(session);
        
        ViewCacheKey<Survey> cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, createdOnString); 
        String json = viewCache.getView(cacheKey, new Supplier<Survey>() {
            @Override public Survey get() {
                long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
                GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
                Survey survey = surveyService.getSurvey(keys);
                verifySurveyIsInStudy(session, studyId, survey);
                return survey;
            }
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    public Result getSurveyMostRecentVersionV3(final String surveyGuid) throws Exception {
        final UserSession session = getAuthenticatedSession(DEVELOPER);
        final StudyIdentifier studyId = session.getStudyIdentifier();
        
        ViewCacheKey<Survey> cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, MOSTRECENT_KEY);
        String json = viewCache.getView(cacheKey, new Supplier<Survey>() {
            @Override public Survey get() {
                Survey survey = surveyService.getSurveyMostRecentVersion(studyId, surveyGuid);
                verifySurveyIsInStudy(session, studyId, survey);
                return survey;
            }
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    public Result getSurveyMostRecentlyPublishedVersionV3(final String surveyGuid) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        canAccessSurvey(session);

        ViewCacheKey<Survey> cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, PUBLISHED_KEY);
        String json = viewCache.getView(cacheKey, new Supplier<Survey>() {
            @Override public Survey get() {
                Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(studyId, surveyGuid);
                verifySurveyIsInStudy(session, studyId, survey);
                return survey;
            }
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    public Result getMostRecentPublishedSurveyVersionByIdentifierV3(String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Do not cache this. It's only used by researchers and without the GUID, you cannot
        // cache it properly.
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersionByIdentifier(studyId, identifier);
        verifySurveyIsInStudy(session, studyId, survey);
        return okResult(survey);
    }
    
    public Result deleteSurveyV3(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        surveyService.deleteSurvey(survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult("Survey deleted.");
    }
    
    public Result getSurveyAllVersionsV3(String surveyGuid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        List<Survey> surveys = surveyService.getSurveyAllVersions(studyId, surveyGuid);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result createSurveyV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        Survey survey = parseJson(request(), Survey.class);
        survey.setStudyIdentifier(studyId.getIdentifier());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result versionSurveyV3(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);

        survey = surveyService.versionSurvey(survey);
        expireCache(surveyGuid, createdOnString);
        
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result updateSurveyV3(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        // The parameters in the URL take precedence over anything declared in 
        // the object itself.
        survey = parseJson(request(), Survey.class);
        survey.setGuid(surveyGuid);
        survey.setCreatedOn(createdOn);
        survey.setStudyIdentifier(studyId.getIdentifier());
        
        survey = surveyService.updateSurvey(survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result publishSurveyV3(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
         
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        survey = surveyService.publishSurvey(studyId, survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    @Deprecated
    public Result getAllSurveysMostRecentVersionV1() throws Exception {
        return getAllSurveysMostRecentVersionV3();
    }
    @Deprecated
    public Result getAllSurveysMostRecentVersion2V1() throws Exception {
        return getAllSurveysMostRecentVersion2V3();
    }
    @Deprecated
    public Result getAllSurveysMostRecentlyPublishedVersionV1() throws Exception {
        return getAllSurveysMostRecentlyPublishedVersionV3();
    }
    @Deprecated
    public Result getSurveyV1(final String surveyGuid, final String createdOnString) throws Exception {
        return getSurveyV3(surveyGuid, createdOnString);
    }
    @Deprecated
    public Result getSurveyMostRecentVersionV1(final String surveyGuid) throws Exception {
        return getSurveyMostRecentVersionV3(surveyGuid);
    }
    @Deprecated
    public Result getSurveyMostRecentlyPublishedVersionV1(final String surveyGuid) throws Exception {
        return getSurveyMostRecentlyPublishedVersionV3(surveyGuid);
    }
    @Deprecated
    public Result getMostRecentPublishedSurveyVersionByIdentifierV1(String identifier) throws Exception {
        return getMostRecentPublishedSurveyVersionByIdentifierV3(identifier);
    }
    @Deprecated
    public Result deleteSurveyV1(String surveyGuid, String createdOnString) throws Exception {
        return deleteSurveyV3(surveyGuid, createdOnString);
    }
    @Deprecated
    public Result getSurveyAllVersionsV1(String surveyGuid) throws Exception {
        return getSurveyAllVersionsV3(surveyGuid);
    }
    @Deprecated
    public Result createSurveyV1() throws Exception {
        return createSurveyV3();
    }
    @Deprecated
    public Result versionSurveyV1(String surveyGuid, String createdOnString) throws Exception {
        return versionSurveyV3(surveyGuid, createdOnString);
    }
    @Deprecated
    public Result updateSurveyV1(String surveyGuid, String createdOnString) throws Exception {
        return updateSurveyV3(surveyGuid, createdOnString);
    }
    @Deprecated
    public Result publishSurveyV1(String surveyGuid, String createdOnString) throws Exception {
        return publishSurveyV3(surveyGuid, createdOnString);
    }
    
    private void canAccessSurvey(UserSession session) {
        boolean isDeveloper = session.getUser().isInRole(DEVELOPER);
        boolean isConsentedUser = session.getUser().doesConsent();
        
        if (isDeveloper || isConsentedUser) {
            return;
        }
        // An imperfect test, but normal users have no other roles, so for them, access 
        // is restricted because they have not consented.
        Set<Roles> roles = new HashSet<>(session.getUser().getRoles());
        roles.remove(TEST_USERS);
        if (session.getUser().getRoles().isEmpty()) {
            throw new ConsentRequiredException(session);
        }
        // Otherwise, for researchers and administrators, the issue is one of authorization.
        throw new UnauthorizedException();        
    }
    
    private void verifySurveyIsInStudy(UserSession session, StudyIdentifier studyIdentifier, List<Survey> surveys) {
        if (!surveys.isEmpty()) {
            verifySurveyIsInStudy(session, studyIdentifier, surveys.get(0));
        }
    }
    
    private void verifySurveyIsInStudy(UserSession session, StudyIdentifier studyIdentifier, Survey survey) {
        if (!session.getUser().isInRole(ADMIN) && 
            !survey.getStudyIdentifier().equals(studyIdentifier.getIdentifier())) {
            throw new UnauthorizedException();
        }
    }
    
    private void expireCache(String surveyGuid, String createdOnString) {
        // Don't screw around trying to figure out if *this* survey instance is the same survey
        // as the most recent or published version, expire all versions in the cache
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, createdOnString));
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, PUBLISHED_KEY));
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, MOSTRECENT_KEY));
    }
    
}
