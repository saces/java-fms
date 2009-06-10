/**
 * MessageList XML Parser
 */
package plugins.FMS.xml;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;

public class MessageXMLParser extends DefaultHandler {
	public static Message parse(FreenetURI uri, FetchResult result) throws IllegalArgumentException {
		Bucket bkt = result.asBucket();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			SAXParser parser = factory.newSAXParser();
			InputStream is = bkt.getInputStream();
			try {
				MessageXMLParser handler = new MessageXMLParser();
				parser.parse(is, handler);
				return handler.msg;
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
	private final SimpleDateFormat tf;

	/**
	 * @param trustList
	 */
	private MessageXMLParser() {
		df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		tf = new SimpleDateFormat("HH:mm:ss", Locale.US);
	}

	private LinkedList<String> lastTag;
	private LinkedList<StringBuilder> text;
	private Message msg;

	private int inReplyTo;

	private int parentOrder;
	private String parentId;

	private String attachCHK;
	private long attachSize;

	@Override
	public void startDocument() {
		lastTag = new LinkedList<String>();
		text = new LinkedList<StringBuilder>();

		msg = new Message();
		msg.boards = new ArrayList<String>();
		msg.parentPost = new TreeMap<Integer, String>();
		msg.attachments = new LinkedHashMap<String, Long>();
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		lastTag.addLast(qName);
		text.addLast(new StringBuilder());

		if ("InReplyTo".equals(qName))
			inReplyTo++;
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		String tag = lastTag.removeLast();
		String content = text.removeLast().toString();

		try {
			if ("Date".equals(tag)) {
				msg.date = df.parse(content).getTime();
			} else if ("Time".equals(tag)) {
				msg.time = tf.parse(content).getTime();
			} else if ("Subject".equals(tag)) {
				if (content.length() > 120)
					content = content.substring(0, 120);
				msg.subject = content;
			} else if ("MessageID".equals(tag) && inReplyTo == 0) {
				msg.uuid = content;
			} else if ("Board".equals(tag) && "Boards".equals(lastTag.getLast())) {
				content = content.toLowerCase(Locale.US);
				if (content.length() > 60)
					content = content.substring(0, 60);
				msg.boards.add(content);
			} else if ("ReplyBoard".equals(tag)) {
				content = content.toLowerCase(Locale.US);
				if (content.length() > 60)
					content = content.substring(0, 60);
				msg.replyBoard = content;
			} else if ("InReplyTo".equals(tag)) {
				inReplyTo--;
			} else if ("Order".equals(tag) && inReplyTo > 0) {
				parentOrder = Integer.parseInt(content);
			} else if ("MessageID".equals(tag) && inReplyTo > 0) {
				parentId = content;
			} else if ("Message".equals(tag) && inReplyTo > 0) {
				msg.parentPost.put(parentOrder, parentId);
			} else if ("Body".equals(tag)) {
				msg.body = content;
			} else if ("Key".equals(tag) && "File".equals(lastTag.getLast())) {
				FreenetURI chk = new FreenetURI(content);
				attachCHK = chk.toString();
			} else if ("Size".equals(tag) && "File".equals(lastTag.getLast())) {
				attachSize = Long.parseLong(content);
			} else if ("File".equals(tag) && "Attachments".equals(lastTag.getLast())) {
				msg.attachments.put(attachCHK, attachSize);
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