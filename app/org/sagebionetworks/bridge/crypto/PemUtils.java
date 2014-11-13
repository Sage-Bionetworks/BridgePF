package org.sagebionetworks.bridge.crypto;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.SystemUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class PemUtils {

    public static String toPem(X509Certificate cert) throws CertificateEncodingException {
        checkNotNull(cert, "Cert cannot be null.");
        Base64 encoder = new Base64(Base64.PEM_CHUNK_SIZE);
        return BEGIN_CERT + NEW_LINE + encoder.encodeToString(cert.getEncoded()) + END_CERT;
    }

    public static X509Certificate loadCertificateFromPem(String pem) {
        checkNotNull(pem, "Pem string cannot be null.");
        Security.addProvider(new BouncyCastleProvider());
        try {
            String[] lines = readLines(pem);
            byte[] decoded = decode(lines, BEGIN_CERT, END_CERT);
            X509CertificateHolder certHolder = new X509CertificateHolder(decoded);
            return new JcaX509CertificateConverter().setProvider(BcCmsConstants.PROVIDER).getCertificate(certHolder);
        } catch(IOException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toPem(PrivateKey privateKey) throws CertificateEncodingException {
        checkNotNull(privateKey, "Private key cannot be null.");
        Base64 encoder = new Base64(Base64.PEM_CHUNK_SIZE);
        return BEGIN_PRIVATE_KEY + NEW_LINE + encoder.encodeToString(privateKey.getEncoded()) + END_PRIVATE_KEY;
    }

    public static PrivateKey loadPrivateKeyFromPem(String pem) {
        checkNotNull(pem, "Pem string cannot be null.");
        Security.addProvider(new BouncyCastleProvider());
        try {
            String[] lines = readLines(pem);
            byte[] decoded = decode(lines, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(BcCmsConstants.KEY_PAIR_ALGO, BcCmsConstants.PROVIDER);
            return keyFactory.generatePrivate(keySpec);
        } catch(IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] decode(String[] lines, String begin, String end) {
        checkNotNull(lines, "Lines cannot be null");
        checkArgument(lines.length >= 2, "Too few lines.");
        StringBuilder base64Encoded = new StringBuilder();
        boolean addLine = false;
        for (String line : lines) {
            if (end.equals(line)) {
                break;
            }
            if (addLine) {
                base64Encoded.append(line);
                base64Encoded.append(NEW_LINE);
            }
            if (begin.equals(line)) {
                addLine = true;
            }
        }
        Base64 encoder = new Base64(Base64.PEM_CHUNK_SIZE);
        byte[] decoded = encoder.decode(base64Encoded.toString());
        return decoded;
    }

    private static String[] readLines(String text) throws IOException {
        return text.split("\\r?\\n");
    }

    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERT = "-----END CERTIFICATE-----";
    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END RSA PRIVATE KEY-----";
    private static final String NEW_LINE = SystemUtils.LINE_SEPARATOR;
}
