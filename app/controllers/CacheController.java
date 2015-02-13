package controllers;

import java.util.Set;

import org.sagebionetworks.bridge.services.CacheService;

import play.mvc.Result;

public class CacheController extends BaseController {

    private CacheService cacheService;
    
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }
    
    public Result listItems() throws Exception {
        getAuthenticatedAdminSession();
        
        Set<String> keys = cacheService.listItems();
        return okResult(keys);
    }
    
    public Result removeItem(String cacheKey) {
        getAuthenticatedAdminSession();
        
        cacheService.removeItem(cacheKey);
        return ok("Item removed from cache.");
    }

}
