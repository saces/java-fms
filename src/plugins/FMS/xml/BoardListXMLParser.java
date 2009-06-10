/**
 * TrustList XML Parser
 */
package plugins.FMS.xml;

import java.io.InputStream;
import java.util.LinkedList;
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

public class BoardListXMLParser extends DefaultHandler {
	private String curBoardName;
	private String curBoardDesc;
	private final BoardListCallback cb;

	public static interface BoardListCallback {
		public void foundBoard(String name, String desc) throws Exception;
	}

	public static void parse(FreenetURI uri, FetchResult result, BoardListCallback callback)
			throws IllegalArgumentException {
		Bucket bkt = result.asBucket();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			SAXParser parser = factory.newSAXParser();
			InputStream is = bkt.getInputStream();
			try {
				parser.parse(is, new BoardListXMLParser(callback));
			} finally {
				Closer.close(is);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		} finally {
			bkt.free();
		}
	}

	private BoardListXMLParser(BoardListCallback callback) {
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
			if ("Name".equals(tag)) {
				content = content.toLowerCase(Locale.US);
				if (content.length() > 60)
					content = content.substring(0, 60);
				curBoardName = content;
			} else if ("Description".equals(tag)) {
				if (content.length() > 127)
					content = content.substring(0, 127);
				curBoardDesc = content;
			} else if ("Board".equals(tag)) {
				cb.foundBoard(curBoardName, curBoardDesc);
				curBoardName = null;
				curBoardDesc = null;
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