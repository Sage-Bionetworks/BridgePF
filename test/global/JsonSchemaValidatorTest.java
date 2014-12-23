package global;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class JsonSchemaValidatorTest {
    private static final String RECORD_WRAPPER = "{\n" +
            "   \"startDate\":\"2014-12-22T12:00:00-08:00\",\n" +
            "   \"endDate\":\"2014-12-22T15:00:00-08:00\",\n" +
            "   \"data\":%s\n" +
            "}";
    private static final String BAD_DATA = String.format(RECORD_WRAPPER, "{\"bad\":\"data\"}");

    private ProcessingReport validate(String trackerName, String string) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);

        FileSystemResource resource = new FileSystemResource(
                "conf/schemas/" + trackerName + ".json");
        Tracker tracker = new Tracker();
        tracker.setSchemaFile(resource);
        return new JsonSchemaValidator().validate(tracker, node);
    }

    @Test
    public void schemaValidationFails() throws Exception {
        ProcessingReport report = validate("bloodpressure", "{\"k1\":\"v1\"}");
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void schemaValidationSucceedsWithGuid() throws Exception {
        ProcessingReport report = validate("bloodpressure", "{ \"guid\": \"asdf-zxfv-sdfg\", \"key\": \"1:1:asdf\", \"startDate\":\"date+time\", \"endDate\":\"date+time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void schemaValidationSucceedsWithoutGuid() throws Exception {
        ProcessingReport report = validate("bloodpressure", "{ \"key\": \"1:1:asdf\", \"startDate\":\"date-time\", \"endDate\":\"date-time\", \"data\":{ \"systolic\":120, \"diastolic\":80 } }");
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void cardioWalkFails() throws Exception {
        ProcessingReport report = validate("cardio-walk-tracker", BAD_DATA);
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void cardioWalkSucceeds() throws Exception {
        String jsonData = "{\n" +
                "   \"distance\":\"sample JSON blob\",\n" +
                "   \"heartRateBPM\":\"sample JSON blob\",\n" +
                "   \"stepCount\":\"sample JSON blob\"\n" +
                "}";
        ProcessingReport report = validate("cardio-walk-tracker", String.format(RECORD_WRAPPER, jsonData));
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void diabetesGlucoseFails() throws Exception {
        ProcessingReport report = validate("diabetes-glucose-tracker", BAD_DATA);
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void diabetesGlucoseSucceeds() throws Exception {
        String jsonData = "{\n" +
                "   \"indexPath\":\"sample string\",\n" +
                "   \"period\":\"sample string\",\n" +
                "   \"scheduledHour\":42,\n" +
                "   \"timeOfDay\":\"sample string\",\n" +
                "   \"value\":42\n" +
                "}";
        ProcessingReport report = validate("diabetes-glucose-tracker", String.format(RECORD_WRAPPER, jsonData));
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void iosSurveyFails() throws Exception {
        ProcessingReport report = validate("ios-survey-tracker", BAD_DATA);
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void iosSurveySucceeds() throws Exception {
        String jsonData = "{\n" +
                "   \"answer\":\"any type\",\n" +
                "   \"endDate\":\"sample string\",\n" +
                "   \"identifier\":\"sample string\",\n" +
                "   \"questionType\":\"sample string\",\n" +
                "   \"startDate\":\"sample string\"\n" +
                "}";
        ProcessingReport report = validate("ios-survey-tracker", String.format(RECORD_WRAPPER, jsonData));
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void parkinsonTappingFails() throws Exception {
        ProcessingReport report = validate("parkinson-tapping-tracker", BAD_DATA);
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void parkinsonTappingSucceeds() throws Exception {
        String jsonData = "{\n" +
                "   \"ContainerSize\":\"sample string\",\n" +
                "   \"LeftTargetFrame\":\"sample string\",\n" +
                "   \"ParkinsonIntervalTappingRecords\":\"sample JSON blob\",\n" +
                "   \"RightTargetFrame\":\"sample string\"\n" +
                "}";
        ProcessingReport report = validate("parkinson-tapping-tracker", String.format(RECORD_WRAPPER, jsonData));
        assertTrue("Report should have no errors", report.isSuccess());
    }

    @Test
    public void parkinsonWalkFails() throws Exception {
        ProcessingReport report = validate("parkinson-walk-tracker", BAD_DATA);
        assertTrue("Report should have errors", !report.isSuccess());
    }

    @Test
    public void parkinsonWalkSucceeds() throws Exception {
        String jsonData = "{\n" +
                "   \"items\":\"sample JSON blob\"\n" +
                "}";
        ProcessingReport report = validate("parkinson-walk-tracker", String.format(RECORD_WRAPPER, jsonData));
        assertTrue("Report should have no errors", report.isSuccess());
    }
}
