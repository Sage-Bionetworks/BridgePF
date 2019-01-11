package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class PostalCodeConstraintsTest {

    @Test
    public void canSerialize() throws Exception {
        PostalCodeConstraints pcc = new PostalCodeConstraints();
        pcc.setCountryCode(CountryCode.US);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(pcc);
        assertEquals("postalcode", node.get("dataType").textValue());
        assertEquals("us", node.get("countryCode").textValue());
        assertEquals("PostalCodeConstraints", node.get("type").textValue());
        int len = BridgeConstants.SPARSE_ZIP_CODE_PREFIXES.size();
        assertEquals(len, node.get("sparseZipCodePrefixes").size());
        for (int i=0; i < len; i++) {
            String zipCodePrefix = BridgeConstants.SPARSE_ZIP_CODE_PREFIXES.get(i);
            assertEquals(zipCodePrefix, node.get("sparseZipCodePrefixes").get(i).textValue());
        }
        
        // Verify that the mapper correctly selects the subclass we want.
        PostalCodeConstraints deser = (PostalCodeConstraints) BridgeObjectMapper
                .get().readValue(node.toString(), Constraints.class);
        assertEquals(CountryCode.US, deser.getCountryCode());
    }
}
