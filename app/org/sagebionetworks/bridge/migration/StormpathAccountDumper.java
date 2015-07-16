package org.sagebionetworks.bridge.migration;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.stormpath.StormpathAccountDao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

class StormpathAccountDumper {

    public static void main(String[] args) throws IOException {
        StormpathAccountDumper saDumper = new StormpathAccountDumper(Environment.PROD, "heroku");
        saDumper.dump("stormpath-account-prod");
    }

    StormpathAccountDumper(final Environment env, final String user) {
        Utils.checkEnvironmentUser(env, user);
    }

    void dump(final String outputFile) throws FileNotFoundException, IOException {

        final BridgeConfig config = BridgeConfigFactory.getConfig();
        System.out.println("Environment: " + config.getEnvironment());
        System.out.println("Healthcode Key: " + config.getHealthCodeKey());
        System.out.println("Stormpath App HREF: " + config.getStormpathApplicationHref());

        final Path file = Paths.get(outputFile);
        if (Files.exists(file)) {
            throw new RuntimeException("File " + outputFile + " already exists.");
        }

        try (FileOutputStream fos = new FileOutputStream(Files.createFile(file).toFile());
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                Writer writer = new BufferedWriter(osw)) {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNodeFactory nodeFactory = new JsonNodeFactory(false);
            final StormpathAccountDao spDao = Utils.loadAppContext().getBean(StormpathAccountDao.class);
            Iterator<Account> accounts = spDao.getAllAccounts();
            while (accounts.hasNext()) {
                Account account = accounts.next();
                if ("cardiovascular".equals(account.getStudyIdentifier().getIdentifier())) {
                    ObjectNode node = nodeFactory.objectNode();
                    node.put("id", account.getId());
                    node.put("email", account.getEmail());
                    node.put("healthId", account.getHealthId());
                    node.put("username", account.getUsername());
                    node.put("study", account.getStudyIdentifier().getIdentifier());
                    node.put("env", config.getEnvironment().name());
                    writer.write(mapper.writeValueAsString(node));
                    writer.write("\r\n");
                }
                try {
                    Thread.sleep(20L);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
