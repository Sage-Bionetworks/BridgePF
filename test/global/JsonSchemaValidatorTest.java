package global;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Tracker;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSchemaValidatorTest {

    private void validate(String string) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);

        FileSystemResource resource = new FileSystemResource(
                "conf/schemas/bloodpressure.json");
        Tracker tracker = new Tracker();
        tracker.setSchemaFile(resource);
        new JsonSchemaValidator().validate(tracker, node);
    }

    @Test
    public void schemaValidationFails() throws Exception {
        try {
            validate("{\"k1\":\"v1\"}");
            fail();
        } catch (BridgeServiceException e) {
            String message = e.getMessage();

            assertTrue(
                    "Message shows properties not allowed by schema",
                    message.contains("object instance has properties which are not allowed by the schema: [\"k1\"]"));
            assertTrue(
                    "Message shows properties that are required and missing",
                    message.contains("object has missing required properties ([\"data\",\"endDate\",\"startDate\"])."));
        }
    }

    @Test
    public void schemaValidationSucceedsWithRecordId() throws Exception {
        validate("{ \"recordId\": \"asdf-zxfv-sdfg\", \"key\": \"1:1:asdf\", \"startDate\":\"date+time\", \"endDate\":\"date+time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
    }

    @Test
    public void schemaValidationSucceedsWithoutRecordId() throws Exception {
        validate("{ \"key\": \"1:1:asdf\", \"startDate\":\"date-time\", \"endDate\":\"date-time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
    }

}
