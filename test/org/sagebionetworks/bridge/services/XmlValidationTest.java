package org.sagebionetworks.bridge.services;

import java.io.File;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXParseException;

/**
 * Makes sure the consent templates are valid XMLs. This is needed for converting them
 * into PDF documents.
 */
public class XmlValidationTest {

    private static DocumentBuilder parser;

    @BeforeClass
    public static void beforeClass() throws ParserConfigurationException {
        parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    @Test
    public void test() throws Exception {
        // The 'conf' folder should be added to the build path of your IDE
        final URI uri = this.getClass().getResource("/email-templates").toURI();
        final Path dir = Paths.get(uri);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
            for (Path file : dirStream) {
                validate(file.toFile());
            }
        }
    }

    private void validate(final File file) throws Exception {
        try {
            parser.parse(file);
        } catch (SAXParseException e) {
            Assert.fail(file.getName() + " at line " + e.getLineNumber() + ": " + e.getMessage() );
        }
    }
}
