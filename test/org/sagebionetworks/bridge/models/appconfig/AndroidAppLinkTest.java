package org.sagebionetworks.bridge.models.appconfig;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class AndroidAppLinkTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void test() throws Exception {
        AndroidAppLink link = new AndroidAppLink("namespace", "packageName", Lists.newArrayList("fingerprint"));

        JsonNode node = MAPPER.valueToTree(link);
        assertEquals("namespace", node.get("namespace").textValue());
        assertEquals("packageName", node.get("package_name").textValue());
        assertEquals("fingerprint", node.get("sha256_cert_fingerprints").get(0).textValue());
        
        AndroidAppLink deser = MAPPER.readValue(node.toString(), AndroidAppLink.class);
        assertEquals("namespace", deser.getNamespace());
        assertEquals("packageName", deser.getPackageName());
        assertEquals(1, deser.getFingerprints().size());
        assertEquals("fingerprint", deser.getFingerprints().get(0));
    }
    
}
