package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Backfill;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.services.BackfillService;

import play.mvc.Result;

public class BackfillController extends AdminController {

    private BackfillService backfillService;
    public void setBackfillService(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    public Result backfill(String name) throws Exception {
        checkUser();
        Backfill backfill = backfillService.backfill(name);
        return okResult(backfill);
    }

    private void checkUser() throws Exception {
        User user = getAuthenticatedAdminSession().getUser();
        if (!user.isInRole(BridgeConstants.BACKFILL_GROUP)) {
            throw new UnauthorizedException();
        }
    }
}
