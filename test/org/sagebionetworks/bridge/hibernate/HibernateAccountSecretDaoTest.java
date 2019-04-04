package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;

@RunWith(MockitoJUnitRunner.class)
public class HibernateAccountSecretDaoTest {

    private static final DateTime CREATED_ON = DateTime.parse("2018-10-10T03:10:30.000Z");
    private static final String ACCOUNT_ID = "id";
    private static final String TOKEN = "token";
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
        //when(dao.generateHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, TOKEN)).thenReturn(TOKEN);
        DateTimeUtils.setCurrentMillisFixed(CREATED_ON.getMillis());
    }
    
    @After
    public void after() { 
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void createSecret() {
        dao.createSecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN);
        
        verify(helper).create(secretCaptor.capture(), eq(null));
        
        AccountSecret secret = secretCaptor.getValue();
        assertEquals(ACCOUNT_ID, secret.getAccountId());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, secret.getAlgorithm());
        assertNotEquals(TOKEN, secret.getHash());
        assertEquals(AccountSecretType.REAUTH, secret.getType());
        assertEquals(CREATED_ON, secret.getCreatedOn());
    }
    
    @Test
    public void verifySecret() throws Exception {
        makeResults(TOKEN);
        
        AccountSecret secret = dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN, ROTATIONS).get();
        assertNotEquals(secret.getHash(), TOKEN); // it is encrypted but matches
        assertNotNull(secret);
        
        verify(helper).queryGet(eq(HibernateAccountSecretDao.GET_QUERY), paramsCaptor.capture(), 
                eq(0), eq(ROTATIONS), eq(HibernateAccountSecret.class));
        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals(ACCOUNT_ID, params.get("accountId"));
        assertEquals(AccountSecretType.REAUTH, params.get("type"));
    }
    
    @Test
    public void verifySecretSucceedsAfterRotation() throws Exception {
        makeResults("ABC", TOKEN, "DEF");
        
        assertTrue(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN, ROTATIONS).isPresent());
    }
    
    @Test
    public void verifySecretFailsOnEmpty() throws Exception {
        makeResults();
        
        assertFalse(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN, ROTATIONS).isPresent());
    }
    
    @Test
    public void verifySecretFailsWhenNoMatch() throws Exception {
        makeResults("ABC", "DEF");
        
        assertFalse(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN, ROTATIONS).isPresent());
    }
    
    @Test
    public void verifySecretExceptionIsSuppressed() throws Exception {
        PasswordAlgorithm algorithm = Mockito.mock(PasswordAlgorithm.class);
        
        HibernateAccountSecret secret = Mockito.mock(HibernateAccountSecret.class);
        when(secret.getAlgorithm()).thenReturn(algorithm);
        
        when(algorithm.checkHash(any(), any())).thenThrow(new InvalidKeyException());
        
        when(helper.queryGet(eq(HibernateAccountSecretDao.GET_QUERY), any(), 
                eq(0), eq(ROTATIONS), eq(HibernateAccountSecret.class)))
            .thenReturn(ImmutableList.of(secret));
        
        assertFalse(dao.verifySecret(AccountSecretType.REAUTH, ACCOUNT_ID, TOKEN, ROTATIONS).isPresent());
    }
    
    @Test
    public void removeSecrets() {
        dao.removeSecrets(AccountSecretType.REAUTH, ACCOUNT_ID);
        
        verify(helper).query(eq(HibernateAccountSecretDao.DELETE_QUERY), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals(ACCOUNT_ID, params.get("accountId"));
        assertEquals(AccountSecretType.REAUTH, params.get("type"));
    }
    
    @Test(expected = BridgeServiceException.class)
    public void generateHashConvertsException() throws Exception {
        PasswordAlgorithm algorithm = Mockito.mock(PasswordAlgorithm.class);
        when(algorithm.generateHash(any())).thenThrow(new InvalidKeyException());
        
        dao.generateHash(algorithm, "whatever");
    }
    
    private List<HibernateAccountSecret> makeResults(String... hashes) throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        List<HibernateAccountSecret> results = new ArrayList<>();
        for (String hash : hashes) {
            HibernateAccountSecret secret = new HibernateAccountSecret();
            secret.setAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
            secret.setHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.generateHash(hash));
            results.add(secret);
        }
        when(helper.queryGet(eq(HibernateAccountSecretDao.GET_QUERY), any(), 
                eq(0), eq(ROTATIONS), eq(HibernateAccountSecret.class))).thenReturn(results);
        return results;
    }
}
