package controllers;

import java.util.Set;

import org.sagebionetworks.bridge.services.CacheAdminService;

import play.mvc.Result;

public class CacheAdminController extends BaseController {

    private CacheAdminService cacheAdminService;
    
    public void setCacheAdminService(CacheAdminService cacheService) {
        this.cacheAdminService = cacheService;
    }
    
    public Result listItems() throws Exception {
        getAuthenticatedAdminSession();
        
        Set<String> keys = cacheAdminService.listItems();
        return okResult(keys);
    }
    
    public Result removeItem(String cacheKey) {
        getAuthenticatedAdminSession();
        
        cacheAdminService.removeItem(cacheKey);
        return ok("Item removed from cache.");
    }

}
