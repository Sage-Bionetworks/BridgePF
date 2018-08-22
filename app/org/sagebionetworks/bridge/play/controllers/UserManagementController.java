package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.UserAdminService;

@Controller
public class UserManagementController extends BaseController {

    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;

    @Autowired
    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result signInForAdmin() throws Exception {
        SignIn originSignIn = parseJson(request(), SignIn.class);
        
        // Persist the requested study
        StudyIdentifier originStudy = new StudyIdentifierImpl(originSignIn.getStudyId());
        
        // Adjust the sign in so it is always done against the API study.
        SignIn signIn = new SignIn.Builder().withSignIn(originSignIn)
                .withStudy(BridgeConstants.API_STUDY_ID_STRING).build();        
        
        Study study = studyService.getStudy(signIn.getStudyId());
        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());

        // We do not check consent, but do verify this is an administrator
        UserSession session = authenticationService.signIn(study, context, signIn);

        if (!session.isInRole(Roles.ADMIN)) {
            authenticationService.signOut(session);
            throw new UnauthorizedException("Not an admin account");
        }
        
        // Now act as if the user is in the study that was requested
        sessionUpdateService.updateStudy(session, originStudy);
        setCookieAndRecordMetrics(session);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result changeStudyForAdmin() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);

        // The only part of this payload we care about is the study property
        SignIn signIn = parseJson(request(), SignIn.class);
        String studyId = signIn.getStudyId();

        // Verify it's correct
        Study study = studyService.getStudy(studyId);
        sessionUpdateService.updateStudy(session, study.getStudyIdentifier());
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result createUser() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        UserSession userSession = userAdminService.createUser(study, participant, null, false, consent);

        return createdResult(UserSessionInfo.toJSON(userSession));
    }

    /**
     * admin api used to create consent/not-consent user for given study
     * nearly identical to createUser() one
     * @param studyId
     * @return
     * @throws Exception
     */
    public Result createUserWithStudyId(String studyId) throws Exception {
        getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(studyId);

        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        userAdminService.createUser(study, participant, null, false, consent);

        return createdResult("User created.");
    }

    public Result deleteUser(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userAdminService.deleteUser(study, userId);
        
        return okResult("User deleted.");
    }
}
