package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Backfill;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.services.backfill.BackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import play.mvc.Result;

public class BackfillController extends BaseController implements ApplicationContextAware  {

    private final Logger logger = LoggerFactory.getLogger(BackfillService.class);

    private ApplicationContext appContext;

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }

    public Result backfill(String name) throws Exception {
        checkUser();
        BackfillService backfillService = appContext.getBean(name, BackfillService.class);
        logger.info("Backfilling " + name);
        Backfill backfill = backfillService.backfill();
        logger.info("Backfilling " + name + " done.");
        return okResult(backfill);
    }

    private void checkUser() throws Exception {
        User user = getAuthenticatedAdminSession().getUser();
        if (!user.isInRole(BridgeConstants.BACKFILL_GROUP)) {
            throw new UnauthorizedException();
        }
    }
}
