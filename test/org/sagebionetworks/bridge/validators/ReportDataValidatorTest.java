package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class ReportDataValidatorTest {
    
    private ReportDataValidator validator = ReportDataValidator.INSTANCE;

    @Test
    public void validWorks() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withStudyIdentifier(new StudyIdentifierImpl("test-study")).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        Validate.entityThrowingException(validator, data);
    }
    
    @Test
    public void cannotHaveTwoDates() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withStudyIdentifier(new StudyIdentifierImpl("test-study")).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        try {
            Validate.entityThrowingException(validator, data);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ReportData must include a localDate or dateTime, but not both"));
        }
    }
    
    @Test
    public void mustHaveOneDate() {
        ReportData data = ReportData.create();
        data.setData(TestUtils.getClientData());
        try {
            Validate.entityThrowingException(validator, data);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ReportData must include a localDate or dateTime"));
        }
    }
    
    @Test
    public void keyIsRequired() {
        ReportData data = ReportData.create();
        data.setData(TestUtils.getClientData());
        
        TestUtils.assertValidatorMessage(validator, data, "key", "is required");
    }
    
    @Test
    public void dataIsRequired() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withStudyIdentifier(new StudyIdentifierImpl("test-study")).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        
        TestUtils.assertValidatorMessage(validator, data, "data", "is required");
    }
    
    @Test
    public void keyIsValidated() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withStudyIdentifier(new StudyIdentifierImpl("test-study")).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        
        TestUtils.assertValidatorMessage(validator, data, "identifier", "cannot be missing or blank");
    }
}
