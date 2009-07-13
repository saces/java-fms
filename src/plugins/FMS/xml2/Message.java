package plugins.FMS.xml2;

import java.io.InputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.Logger;

@XStreamAlias("Message")
public class Message {
	@XStreamAlias("Message")
	public static class ParentMessage {
		@XStreamAlias("Order")
		public int order;
		@XStreamAlias("MessageID")
		public String messageUUID;

		ParentMessage(int order, String messageId) {
			this.order = order;
			this.messageUUID = messageId;
		}
	}

	@XStreamAlias("File")
	public static class Attachment {
		@XStreamAlias("Key")
		String key;
		@XStreamAlias("Size")
		long size;

		public Attachment(String key, long size) {
			super();
			this.key = key;
			this.size = size;
		}
	}

	@XStreamAlias("Date")
	public java.sql.Date date;
	@XStreamAlias("Time")
	public java.sql.Time time;
	@XStreamAlias("Subject")
	public String subject;
	@XStreamAlias("MessageID")
	public String messageUUID;

	@XStreamAlias("Boards")
	@XStreamConverter(BoardsConverter.class)
	public List<String> boards;

	@XStreamAlias("ReplyBoard")
	public String replyBoard;

	@XStreamAlias("InReplyTo")
	public List<ParentMessage> inReplyTo;

	@XStreamAlias("Body")
	public String body;

	@XStreamAlias("Attachments")
	public List<Attachment> attachments;

	@XStreamOmitField
	public FreenetURI uri;

	private Message(FreenetURI uri) {
		this.uri = uri;
	}

	/**
	 * Create from fetch result.
	 * 
	 * @param identityId
	 *            IdentityId, may be <code>null</code>
	 * @param uri
	 *            SSK key of the identity.
	 * @param fr
	 *            Fetched result.
	 * @return the identity
	 * @throws ValidationException
	 *             if the input is malformed
	 */
	public static Message fromFetchResult(Integer identityId, FreenetURI uri, FetchResult fr)
			throws ValidationException {
		Message msg;

		try {
			InputStream is = fr.asBucket().getInputStream();
			msg = fromXML(is, uri);
			is.close();
		} catch (Exception e) {
			throw new ValidationException(e);
		} finally {
			fr.asBucket().free();
		}

		msg.uri = uri;
		msg.validate();

		return msg;
	}

	private void validate() throws ValidationException {
		if (replyBoard == null)
			throw new ValidationException("replyBoard is null");
		if (boards == null)
			throw new ValidationException("boards is null");
		if (subject == null)
			throw new ValidationException("subject is null");
		if (body == null)
			throw new ValidationException("body is null");

		if (subject.length() > 120)
			subject = subject.substring(0, 120);

		replyBoard = replyBoard.toLowerCase(Locale.US);
		if (replyBoard.length() > 60)
			replyBoard = replyBoard.substring(0, 60);

		ListIterator<String> it = boards.listIterator();
		while (it.hasNext()) {
			String board = it.next();
			board = board.toLowerCase(Locale.US);
			if (board.length() > 60)
				board = board.substring(0, 60);
			it.set(board);
		}

		String routingKey = "@" + Base64.encode(uri.getRoutingKey()).replaceAll("[~\\-]", "");
		if (!messageUUID.endsWith(routingKey)) {
			Logger.normal(this, "Invalid UUID suffix expected=" + routingKey + ", uuid=" + messageUUID + ", uri=" + uri);
			return;
		}
	}

	private static final XStream xstream = new XStream(new NVDomDriver());
	static {
		xstream.processAnnotations(Message.class);
		xstream.processAnnotations(Attachment.class);
		xstream.processAnnotations(ParentMessage.class);
		xstream.setMode(XStream.NO_REFERENCES);
	}

	public static Message fromXML(InputStream xml, FreenetURI uri) throws ValidationException {
		Message m = (Message) xstream.fromXML(xml, new Message(uri));
		m.validate();
		return m;
	}

	public static Message fromXML(String xml, FreenetURI uri) throws ValidationException {
		Message m = (Message) xstream.fromXML(xml, new Message(uri));
		m.validate();
		return m;
	}

	public String toXML() {
		return xstream.toXML(this);
	}
}
