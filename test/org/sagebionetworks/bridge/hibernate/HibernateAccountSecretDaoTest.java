package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;

@RunWith(MockitoJUnitRunner.class)
public class HibernateAccountSecretDaoTest {

    private static final DateTime CREATED_ON = DateTime.parse("2018-10-10T03:10:30.000Z");
    private static final String ACCOUNT_ID = "id";
    private static final String PLAINTEXT = "plainttext";
    private static final String HASH = "hash";
    private static final int ROTATIONS = 4;
    
    @Spy
    private HibernateAccountSecretDao dao;
    
    @Mock
    private HibernateHelper helper;
    
    @Captor
    ArgumentCaptor<AccountSecret> secretCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Before
    public void before() {
        dao.setHibernateHelper(helper);
        when(dao.generateHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, PLAINTEXT)).thenReturn(HASH);
        DateTimeUtils.setCurrentMillisFixed(CREATED_ON.getMillis());
    }
    
    @After
    public void after() { 
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void createSecret() {
        dao.createSecret(AccountSecretType.REAUTH, ACCOUNT_ID, PLAINTEXT);
        
        verify(helper).create(secretCaptor.capture(), eq(null));
        AccountSecret secret = secretCaptor.getValue();
        assertEquals(ACCOUNT_ID, secret.getAccountId());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, secret.getAlgorithm());
        assertEquals(HASH, secret.getHash());
        assertEquals(AccountSecretType.REAUTH, secret.getType());
        assertEquals(CREATED_ON, secret.getCreatedOn());
    }
    
    @Test
    public void verifySecret() {
        makeResults(HASH);
        
        assertTrue(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, PLAINTEXT, ROTATIONS).isPresent());
        
        verify(helper).queryGet(eq(HibernateAccountSecretDao.GET_QUERY), paramsCaptor.capture(), 
                eq(0), eq(ROTATIONS), eq(HibernateAccountSecret.class));
        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals(ACCOUNT_ID, params.get("accountId"));
        assertEquals(AccountSecretType.REAUTH, params.get("type"));
    }
    
    @Test
    public void verifySecretSucceedsAfterRotation() {
        makeResults("ABC", HASH, "DEF");
        
        assertTrue(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void verifySecretFailsOnEmpty() {
        makeResults();
        
        assertFalse(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void verifySecretFailsWhenNoMatch() {
        makeResults("ABC", "DEF");
        
        assertFalse(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void accountContainsReauthToken() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        account.setReauthTokenHash(HASH);
        makeResults("ABC", "DEF", "GHI");
        
        assertTrue(dao.verifySecret(account, AccountSecretType.REAUTH, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void accountContainsReauthTokenButItsRotatedOut() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        account.setReauthTokenHash(HASH);
        makeResults("ABC", "DEF", "GHI", "JKL");
        
        assertFalse(dao.verifySecret(account, AccountSecretType.REAUTH, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void accountDoesNotContainReauthTokenButVerifySucceeds() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        makeResults("ABC", "DEF", HASH);
        
        assertTrue(dao.verifySecret(account, AccountSecretType.REAUTH, PLAINTEXT, ROTATIONS).isPresent());
    }
    
    @Test
    public void removeSecrets() {
        dao.removeSecrets(AccountSecretType.REAUTH, ACCOUNT_ID);
        
        verify(helper).query(eq(HibernateAccountSecretDao.DELETE_QUERY), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals(ACCOUNT_ID, params.get("accountId"));
        assertEquals(AccountSecretType.REAUTH, params.get("type"));
    }
    
    private List<HibernateAccountSecret> makeResults(String... hashes) {
        List<HibernateAccountSecret> results = new ArrayList<>();
        for (String hash : hashes) {
            HibernateAccountSecret secret = new HibernateAccountSecret();
            secret.setAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
            secret.setHash(hash);
            results.add(secret);
        }
        when(helper.queryGet(eq(HibernateAccountSecretDao.GET_QUERY), any(), 
                eq(0), eq(ROTATIONS), eq(HibernateAccountSecret.class))).thenReturn(results);
        return results;
    }
}
