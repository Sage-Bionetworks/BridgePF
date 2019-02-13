package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

import com.google.common.collect.ImmutableSet;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdValidatorTest {
    
    private ExternalIdValidator validatorV4;
    
    @Mock
    private SubstudyService substudyService;
    
    @Before
    public void before() {
        validatorV4 = new ExternalIdValidator(substudyService, false);
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void validates() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudy-id"))
                .withCallerStudyId(TEST_STUDY).build());
        
        when(substudyService.getSubstudy(TEST_STUDY, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "one-id");
        id.setSubstudyId("substudy-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void validatesV3() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TEST_STUDY).build());
        
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "one-id");
        
        ExternalIdValidator validatorV3 = new ExternalIdValidator(substudyService, true);
        Validate.entityThrowingException(validatorV3, id);
    }
    
    @Test
    public void identifierCannotBeNull() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, null);
        assertValidatorMessage(validatorV4, id, "identifier", "cannot be null or blank");
    }
    
    @Test
    public void identifierCannotBeBlank() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "\t");
        assertValidatorMessage(validatorV4, id, "identifier", "cannot be null or blank");
    }
    
    @Test
    public void identifierMustMatchPattern1() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "two words");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'two words' must contain only digits, letters, underscores and dashes");
    }
    
    @Test
    public void identifierMustMatchPattern2() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "<Funky>Markup");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'<Funky>Markup' must contain only digits, letters, underscores and dashes");
    }

    @Test
    public void identifierMustMatchPattern3() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "And a \ttab character");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'And a \ttab character' must contain only digits, letters, underscores and dashes");
    }
    
    @Test
    public void substudyIdCannotBeNull() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "identifier");
        assertValidatorMessage(validatorV4, id, "substudyId", "cannot be null or blank");
    }
    
    @Test
    public void substudyIdCannotBeBlank() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "identifier");
        id.setSubstudyId("   ");
        assertValidatorMessage(validatorV4, id, "substudyId", "cannot be null or blank");
    }
    
    @Test
    public void substudyIdMustBeValid() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "identifier");
        id.setSubstudyId("not-real");
        assertValidatorMessage(validatorV4, id, "substudyId", "is not a valid substudy");
    }
    
    @Test
    public void substudyIdCanBeAnythingForAdmins() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN))
                .withCallerStudyId(TEST_STUDY).build());
        
        when(substudyService.getSubstudy(TEST_STUDY, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "one-id");
        id.setSubstudyId("substudy-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void substudyIdMustMatchCallersSubstudies() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyB"))
                .withCallerStudyId(TEST_STUDY).build());
        
        when(substudyService.getSubstudy(TEST_STUDY, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "one-id");
        id.setSubstudyId("substudy-id");
        
        assertValidatorMessage(validatorV4, id, "substudyId", "is not a valid substudy");
    }
    
    @Test(expected = BadRequestException.class)
    public void studyIdCannotBeBlank() { 
        ExternalIdentifier.create(new StudyIdentifierImpl("   "), "one-id");
    }
    
    @Test
    public void studyIdMustBeCallersStudyId() { 
        // This fails because we have not set a context with this study ID.
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "one-id");
        
        assertValidatorMessage(validatorV4, id, "studyId", "is not a valid study");
    }
}
