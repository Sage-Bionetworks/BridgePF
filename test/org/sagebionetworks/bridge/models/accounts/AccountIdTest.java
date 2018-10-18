package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

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
        
        assertTrue(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI")
                .equals(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI")));
        
        assertTrue(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID")
                .equals(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID")));
    }
    
    @Test
    public void testToString() {
        assertEquals("AccountId [studyId=api, credential=user-id]", AccountId.forId(TEST_STUDY_IDENTIFIER, "user-id").toString());
        
        assertEquals("AccountId [studyId=api, credential=Phone [regionCode=US, number=9712486796]]", AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE).toString());
        
        assertEquals("AccountId [studyId=api, credential=email]", AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email").toString());
        
        assertEquals("AccountId [studyId=api, credential=HEALTH_CODE]", AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI").toString());
        
        assertEquals("AccountId [studyId=api, credential=EXTID]", AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID").toString());
    }
    
    @Test
    public void factoryMethodsWork() {
        String number = PHONE.getNumber();
        assertEquals("one", AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getId());
        assertEquals("one", AccountId.forEmail(TEST_STUDY_IDENTIFIER, "one").getEmail());
        assertEquals(number, AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE).getPhone().getNumber());
        assertEquals("ABC-DEF", AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "ABC-DEF").getHealthCode());
        assertEquals("EXTID", AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID").getExternalId());
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
    public void healthCodeAccessorThrows() {
        AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "one").getEmail();
    }
    
    @Test(expected = NullPointerException.class)
    public void externalIdAccessorThrows() {
        AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "one").getEmail();
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
    public void cannotCreateIdObjectWithNoHealthCode() {
        AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expected = NullPointerException.class)
    public void cannotCreateIdObjectWithNoExternalId() {
        AccountId.forExternalId(TEST_STUDY_IDENTIFIER, null);
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
        assertNull(accountId.getHealthCode());
        assertNull(accountId.getExternalId());
    }
    

    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'study':'api'," +
                "'email': '"+TestConstants.EMAIL+"'," +
                "'healthCode': 'someHealthCode', "+
                "'externalId': 'someExternalId', "+
                "'phone': {'number': '"+TestConstants.PHONE.getNumber()+"', "+
                "'regionCode':'"+TestConstants.PHONE.getRegionCode()+"'}}");
        
        AccountId identifier = BridgeObjectMapper.get().readValue(json, AccountId.class);
        
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, identifier.getStudyId());
        assertEquals(TestConstants.EMAIL, identifier.getEmail());
        assertEquals("someExternalId", identifier.getExternalId());
        assertEquals("someHealthCode", identifier.getHealthCode());
        assertEquals(TestConstants.PHONE.getNumber(), identifier.getPhone().getNumber());
        assertEquals(TestConstants.PHONE.getRegionCode(), identifier.getPhone().getRegionCode());
    }
    
}
