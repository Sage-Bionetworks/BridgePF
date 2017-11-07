package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountIdTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountId.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void twoAccountIdsAreTheSameWithTheSameData() {
        assertTrue(AccountId.forId(TEST_STUDY_IDENTIFIER, "id")
                .equals(AccountId.forId(TEST_STUDY_IDENTIFIER, "id")));
        
        assertTrue(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE)
                .equals(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE)));
        
        assertTrue(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email")
                .equals(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email")));
    }
    
    @Test
    public void factoryMethodsWork() {
        String number = PHONE.getNumber();
        assertEquals("one", AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getId());
        assertEquals("one", AccountId.forEmail(TEST_STUDY_IDENTIFIER, "one").getEmail());
        assertEquals(number, AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE).getPhone().getNumber());
    }
    
    @Test(expected = NullPointerException.class)
    public void idAccessorThrows() {
        AccountId.forEmail(TEST_STUDY_IDENTIFIER, "one").getId();
    }
    
    @Test(expected = NullPointerException.class)
    public void emailAccessorThrows() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getEmail();
    }
    
    @Test(expected = NullPointerException.class)
    public void phoneAccessorThrows() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getPhone();
    }
    
    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoEmail() {
        AccountId.forEmail(TEST_STUDY_IDENTIFIER, null);
    }

    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoId() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoPhone() {
        AccountId.forPhone(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudy() {
        AccountId.forId(null, "id");
    }

    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrEmail() {
        AccountId.forEmail(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrId() {
        AccountId.forId(null, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrPhone() {
        AccountId.forPhone(null, null);
    }
    
    @Test
    public void getValuesWithoutGuards() {
        AccountId id = AccountId.forId("test-study", "id");
        
        AccountId accountId = id.getUnguardedAccountId();
        assertEquals("test-study", accountId.getStudyId());
        assertEquals("id", accountId.getId());
        assertNull(accountId.getEmail());
        assertNull(accountId.getPhone());
    }
}
