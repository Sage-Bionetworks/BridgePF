package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.AndroidAppLink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

/**
 * A model of the JSON we produce to associate app links on Android with our server. The JSON 
 * that is returned is an array of these objects, one for every study that has defined app 
 * links.
 * 
 * @see https://developer.android.com/training/app-links/verify-site-associations.html
 */
public final class AndroidAppSiteAssociation {
    public static final String RELATION = "delegate_permission/common.handle_all_urls";
    private static final List<String> RELATION_LIST = Lists.newArrayList(RELATION);
    
    private final AndroidAppLink target;
    
    @JsonCreator
    public AndroidAppSiteAssociation(@JsonProperty("target") AndroidAppLink target) {
        this.target = target;
    }
    public List<String> getRelation() {
        return RELATION_LIST; // this never appears to change.
    }
    @JsonProperty("target")
    public AndroidAppLink getTarget() {
        return target;
    }
}
