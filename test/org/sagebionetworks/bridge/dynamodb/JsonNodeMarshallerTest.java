package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class JsonNodeMarshallerTest {

    private static final JsonNodeMarshaller MARSHALLER = new JsonNodeMarshaller();

    @Test
    public void testSerialization() throws Exception {
        Criteria criteria = new DynamoCriteria();
        criteria.setKey("key");
        criteria.setLanguage("en");
        criteria.setAllOfGroups(Sets.newHashSet("A","B"));
        criteria.setNoneOfGroups(Sets.newHashSet("C","D"));
        criteria.setMinAppVersion("Android", 2);
        criteria.setMaxAppVersion("Android", 10);
        
        Subpopulation subpop = new DynamoSubpopulation();
        subpop.setCriteria(criteria);
        subpop.setGuidString("subpop-guid");
        subpop.setName("Subpopulation Name");
        subpop.setRequired(true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(subpop);
        
        String json = MARSHALLER.convert(node);
        JsonNode deser = MARSHALLER.unconvert(json);
        
        assertEquals(node.toString(), deser.toString());
        
        // To simplify testing, convert the deserialized node to a subpopulation
        Subpopulation copy = BridgeObjectMapper.get().treeToValue(deser, Subpopulation.class);
        // The key is not serialized, so copy it over for the tests below
        copy.getCriteria().setKey(subpop.getCriteria().getKey());
        
        // It has serialized/deserialized the object structure, including criteria
        assertEquals(subpop.getName(), copy.getName());
        assertEquals(subpop.getGuidString(), copy.getGuidString());
        assertTrue(copy.isRequired());
        assertEquals(subpop.getCriteria().getLanguage(), copy.getCriteria().getLanguage());
        assertEquals(subpop.getCriteria(), copy.getCriteria());
    }
}
