package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class AndroidAppSiteAssociationTest {
    
    @Test
    public void producesCorrectJson() throws Exception {
        AndroidAppLink link = new AndroidAppLink("namespace", "package", Lists.newArrayList("fingerprint"));
        
        AndroidAppSiteAssociation assoc = new AndroidAppSiteAssociation(link);
        
        JsonNode node = new ObjectMapper().valueToTree(assoc);
        assertEquals(AndroidAppSiteAssociation.RELATION, node.get("relation").get(0).textValue());
        JsonNode target = node.get("target");
        assertEquals("namespace", target.get("namespace").textValue());
        assertEquals("package", target.get("package_name").textValue());
        assertEquals("fingerprint", target.get("sha256_cert_fingerprints").get(0).textValue());
    }
    
}
