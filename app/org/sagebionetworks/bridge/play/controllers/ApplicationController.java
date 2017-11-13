package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;
import static org.sagebionetworks.bridge.BridgeConstants.JSON_MIME_TYPE;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.AndroidAppSiteAssociation;
import org.sagebionetworks.bridge.models.AppleAppSiteAssociation;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.appconfig.AndroidAppLink;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.google.common.collect.Lists;

import play.mvc.Result;

@Controller
public class ApplicationController extends BaseController {
    
    @SuppressWarnings("serial")
    private static class AndroidAppLinkList extends ArrayList<AndroidAppSiteAssociation> {};

    private AppConfigService appConfigService;
    
    private ViewCache viewCache;
    
    private static final String ASSETS_BUILD = "201501291830";

    @Autowired
    final void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }
    
    @Autowired
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    
    public Result loadApp() throws Exception {
        return ok(views.html.index.render());
    }

    public Result verifyEmail(String studyId) {
        Study study = studyService.getStudy(studyId);
        return ok(views.html.verifyEmail.render(ASSETS_HOST, ASSETS_BUILD,
                StringEscapeUtils.escapeHtml4(study.getName()), study.getSupportEmail()));
    }

    public Result resetPassword(String studyId) {
        Study study = studyService.getStudy(studyId);
        String passwordDescription = BridgeUtils.passwordPolicyDescription(study.getPasswordPolicy());
        return ok(views.html.resetPassword.render(ASSETS_HOST, ASSETS_BUILD,
            StringEscapeUtils.escapeHtml4(study.getName()), study.getSupportEmail(), 
            passwordDescription));
    }
    
    public Result startSession(String studyId, String email, String token) {
        SignIn signIn = new SignIn.Builder().withStudy(studyId).withEmail(email).withToken(token).build();
        
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);
        CriteriaContext context = getCriteriaContext(studyIdentifier);
        UserSession session = authenticationService.emailSignIn(context, signIn);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result androidAppLinks() throws Exception {
        ViewCacheKey<AndroidAppLinkList> cacheKey = viewCache.getCacheKey(AndroidAppLinkList.class);
        
        String json = viewCache.getView(cacheKey, false, BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS, () -> {
            AndroidAppLinkList links = new AndroidAppLinkList();
            List<Study> studies = studyService.getStudies();
            for(Study study : studies) {
                CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
                
                AppConfig config = appConfigService.getAppConfigForUser(context, false);
                if (config != null) {
                    for (AndroidAppLink link : config.getAndroidAppLinks()) {
                        links.add(new AndroidAppSiteAssociation(link));
                    }
                }
            }
            return links;
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    public Result appleAppLinks() throws Exception {
        ViewCacheKey<AppleAppSiteAssociation> cacheKey = viewCache.getCacheKey(AppleAppSiteAssociation.class);
        
        String json = viewCache.getView(cacheKey, false, BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS, () -> {
            List<AppleAppLink> links = Lists.newArrayList();
            List<Study> studies = studyService.getStudies();
            for(Study study : studies) {
                CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
                
                AppConfig config = appConfigService.getAppConfigForUser(context, false);
                if (config != null) {
                    links.addAll(config.getAppleAppLinks());
                }
            }
            return new AppleAppSiteAssociation(links);
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
}
