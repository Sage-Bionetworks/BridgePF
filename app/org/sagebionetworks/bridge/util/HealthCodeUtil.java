package org.sagebionetworks.bridge.util;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import org.sagebionetworks.bridge.crypto.BridgeEncryptor;

public class HealthCodeUtil {
    @SuppressWarnings("resource")
    public static void main(String[] args) {
        // usage
        if (args.length == 0) {
            System.out.println("Usage: HealthCodeUtil [encrypt/decrypt] [input string]");
            System.exit(1);
            return;
        }

        // args
        String method = args[0];
        String input = args[1];

        // load spring beans
        ApplicationContext springCtx = new ClassPathXmlApplicationContext("application-context.xml");
        BridgeEncryptor encryptor = springCtx.getBean("healthCodeEncryptor", BridgeEncryptor.class);

        // encrypt/decrypt
        String output;
        switch (method) {
            case "encrypt":
                output = encryptor.encrypt(input);
                break;
            case "decrypt":
                output = encryptor.decrypt(input);
                break;
            default:
                throw new IllegalArgumentException("Unknown method " + method);
        }

        // output
        System.out.println("Output: " + output);
    }
}
