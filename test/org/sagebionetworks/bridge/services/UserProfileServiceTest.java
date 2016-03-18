package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private UserProfileService profileService;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        testUser = helper.getBuilder(UserProfileServiceTest.class).build();
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile profile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        profile.setFirstName("Test");
        profile.setLastName("Powers");
        profile.setAttribute("phone", "123-456-7890");
        profile.setAttribute("can_be_recontacted", "true");
        
        // You cannot reset a field through the attributes. These should do NOTHING.
        profile.setAttribute("firstName", "NotTest");
        profile.setAttribute("lastName", "NotPowers");
        profile.setAttribute("email", "NotEmail");

        profileService.updateProfile(testUser.getStudy(), testUser.getUser(), profile);
        profile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        
        assertEquals("First name is persisted", "Test", profile.getFirstName());
        assertEquals("Last name is persisted", "Powers", profile.getLastName());
        assertEquals("Email is persisted", testUser.getEmail(), profile.getEmail());
        assertEquals("Phone is persisted", "123-456-7890", profile.getAttribute("phone"));
        assertEquals("Attribute is persisted", "true", profile.getAttribute("can_be_recontacted"));
        assertNull("Unknown attribute is null", profile.getAttribute("some_unknown_attribute"));
    }

    @Test
    public void cannotBreakProfileWithBadNameValues() {
        UserProfile profile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        profile.setFirstName("");
        profile.setLastName(null);
        profileService.updateProfile(testUser.getStudy(), testUser.getUser(), profile);

        profile = profileService.getProfile(testUser.getStudy(), testUser.getEmail());
        assertNull(profile.getFirstName());
        assertNull(profile.getLastName());
    }
}
