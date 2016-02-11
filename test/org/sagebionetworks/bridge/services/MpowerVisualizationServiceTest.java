package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.MpowerVisualizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;

public class MpowerVisualizationServiceTest {
    private static final String DUMMY_HEALTH_CODE = "dummyHealthCode";

    @Test
    public void specifiedStartAndEndDates() {
        // mock dao
        MpowerVisualizationDao mockDao = mock(MpowerVisualizationDao.class);
        JsonNode mockViz = mock(JsonNode.class);
        when(mockDao.getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-06"), LocalDate.parse("2016-02-08")))
                .thenReturn(mockViz);

        // set up service
        MpowerVisualizationService svc = new MpowerVisualizationService();
        svc.setMpowerVisualizationDao(mockDao);

        // execute and validate
        JsonNode result = svc.getVisualization(DUMMY_HEALTH_CODE, "2016-02-06", "2016-02-08");
        assertSame(mockViz, result);
    }

    @Test
    public void defaultStartAndEndDates() {
        // mock now
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2016-02-08T09:00-0800").getMillis());

        try {
            // mock dao
            MpowerVisualizationDao mockDao = mock(MpowerVisualizationDao.class);
            JsonNode mockViz = mock(JsonNode.class);
            when(mockDao.getVisualization(DUMMY_HEALTH_CODE, LocalDate.parse("2016-02-07"),
                    LocalDate.parse("2016-02-07"))).thenReturn(mockViz);

            // set up service
            MpowerVisualizationService svc = new MpowerVisualizationService();
            svc.setMpowerVisualizationDao(mockDao);

            // execute and validate
            JsonNode result = svc.getVisualization(DUMMY_HEALTH_CODE, null, null);
            assertSame(mockViz, result);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test(expected = BadRequestException.class)
    public void malformedStartDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, "this is not a date",
                "2016-02-08");
    }

    @Test(expected = BadRequestException.class)
    public void timestampAsStartDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE,
                "2016-02-06T18:00-0800", "2016-02-08");
    }

    @Test(expected = BadRequestException.class)
    public void malformedEndDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, "2016-02-06",
                "also not a date");
    }

    @Test(expected = BadRequestException.class)
    public void timestampAsEndDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, "2016-02-06",
                "2016-02-08T23:00-0800");
    }

    @Test(expected = BadRequestException.class)
    public void startDateAfterEndDate() {
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, "2016-02-09",
                "2016-02-08");
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWide() {
        // Two months is definitely too wide. Don't need exactly 45 days.
        new MpowerVisualizationService().getVisualization(DUMMY_HEALTH_CODE, "2016-01-01",
                "2016-03-01");
    }
}
