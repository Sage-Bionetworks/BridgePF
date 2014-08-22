package controllers;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.BackfillService;

import play.mvc.Result;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;

public class BackfillController extends BaseController {

    private Client stormpathClient;

    private BackfillService backfillService;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setBackfillService(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    public Result stormpathUserConsent() throws Exception {
        checkUser();
        int total = backfillService.stormpathUserConsent();
        return okResult("Done. " + total + " accounts backfilled.");
    }

    public Result dynamoUserConsent() throws Exception {
        checkUser();
        int total = backfillService.dynamoUserConsent();
        return okResult("Done. " + total + " records backfilled.");
    }

    private void checkUser() throws Exception {
        UserSession session = getSession();
        Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);
        if (!account.isMemberOfGroup("backfill")) {
            throw new BridgeServiceException(account.getUsername() + " not allowed to perform backfill.", 403);
        }
    }
}
