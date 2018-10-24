package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.services.AppConfigElementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class AppConfigElementsController extends BaseController {

    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private AppConfigElementService service;
    
    @Autowired
    final void setAppConfigElementService(AppConfigElementService service) {
        this.service = service;
    }
    
    public Result getMostRecentElements(String includeDeletedStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        boolean includeDeleted = Boolean.valueOf(includeDeletedStr);
        
        List<AppConfigElement> elements = service.getMostRecentElements(session.getStudyIdentifier(), includeDeleted);
        
        ResourceList<AppConfigElement> resourceList = new ResourceList<AppConfigElement>(elements)
                .withRequestParam(INCLUDE_DELETED_PARAM, includeDeleted);
        return okResult(resourceList);
    }
    
    public Result createElement() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        AppConfigElement element = parseJson(request(), AppConfigElement.class);
        
        VersionHolder version = service.createElement(session.getStudyIdentifier(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return createdResult(version);
    }

    public Result getElementRevisions(String id, String includeDeletedStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        boolean includeDeleted = Boolean.valueOf(includeDeletedStr);
        
        List<AppConfigElement> elements = service.getElementRevisions(session.getStudyIdentifier(), id, includeDeleted);
        
        ResourceList<AppConfigElement> resourceList = new ResourceList<AppConfigElement>(elements)
                .withRequestParam(INCLUDE_DELETED_PARAM, includeDeleted);
        return okResult(resourceList);
    }
    
    public Result getMostRecentElement(String id) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfigElement element = service.getMostRecentElement(session.getStudyIdentifier(), id);
        return okResult(element);
    }

    public Result getElementRevision(String id, String revisionString) {
        UserSession session = getSessionEitherConsentedOrInRole(DEVELOPER);
        Long revision = BridgeUtils.getLongOrDefault(revisionString, null);
        if (revision == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        
        AppConfigElement element = service.getElementRevision(session.getStudyIdentifier(), id, revision);
        
        return okResult(element);
    }

    public Result updateElementRevision(String id, String revisionString) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Long revision = BridgeUtils.getLongOrDefault(revisionString, null);
        if (revision == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        
        AppConfigElement element = parseJson(request(), AppConfigElement.class);
        element.setId(id);
        element.setRevision(revision);
        
        VersionHolder holder = service.updateElementRevision(session.getStudyIdentifier(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return okResult(holder);
    }
    
    public Result deleteElementAllRevisions(String id, String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementAllRevisionsPermanently(session.getStudyIdentifier(), id);
        } else {
            service.deleteElementAllRevisions(session.getStudyIdentifier(), id);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return okResult("App config element deleted.");
    }
    
    public Result deleteElementRevision(String id, String revisionString, String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Long revision = BridgeUtils.getLongOrDefault(revisionString, null);
        if (revision == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementRevisionPermanently(session.getStudyIdentifier(), id, revision);
        } else {
            service.deleteElementRevision(session.getStudyIdentifier(), id, revision);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return okResult("App config element revision deleted.");
    }
    
}
