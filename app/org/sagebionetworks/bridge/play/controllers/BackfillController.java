package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.backfill.BackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class BackfillController extends BaseController implements ApplicationContextAware  {

    private final Logger logger = LoggerFactory.getLogger(BackfillService.class);

    private ApplicationContext appContext;

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }

    public Result backfill(final String name) throws Exception {
        return ok(views.html.backfill.render(name));
    }

    public Result start(final String name) throws Exception {
        final String user = checkUser();
        final BackfillService backfillService = appContext.getBean(name, BackfillService.class);
        Chunks<String> chunks = new StringChunks() {
                @Override
                public void onReady(final Chunks.Out<String> out) {
                    BackfillChunksAdapter chunksAdapter = new BackfillChunksAdapter(out);
                    backfillService.backfill(user, name, chunksAdapter);
                }
            };
        logger.info("Backfill " + name + " submitted.");
        return ok(chunks);
    }

    private String checkUser() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        return session.getStudyParticipant().getEmail();
    }
}
