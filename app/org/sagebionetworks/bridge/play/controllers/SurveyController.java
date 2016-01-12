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
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.User;
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
    
    public Result getAllSurveysMostRecentVersion(String format) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        if ("summary".equals(format)) {
            List<Survey> surveys = surveyService.getSurveysSummary(studyId);
            verifySurveyIsInStudy(session, studyId, surveys);
            
            return ok(Survey.SUMMARY_LIST_WRITER.writeValueAsString(new ResourceList<Survey>(surveys)));
        }
        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentVersion2() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getSurveyForUser(final String surveyGuid, final String createdOnString) throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();

        ViewCacheKey<Survey> cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, createdOnString); 
        String json = viewCache.getView(cacheKey, new Supplier<Survey>() {
            @Override public Survey get() {
                StudyIdentifier studyId = session.getStudyIdentifier();
                long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
                GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);

                Survey survey = surveyService.getSurvey(keys);
                verifySurveyIsInStudy(session, studyId, survey);
                return surveyService.getSurvey(keys);
            }
        });
        return ok(json).as(JSON_MIME_TYPE);
    }

    public Result getSurveyMostRecentlyPublishedVersionForUser(final String surveyGuid) throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        
        ViewCacheKey<Survey> cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, PUBLISHED_KEY);
        String json = viewCache.getView(cacheKey, new Supplier<Survey>() {
            @Override public Survey get() {
                StudyIdentifier studyId = session.getStudyIdentifier();
                Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(studyId, surveyGuid);
                verifySurveyIsInStudy(session, studyId, survey);
                return survey;
            }
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    // Otherwise you don't need consent but you must be a researcher or an administrator
    public Result getSurvey(final String surveyGuid, final String createdOnString) throws Exception {
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
    
    public Result getSurveyMostRecentVersion(final String surveyGuid) throws Exception {
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
    
    public Result getSurveyMostRecentlyPublishedVersion(final String surveyGuid) throws Exception {
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
    
    /**
     * Administrators can pass the ?physical=true flag to this endpoint to physically delete a survey and all its 
     * survey elements, rather than only marking it deleted to maintain referential integrity. This should only be 
     * used as part of testing.
     * @param surveyGuid
     * @param createdOnString
     * @param physical
     * @return
     * @throws Exception
     */
    public Result deleteSurvey(String surveyGuid, String createdOnString, String physical) throws Exception {
        UserSession session = getAuthenticatedSession();
        User user = session.getUser();
        
        // If not in either of these roles, don't do the work of getting the survey
        if (!user.isInRole(DEVELOPER) && !user.isInRole(ADMIN)) {
            throw new UnauthorizedException();
        }
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        if ("true".equals(physical) && user.isInRole(ADMIN)) {
            surveyService.deleteSurveyPermanently(survey);
        } else if (user.isInRole(DEVELOPER)) {
            surveyService.deleteSurvey(survey);    
        } else {
            // An admin calling for a logical delete. That wasn't allowed before so we don't allow it now.
            throw new UnauthorizedException();
        }
        expireCache(surveyGuid, createdOnString);
        return okResult("Survey deleted.");
    }
    
    public Result getSurveyAllVersions(String surveyGuid) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        List<Survey> surveys = surveyService.getSurveyAllVersions(studyId, surveyGuid);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result createSurvey() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        Survey survey = parseJson(request(), Survey.class);
        survey.setStudyIdentifier(studyId.getIdentifier());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String createdOnString) throws Exception {
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
    
    public Result updateSurvey(String surveyGuid, String createdOnString) throws Exception {
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
    
    public Result publishSurvey(String surveyGuid, String createdOnString) throws Exception {
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
