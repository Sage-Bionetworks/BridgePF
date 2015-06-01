package controllers;

import static org.sagebionetworks.bridge.BridgeConstants.JSON_MIME_TYPE;

import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
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

@Controller("surveyController")
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
    
    public Result getAllSurveysMostRecentVersion() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentVersion2() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(studyId);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
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
        final UserSession session = getAuthenticatedResearcherSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        
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
        final UserSession session = getAuthenticatedResearcherSession();
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
        final UserSession session = getAuthenticatedResearcherSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        
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
    
    public Result getMostRecentPublishedSurveyVersionByIdentifier(String identifier) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Do not cache this. It's only used by researchers and without the GUID, you cannot
        // cache it properly.
        Survey survey = surveyService.getSurveyMostRecentlyPublishedVersionByIdentifier(studyId, identifier);
        verifySurveyIsInStudy(session, studyId, survey);
        return okResult(survey);
    }
    
    public Result deleteSurvey(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        surveyService.deleteSurvey(studyId, survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult("Survey deleted.");
    }
    
    public Result getSurveyAllVersions(String surveyGuid) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        List<Survey> surveys = surveyService.getSurveyAllVersions(studyId, surveyGuid);
        verifySurveyIsInStudy(session, studyId, surveys);
        return okResult(surveys);
    }
    
    public Result createSurvey() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        Survey survey = parseJson(request(), Survey.class);
        survey.setStudyIdentifier(studyId.getIdentifier());
        
        survey = surveyService.createSurvey(survey);
        return createdResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result versionSurvey(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
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
        UserSession session = getAuthenticatedResearcherSession();
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
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
         
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        survey = surveyService.publishSurvey(survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult(new GuidCreatedOnVersionHolderImpl(survey));
    }
    
    public Result closeSurvey(String surveyGuid, String createdOnString) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        
        Survey survey = surveyService.getSurvey(keys);
        verifySurveyIsInStudy(session, studyId, survey);
        
        surveyService.closeSurvey(survey);
        expireCache(surveyGuid, createdOnString);
        
        return okResult("Survey closed.");
    }
    
    private void verifySurveyIsInStudy(UserSession session, StudyIdentifier studyIdentifier, List<Survey> surveys) {
        if (!surveys.isEmpty()) {
            verifySurveyIsInStudy(session, studyIdentifier, surveys.get(0));
        }
    }
    
    private void verifySurveyIsInStudy(UserSession session, StudyIdentifier studyIdentifier, Survey survey) {
        if (!session.getUser().isInRole(BridgeConstants.ADMIN_GROUP) && 
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