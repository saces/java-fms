/**
 * TrustList XML Parser
 */
package plugins.FMS.xml;

import java.io.InputStream;
import java.util.LinkedList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;

public class TrustListXMLParser extends DefaultHandler {
	public interface TrustListCallback {
		public void foundTrust(String publicKey, Integer messageTrustLevel, Integer trustListTrustLevel,
				String messageTrustComment, String trustListTrustComment) throws Exception;
	}

	public static void parse(FreenetURI uri, FetchResult result, TrustListCallback callback)
			throws IllegalArgumentException {
		Bucket bkt = result.asBucket();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			SAXParser parser = factory.newSAXParser();
			InputStream is = bkt.getInputStream();
			try {
				parser.parse(is, new TrustListXMLParser(callback));
			} finally {
				Closer.close(is);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		} finally {
			bkt.free();
		}
	}

	private final TrustListCallback cb;

	private String publicKey;
	private Integer messageTrustLevel;
	private Integer trustListTrustLevel;
	private String messageTrustComment;
	private String trustListTrustComment;

	/**
	 * @param trustList
	 */
	private TrustListXMLParser(TrustListCallback callback) {
		cb = callback;
	}

	private LinkedList<String> lastTag;
	private LinkedList<StringBuilder> text;

	@Override
	public void startDocument() {
		lastTag = new LinkedList<String>();
		text = new LinkedList<StringBuilder>();
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		lastTag.addLast(qName);
		text.addLast(new StringBuilder());
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		String tag = lastTag.removeLast();
		String content = text.removeLast().toString();

		try {
			if ("Identity".equals(tag)) {
				publicKey = content;

				FreenetURI fURI = new FreenetURI(publicKey);
				if (!fURI.isSSK())
					throw new IllegalStateException("publicKey not SSK");
				if (fURI.getDocName() == null || fURI.getDocName().length() != 0)
					throw new IllegalStateException("publicKey have non-empty docName");
				if (fURI.hasMetaStrings())
					throw new IllegalStateException("publicKey have metaString");
			} else if ("MessageTrustLevel".equals(tag)) {
				messageTrustLevel = Integer.parseInt(content);
				if (messageTrustLevel != null && (messageTrustLevel < 0 || messageTrustLevel > 100))
					throw new IllegalStateException("messageTrustLevel out of range");
			} else if ("TrustListTrustLevel".equals(tag)) {
				trustListTrustLevel = Integer.parseInt(content);
				if (trustListTrustLevel != null && (trustListTrustLevel < 0 || trustListTrustLevel > 100))
					throw new IllegalStateException("trustListTrustLevel out of range");
			} else if ("MessageTrustComment".equals(tag)) {
				if (content.length() > 60)
					content = content.substring(0, 60);
				messageTrustComment = content;
			} else if ("TrustListTrustComment".equals(tag)) {
				if (content.length() > 60)
					content = content.substring(0, 60);
				trustListTrustComment = content;
			} else if ("Trust".equals(tag)) {
				cb.foundTrust(publicKey, messageTrustLevel, trustListTrustLevel, messageTrustComment,
						trustListTrustComment);

				publicKey = null;
				messageTrustLevel = null;
				trustListTrustLevel = null;
				messageTrustComment = null;
				trustListTrustComment = null;
			}
		} catch (Exception e) {
			throw new SAXException(e);
		}
	}

	@Override
	public void characters(char ch[], int start, int length) throws SAXException {
		StringBuilder sb = text.getLast();
		sb.append(ch, start, length);
	}
}