package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;

@Component
public class UrlShortenerService {

    private static final String WEBSERVICES_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    public String shortenUrl(String url, int expireInSeconds) {
        Preconditions.checkNotNull(url);
        
        // We are using a relatively short, randomized token, so verify it isn't being
        // used by another URL. If it's being used by the same URL, don't reset the 
        // expiration
        String token = null;
        CacheKey cacheKey = null;
        String foundValue = null;
        do {
            token = getToken();
            cacheKey = CacheKey.shortenUrl(token);
            foundValue = cacheProvider.getObject(cacheKey, String.class);
        } while(foundValue != null && !foundValue.equals(url));
        
        if (foundValue == null) {
            cacheProvider.setObject(cacheKey, url, expireInSeconds);
        }
        return WEBSERVICES_URL + "/r/" + token;
    }
    
    public String retrieveUrl(String token) {
        Preconditions.checkNotNull(token);
        
        CacheKey cacheKey = CacheKey.shortenUrl(token);
        return cacheProvider.getObject(cacheKey, String.class);
    }
    
    protected String getToken() {
        return SecureTokenGenerator.NAME_SCOPE_INSTANCE.nextToken();
    }
}
