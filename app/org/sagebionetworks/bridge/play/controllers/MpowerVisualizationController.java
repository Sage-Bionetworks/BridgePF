package org.sagebionetworks.bridge.play.controllers;

import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * Controller for mPower visualization. This is mPower-specific for now to get things out the door. We'll figure out if
 * and how to generalize this later.
 */
@Controller
public class MpowerVisualizationController extends BaseController {
    public static final Set<String> DATA_KEY_SET = ImmutableSet.of("standingPreMedication", "standingPostMedication",
            "tappingPreMedication", "tappingPostMedication", "voicePreMedication", "voicePostMedication",
            "walkingPreMedication", "walkingPostMedication");

    /** Gets the mPower visualization for the given start and end dates, inclusive. */
    public Result getVisualization(String startDate, String endDate) {
        getAuthenticatedAndConsentedSession();

        // parse start and end dates
        LocalDate startDateObj;
        try {
            startDateObj = LocalDate.parse(startDate);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid start date " + startDate);
        }

        LocalDate endDateObj;
        try {
            endDateObj = LocalDate.parse(endDate);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid end date " + endDate);
        }

        // To prevent browning out the back end, the date range must be <= 45 days.
        Period dateRange = new Period(startDateObj, endDateObj, PeriodType.days());
        if (dateRange.getDays() > 45) {
            throw new BadRequestException("Date range cannot exceed 45 days, startDate=" + startDate + ", endDate=" +
                    endDate);
        }

        // TODO: replace test version with real implementation
        // Initial dummy implementation just returns random values
        ObjectNode parentNode = BridgeObjectMapper.get().createObjectNode();
        for (LocalDate curDate = startDateObj; !curDate.isAfter(endDateObj); curDate = curDate.plusDays(1)) {
            ObjectNode dateNode = BridgeObjectMapper.get().createObjectNode();

            for (String oneDataKey : DATA_KEY_SET) {
                dateNode.put(oneDataKey, Math.random());
            }

            parentNode.set(curDate.toString(), dateNode);
        }

        return ok(parentNode);
    }
}
