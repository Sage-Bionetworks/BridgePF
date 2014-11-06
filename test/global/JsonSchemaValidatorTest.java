package global;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.models.Tracker;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class JsonSchemaValidatorTest {

    private ProcessingReport validate(String string) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);

        FileSystemResource resource = new FileSystemResource(
                "conf/schemas/bloodpressure.json");
        Tracker tracker = new Tracker();
        tracker.setSchemaFile(resource);
        return new JsonSchemaValidator().validate(tracker, node);
    }

    @Test
    public void schemaValidationFails() throws Exception {
        ProcessingReport report = validate("{\"k1\":\"v1\"}");
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void schemaValidationSucceedsWithGuid() throws Exception {
        ProcessingReport report = validate("{ \"guid\": \"asdf-zxfv-sdfg\", \"key\": \"1:1:asdf\", \"startDate\":\"date+time\", \"endDate\":\"date+time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void schemaValidationSucceedsWithoutGuid() throws Exception {
        ProcessingReport report = validate("{ \"key\": \"1:1:asdf\", \"startDate\":\"date-time\", \"endDate\":\"date-time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
        assertTrue("Report should have no errors", report.isSuccess());
    }

}
