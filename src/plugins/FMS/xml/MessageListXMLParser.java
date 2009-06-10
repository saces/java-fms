/**
 * MessageList XML Parser
 */
package plugins.FMS.xml;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;

public class MessageListXMLParser extends DefaultHandler {
	public interface MessageListCallback {
		public void foundMessage(java.sql.Date day, int idx, List<String> boards) throws Exception;

		public void foundExternalMessage(String ssk, java.sql.Date day, int idx, List<String> boards) throws Exception;
	}

	public static void parse(FreenetURI uri, FetchResult result, MessageListCallback callback)
			throws IllegalArgumentException {
		Bucket bkt = result.asBucket();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			SAXParser parser = factory.newSAXParser();
			InputStream is = bkt.getInputStream();
			try {
				parser.parse(is, new MessageListXMLParser(callback));
			} finally {
				Closer.close(is);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		} finally {
			bkt.free();
		}
	}

	private final SimpleDateFormat df;
	private final MessageListCallback cb;

	private String ssk;
	private java.sql.Date day;
	private Integer idx;
	private List<String> boards;

	/**
	 * @param trustList
	 */
	private MessageListXMLParser(MessageListCallback callback) {
		df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
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

		if ("Boards".equals(qName))
			boards = new ArrayList<String>();
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		String tag = lastTag.removeLast();
		String content = text.removeLast().toString();

		try {
			if ("Date".equals(tag)) {
				day = new java.sql.Date(df.parse(content).getTime());
			} else if ("Board".equals(tag)) {
				content = content.toLowerCase(Locale.US);
				if (content.length() > 60)
					content = content.substring(0, 60);
				String board = content;
				boards.add(board);
			} else if ("Index".equals(tag)) {
				idx = Integer.parseInt(content);
			} else if ("Identity".equals(tag)) {
				ssk = content;

				FreenetURI fURI = new FreenetURI(ssk);
				if (!fURI.isSSK())
					throw new IllegalStateException("publicKey not SSK");
				if (fURI.getDocName() == null || fURI.getDocName().length() != 0)
					throw new IllegalStateException("publicKey have non-empty docName");
				if (fURI.hasMetaStrings())
					throw new IllegalStateException("publicKey have metaString");
			} else if ("Message".equals(tag)) {
				cb.foundMessage(day, idx, boards);

				ssk = null;
				day = null;
				idx = null;
				boards = null;
			} else if ("ExternalMessage".equals(tag)) {
				cb.foundExternalMessage(ssk, day, idx, boards);

				ssk = null;
				day = null;
				idx = null;
				boards = null;
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