package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.AppleAppLink;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.newrelic.agent.deps.com.google.common.collect.ImmutableList;

/**
 * A model of the JSON we return to iOS clients. For iOS clients, one object encompasses all the 
 * links for all the studies. 
 */
public class AppleAppSiteAssociation {
    
    public static class AppLinks {
        private List<AppleAppLink> details;
        
        private AppLinks(List<AppleAppLink> details) {
            this.details = details;
        }
        @SuppressWarnings("unused")
        @JsonProperty("apps") // needed for a private accessor
        public List<String> getApps() {
            return ImmutableList.of();
        }
        @SuppressWarnings("unused")
        @JsonProperty("details") // needed for a private accessor
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
