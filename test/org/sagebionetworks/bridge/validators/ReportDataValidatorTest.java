package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.reports.ReportData;

public class ReportDataValidatorTest {
    
    private ReportDataValidator validator;

    @Before
    public void before() throws Exception {
        validator = new ReportDataValidator();
    }

    @Test
    public void cannotHaveTwoDates() {
        ReportData data = ReportData.create();
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
}
