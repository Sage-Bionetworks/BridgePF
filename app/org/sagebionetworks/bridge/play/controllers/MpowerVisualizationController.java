package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
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
        UserSession session = getAuthenticatedAndConsentedSession();
        String healthCode = session.getUser().getHealthCode();

        JsonNode vizColNode = mpowerVisualizationService.getVisualization(healthCode, startDate, endDate);
        return ok(vizColNode);
    }
}
