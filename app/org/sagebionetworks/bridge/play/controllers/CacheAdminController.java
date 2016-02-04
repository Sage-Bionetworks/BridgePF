package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import java.util.Set;

import org.sagebionetworks.bridge.services.CacheAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class CacheAdminController extends BaseController {

    private CacheAdminService cacheAdminService;

    @Autowired
    public void setCacheAdminService(CacheAdminService cacheService) {
        this.cacheAdminService = cacheService;
    }
    
    public Result listItems() throws Exception {
        getAuthenticatedSession(ADMIN);
        
        Set<String> keys = cacheAdminService.listItems();
        return okResult(keys);
    }
    
    public Result removeItem(String cacheKey) {
        getAuthenticatedSession(ADMIN);
        
        cacheAdminService.removeItem(cacheKey);
        return okResult("Item removed from cache.");
    }

}
