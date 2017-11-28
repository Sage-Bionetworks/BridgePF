package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.AppleAppLink;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

/**
 * A model of the JSON we return to iOS clients. For iOS clients, one object encompasses all the 
 * links for all the studies.
 * 
 * @see https://developer.apple.com/library/content/documentation/General/Conceptual/AppSearch/UniversalLinks.html
 */
public class AppleAppSiteAssociation {
    
    public static class AppLinks {
        private List<AppleAppLink> details;
        
        private AppLinks(List<AppleAppLink> details) {
            this.details = details;
        }
        public List<String> getApps() {
            return ImmutableList.of();
        }
        public List<AppleAppLink> getDetails() {
            return details;
        }
    }
    
    private final AppLinks appLinks;
    
    public AppleAppSiteAssociation(List<AppleAppLink> details) {
        this.appLinks = new AppLinks(details);
    }
    
    @JsonProperty("applinks")
    public AppLinks getAppLinks() {
        return appLinks;
    }
    
}
