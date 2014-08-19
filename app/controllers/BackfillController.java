package controllers;

import org.sagebionetworks.bridge.backfill.HealthCodeBackfill;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserSession;

import play.mvc.Result;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;

public class BackfillController extends BaseController {

    private Client stormpathClient;

    private HealthCodeBackfill healthCodeBackfill;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setHealthCodeBackfill(HealthCodeBackfill healthCodeBackfill) {
        this.healthCodeBackfill = healthCodeBackfill;
    }

    public Result resetHealthId() throws Exception {
        UserSession session = getSession();
        Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);
        if (!account.isMemberOfGroup("backfill")) {
            throw new BridgeServiceException(account.getUsername() + " not allowed to perform backfill.", 403);
        }
        int total = healthCodeBackfill.resetHealthId();
        return okResult("Done. " + total + " accounts backfilled.");
    }
}
