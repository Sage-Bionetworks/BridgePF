package org.sagebionetworks.bridge.play.controllers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

/** Play controller for User Data Download requests. */
@Controller
public class UserDataDownloadController extends BaseController {
    private UserDataDownloadService userDataDownloadService;

    /** Service handler for User Data Download requests. */
    @Autowired
    public void setUserDataDownloadService(UserDataDownloadService userDataDownloadService) {
        this.userDataDownloadService = userDataDownloadService;
    }

    /**
     * Play handler for requesting user data. User must be authenticated and consented. (Otherwise, they couldn't have
     * any data to download to begin with.)
     */
    @BodyParser.Of(BodyParser.Empty.class)
    public Result requestUserData(String startDate, String endDate) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        StudyIdentifier studyIdentifier = session.getStudyIdentifier();
        
        // At least for now, if the user does not have a verified email address, do not allow this service.
        StudyParticipant participant = session.getParticipant();
        if (participant.getEmail() == null || participant.getEmailVerified() != Boolean.TRUE) {
            throw new BadRequestException("Cannot request user data, account has no verified email address.");
        }
        
        DateRange dateRange;
        if (isNotBlank(startDate) && isNotBlank(endDate)) {
            dateRange = new DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate));
        } else {
            dateRange = parseJson(request(), DateRange.class);    
        }
        userDataDownloadService.requestUserData(studyIdentifier, session.getParticipant().getId(), dateRange);
        return acceptedResult("Request submitted.");
    }
}
