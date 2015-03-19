package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceImplTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private UserProfileServiceImpl profileService;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        testUser = helper.createUser(UserProfileServiceImplTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile userProfile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        userProfile.setFirstName("Test");
        userProfile.setLastName("Powers");
        userProfile.setPhone("123-456-7890");
        userProfile.setAttribute("can_be_recontacted", "true");
        
        // You cannot reset a field through the attributes. These should do NOTHING.
        userProfile.setAttribute("firstName", "NotTest");
        userProfile.setAttribute("lastName", "NotPowers");
        userProfile.setAttribute("phone", "Not123-456-7890");
        userProfile.setAttribute("email", "NotEmail");
        userProfile.setAttribute("username", "NotUsername");

        User updatedUser = profileService.updateProfile(testUser.getStudy(), testUser.getUser(), userProfile);
        userProfile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        
        assertEquals("Test", updatedUser.getFirstName());
        assertEquals("Powers", updatedUser.getLastName());
        assertEquals("Test", userProfile.getFirstName());
        assertEquals("Powers", userProfile.getLastName());
        assertEquals(testUser.getEmail(), userProfile.getEmail());
        assertEquals(testUser.getUsername(), userProfile.getUsername());
        assertEquals("123-456-7890", userProfile.getPhone());
        assertEquals("true", userProfile.getAttribute("can_be_recontacted"));
        assertNull(userProfile.getAttribute("some_unknown_attribute"));
    }
    
    @Test(expected = BridgeServiceException.class)
    public void getErrorIfNoConsentEmailSet() {
        testUser.getStudy().setConsentNotificationEmail(null);
        profileService.sendStudyParticipantRoster(testUser.getStudy());
    }
    
    @Test
    public void cannotBreakProfileWithBadFirstLastName() {
        UserProfile userProfile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        userProfile.setFirstName("");
        userProfile.setLastName(null);
        profileService.updateProfile(testUser.getStudy(), testUser.getUser(), userProfile);
        
        userProfile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        assertNull(userProfile.getFirstName());
        assertNull(userProfile.getLastName());
    }
    
    @Test
    public void canRetrieveStudyParticipants() {
        // Do not send email when this service is called.
        ExecutorService service = mock(ExecutorService.class);
        profileService.setExecutorService(service);
        
        // All we an really do here is verify no error is thrown.
        profileService.sendStudyParticipantRoster(testUser.getStudy());
        
        verify(service).submit(any(Runnable.class));
    }
}
