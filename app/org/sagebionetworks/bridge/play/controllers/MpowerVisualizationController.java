package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;
import org.sagebionetworks.bridge.services.MpowerVisualizationService;

/**
 * Controller for mPower visualization. This is mPower-specific for now to get things out the door. We'll figure out if
 * and how to generalize this later.
 */
@Controller
public class MpowerVisualizationController extends BaseController {
    private MpowerVisualizationService mpowerVisualizationService;

    /** mPower Visualization Service */
    @Autowired
    final void setMpowerVisualizationService(MpowerVisualizationService mpowerVisualizationService) {
        this.mpowerVisualizationService = mpowerVisualizationService;
    }

    /** Gets the mPower visualization for the given start and end dates, inclusive. */
    public Result getVisualization(String startDate, String endDate) {
        // get health code from session
        UserSession session = getAuthenticatedSession();
        String healthCode = session.getHealthCode();

        // parse string dates into Joda dates
        LocalDate startDateObj = parseDateHelper(startDate);
        LocalDate endDateObj = parseDateHelper(endDate);

        // call through to the service
        JsonNode vizColNode = mpowerVisualizationService.getVisualization(healthCode, startDateObj, endDateObj);
        return ok(vizColNode);
    }

    // Helper method to parse dates. Returns null on null or blank strings.
    private static LocalDate parseDateHelper(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        } else {
            try {
                return DateUtils.parseCalendarDate(dateStr);
            } catch (RuntimeException ex) {
                throw new BadRequestException("invalid date " + dateStr);
            }
        }
    }

    /** Writes the mPower visualization. The request body includes both the visualization data and metadata. */
    public Result writeVisualization() {
        getAuthenticatedSession(Roles.WORKER);
        MpowerVisualization viz = parseJson(request(), MpowerVisualization.class);
        mpowerVisualizationService.writeVisualization(viz);
        return created("Visualization created.");
    }
}
