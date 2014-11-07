package org.sagebionetworks.bridge.crypto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;

public class CertificateUtils {

    public static String toPem(X509Certificate cert) throws CertificateEncodingException {
        if (cert == null) {
            throw new IllegalArgumentException("Cert cannot be null.");
        }
        Base64 encoder = new Base64(Base64.PEM_CHUNK_SIZE);
        return BEGIN_CERT + NEW_LINE + encoder.encodeToString(cert.getEncoded()) + END_CERT;
    }

    public static X509Certificate fromPem(String pem) {
        try {
            List<String> lines = readLines(pem);
            if (lines == null || lines.size() < 2) {
                throw new IllegalArgumentException("Pem string has too few lines.");
            }
            StringBuilder base64Encoded = new StringBuilder();
            boolean addLine = false;
            for (String line : lines) {
                if (END_CERT.equals(line)) {
                    break;
                }
                if (addLine) {
                    base64Encoded.append(line);
                    base64Encoded.append(NEW_LINE);
                }
                if (BEGIN_CERT.equals(line)) {
                    addLine = true;
                }
            }
            Base64 encoder = new Base64(Base64.PEM_CHUNK_SIZE);
            byte[] decoded = encoder.decode(base64Encoded.toString());
            X509CertificateHolder certHolder = new X509CertificateHolder(decoded);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
        } catch(IOException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> readLines(String text) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader bufferedReader = null;
        StringReader stringReader = null;
        try {
            stringReader = new StringReader(text);
            bufferedReader = new BufferedReader(stringReader);
            String line = bufferedReader.readLine();
            while (line != null) {
                lines.add(line);
                line = bufferedReader.readLine();
            }
        } finally {
            if (stringReader != null) {
                stringReader.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
        return lines;
    }

    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERT = "-----END CERTIFICATE-----";
    private static final String NEW_LINE = "\n";
}
