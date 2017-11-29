package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AndroidAppLinkTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AndroidAppLink.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        AndroidAppLink link = new AndroidAppLink("namespace", "packageName", Lists.newArrayList("fingerprint"));

        JsonNode node = MAPPER.valueToTree(link);
        assertEquals(3, node.size());
        assertEquals("namespace", node.get("namespace").textValue());
        assertEquals("packageName", node.get("package_name").textValue());
        assertEquals("fingerprint", node.get("sha256_cert_fingerprints").get(0).textValue());
        
        AndroidAppLink deser = MAPPER.readValue(node.toString(), AndroidAppLink.class);
        assertEquals("namespace", deser.getNamespace());
        assertEquals("packageName", deser.getPackageName());
        assertEquals(1, deser.getFingerprints().size());
        assertEquals("fingerprint", deser.getFingerprints().get(0));    }
    
}
