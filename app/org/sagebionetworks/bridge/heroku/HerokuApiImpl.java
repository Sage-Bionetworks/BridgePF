package org.sagebionetworks.bridge.heroku;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.HerokuApi;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;

public class HerokuApiImpl implements HerokuApi {

    private BridgeConfig config;
    
    private final CloseableHttpClient client = HttpClientBuilder.create().build();
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    
    @Override
    public String registerDomainForStudy(String identifier) {
        String studyHostname = config.getFullStudyHostname(identifier);
        String herokuAppName = config.getHerokuAppName();
        String url = String.format("https://api.heroku.com/apps/%s/domains", herokuAppName);
        
        HttpEntity entity = new StringEntity("{\"hostname\":\""+studyHostname+"\"}", ContentType.APPLICATION_JSON);
        HttpPost post = new HttpPost(url);
        post.setHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
        post.setHeader("Accept", "application/vnd.heroku+json; version=3");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(entity);
        try (CloseableHttpResponse response = client.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 201) {
                String msg = String.format("%s: could not register domain name %s with Heroku app %s",
                        Integer.toString(statusCode), studyHostname, herokuAppName);
                throw new BridgeServiceException(msg);
            }
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        return getDomainRegistrationForStudy(identifier);
    }

    @Override
    public String getDomainRegistrationForStudy(String identifier) {
        String herokuAppName = config.getHerokuAppName();
        String studyHostname = config.getFullStudyHostname(identifier); 
        String url = String.format("https://api.heroku.com/apps/%s/domains", herokuAppName);
        
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
        get.setHeader("Accept", "application/vnd.heroku+json; version=3");
        try (CloseableHttpResponse response = client.execute(get)) {
            
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String msg = String.format("%s: Could not get domains for Heroku app %s", Integer.toString(statusCode),
                        herokuAppName);
                throw new BridgeServiceException(msg);
            }
            
            String domainString = EntityUtils.toString(response.getEntity());
            ArrayNode array = (ArrayNode)BridgeObjectMapper.get().readTree(domainString);
            for (int i=0; i < array.size(); i++) {
                String hostname = array.get(i).get("hostname").asText();
                if (hostname.equals(studyHostname)) {
                    return hostname;
                }
            }
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        return null;
    }

    @Override
    public void unregisterDomainForStudy(String identifier) {
        String studyHostname = config.getFullStudyHostname(identifier);
        String herokuAppName = config.getHerokuAppName();
        String url = String.format("https://api.heroku.com/apps/%s/domains/%s", herokuAppName, studyHostname);

        HttpDelete delete = new HttpDelete(url);
        delete.setHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
        delete.setHeader("Accept", "application/vnd.heroku+json; version=3");
        try (CloseableHttpResponse response = client.execute(delete)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String msg = String.format("%s: could not register domain name %s with Heroku app %s",
                        Integer.toString(statusCode), studyHostname, herokuAppName);
                throw new BridgeServiceException(msg);
            }
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
    }
    
}
