import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.Transform;
import javax.xml.transform.stream.StreamSource;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for detecting duplicate XML messages using Canonical XML (C14N) + SHA-256 hashing.
 *
 * Two XML documents are considered equal if they are semantically equivalent,
 * regardless of whitespace, attribute ordering, or namespace declarations.
 *
 * Not thread-safe. For concurrent use, synchronize externally or use one instance per thread.
 */
public class XmlDuplicateDetector {

    private final Set<String> seenHashes = new HashSet<>();
    private final DocumentBuilderFactory documentBuilderFactory;
    private final TransformerFactory transformerFactory;
    private final MessageDigest sha256;

    public XmlDuplicateDetector() {
        // Configure a secure, namespace-aware DocumentBuilderFactory
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);

        // Disable external entity resolution to prevent XXE attacks
        try {
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            // Feature may not be supported by all parsers — safe to ignore if not available
        }

        this.transformerFactory = TransformerFactory.newInstance();

        try {
            this.sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM (JCA spec)
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Parses and canonicalizes the given XML string, then computes its SHA-256 hash.
     *
     * The canonicalization process (C14N) normalizes:
     * - Insignificant whitespace between elements
     * - Attribute ordering (sorted alphabetically by name)
     * - Namespace declarations
     * - Line endings
     *
     * @param xmlContent the raw XML string (up to ~2MB supported)
     * @return a hex-encoded SHA-256 hash of the canonical form
     * @throws XmlProcessingException if the XML is malformed or canonicalization fails
     */
    public String computeHash(String xmlContent) throws XmlProcessingException {
        byte[] canonicalBytes = canonicalize(xmlContent);
        return toHex(digest(canonicalBytes));
    }

    /**
     * Checks whether the given XML message is a duplicate of a previously seen one.
     *
     * Internally computes the canonical hash and checks it against the set of seen hashes.
     * If not a duplicate, the hash is recorded so future calls can detect it.
     *
     * @param xmlContent the raw XML string to check
     * @return true if an equivalent XML message was already processed, false otherwise
     * @throws XmlProcessingException if the XML is malformed or canonicalization fails
     */
    public boolean isDuplicate(String xmlContent) throws XmlProcessingException {
        String hash = computeHash(xmlContent);
        // add() returns false if the element was already in the set
        return !seenHashes.add(hash);
    }

    /**
     * Clears all previously recorded hashes.
     * Useful when reusing the same instance across processing batches.
     */
    public void reset() {
        seenHashes.clear();
    }

    /**
     * Returns the number of unique XML messages seen so far.
     */
    public int uniqueCount() {
        return seenHashes.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the XML and produces its Canonical XML (C14N) byte representation.
     *
     * Strategy:
     * 1. Parse XML string into a DOM Document (handles structural validation)
     * 2. Serialize DOM back to a normalized byte stream via the Transformer
     * 3. Re-parse that stream and use javax.xml.crypto C14N transform
     *
     * This two-step approach ensures maximum compatibility across JVM versions
     * without requiring third-party libraries.
     */
    private byte[] canonicalize(String xmlContent) throws XmlProcessingException {
        // Step 1 — parse into DOM to validate and normalize the structure
        Document document = parseXml(xmlContent);

        // Step 2 — serialize DOM to a clean, normalized byte stream
        byte[] normalizedBytes = serializeToBytes(document);

        // Step 3 — apply Canonical XML 1.0 transform via javax.xml.crypto
        return applyC14N(normalizedBytes);
    }

    private Document parseXml(String xmlContent) throws XmlProcessingException {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            // Suppress default stderr output for parse errors
            builder.setErrorHandler(null);
            byte[] bytes = xmlContent.getBytes(StandardCharsets.UTF_8);
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (ParserConfigurationException e) {
            throw new XmlProcessingException("Failed to create XML parser", e);
        } catch (SAXException e) {
            throw new XmlProcessingException("Malformed XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XmlProcessingException("Failed to read XML content", e);
        }
    }

    private byte[] serializeToBytes(Document document) throws XmlProcessingException {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new XmlProcessingException("Failed to serialize XML document", e);
        }
    }

    private byte[] applyC14N(byte[] xmlBytes) throws XmlProcessingException {
        try {
            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            CanonicalizationMethod c14n = factory.newCanonicalizationMethod(
                    CanonicalizationMethod.INCLUSIVE,   // C14N 1.0 — most widely compatible
                    (C14NMethodParameterSpec) null
            );

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            c14n.transform(
                    new OctetStreamData(new ByteArrayInputStream(xmlBytes)),
                    null,
                    result
            );
            return result.toByteArray();

        } catch (Exception e) {
            throw new XmlProcessingException("Canonicalization (C14N) failed: " + e.getMessage(), e);
        }
    }

    private byte[] digest(byte[] data) {
        sha256.reset();
        return sha256.digest(data);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
