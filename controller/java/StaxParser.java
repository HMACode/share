import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

public class XmlHasher {

    public String hashXmlInput(String xmlInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlInput));

            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        digest.update((reader.getName().toString() + ":").getBytes(StandardCharsets.UTF_8));
                        int attrCount = reader.getAttributeCount();
                        if (attrCount > 0) {
                            Map<String, String> attributes = new TreeMap<>();
                            for (int i = 0; i < attrCount; i++) {
                                attributes.put(reader.getAttributeName(i).toString(), reader.getAttributeValue(i));
                            }
                            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                                digest.update((entry.getKey() + "=" + entry.getValue() + ";").getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        String text = reader.getText().trim();
                        if (!text.isEmpty()) {
                            digest.update((text + "|").getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        digest.update(("~" + reader.getName().toString() + ":").getBytes(StandardCharsets.UTF_8));
                        break;
                }
            }
            reader.close();

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate XML hash", e);
        }
    }
}
