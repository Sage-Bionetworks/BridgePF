package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.JSON_MIME_TYPE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class AppConfigController extends BaseController {

    private AppConfigService appConfigService;
    
    private ViewCache viewCache;

    @Autowired
    final void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @Resource(name = "genericViewCache")
    public void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    
    private ViewCacheKey<AppConfig> getCacheKey(CriteriaContext context) {
        ClientInfo info = context.getClientInfo();
        String appVersion = info.getAppVersion() == null ? "0" : Integer.toString(info.getAppVersion());
        String osName = info.getOsName() == null ? "" : info.getOsName();
        String studyId = context.getStudyIdentifier().getIdentifier();
        // Languages. We don't provide a UI to create filtering criteria for these, but if they are 
        // set through our API, and they are included in the Accept-Language header, we will filter on 
        // them, so it's important they be part of the key
        String langs = BridgeUtils.SPACE_JOINER.join(context.getLanguages());
        
        return viewCache.getCacheKey(AppConfig.class, appVersion, osName, langs, studyId);
    }
    
    private String getCacheKeyOfSet(StudyIdentifier studyId) {
        return studyId.getIdentifier()+":AppConfigList";
    }
    
    public Result getStudyAppConfig(String studyId) {
        Study study = studyService.getStudy(studyId);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(getLanguagesFromAcceptLanguageHeader())
                .withClientInfo(getClientInfoFromUserAgentHeader())
                .withStudyIdentifier(study.getStudyIdentifier())
                .build();
        
        ViewCacheKey<AppConfig> cacheKey = getCacheKey(context);
        String json = viewCache.getView(cacheKey, () -> {
            AppConfig appConfig = appConfigService.getAppConfigForUser(context, true);
            // So we can delete all the relevant cached versions, keep track of them under the GUID
            cacheProvider.addCacheKeyToSet(getCacheKeyOfSet(study.getStudyIdentifier()), cacheKey.getKey());
            return appConfig;
        });
        return ok(json).as(JSON_MIME_TYPE);
    }
    
    public Result getAppConfigs() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<AppConfig> results = appConfigService.getAppConfigs(session.getStudyIdentifier());
        
        return okResult(results);
    }
    
    public Result getAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = appConfigService.getAppConfig(session.getStudyIdentifier(), guid);
        
        return okResult(appConfig);
    }
    
    public Result createAppConfig() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(request(), AppConfig.class);
        
        AppConfig created = appConfigService.createAppConfig(session.getStudyIdentifier(), appConfig);
        cacheProvider.removeSetOfCacheKeys(getCacheKeyOfSet(session.getStudyIdentifier()));
        
        return createdResult(new GuidVersionHolder(created.getGuid(), created.getVersion()));
    }
    
    public Result updateAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(request(), AppConfig.class);
        appConfig.setGuid(guid);
        
        AppConfig updated = appConfigService.updateAppConfig(session.getStudyIdentifier(), appConfig);
        cacheProvider.removeSetOfCacheKeys(getCacheKeyOfSet(session.getStudyIdentifier()));

        return okResult(new GuidVersionHolder(updated.getGuid(), updated.getVersion()));
    }
    
    public Result deleteAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        appConfigService.deleteAppConfig(session.getStudyIdentifier(), guid);
        cacheProvider.removeSetOfCacheKeys(getCacheKeyOfSet(session.getStudyIdentifier()));

        return okResult("App config deleted.");
    }
}
