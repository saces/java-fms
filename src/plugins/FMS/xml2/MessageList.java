package plugins.FMS.xml2;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;

@XStreamAlias("MessageList")
public class MessageList {
	@XStreamAlias("Message")
	public static class Message {
		@XStreamAlias("Date")
		public java.sql.Date date;

		@XStreamAlias("Boards")
		@XStreamConverter(BoardsConverter.class)
		public List<String> boards;

		@XStreamAlias("Index")
		public int index;

		private void validate() throws ValidationException {
			if (boards == null)
				throw new ValidationException("boards is null");

			ListIterator<String> it = boards.listIterator();
			while (it.hasNext()) {
				String board = it.next();
				board = board.toLowerCase(Locale.US);
				if (board.length() > 60)
					board = board.substring(0, 60);
				it.set(board);
			}
		}
	}

	@XStreamAlias("ExternalMessage")
	public static class ExternalMessage {
		@XStreamAlias("Type")
		public String type;
		@XStreamAlias("Identity")
		public String identity;

		@XStreamAlias("Index")
		public int index;

		@XStreamAlias("Date")
		public java.sql.Date date;

		@XStreamAlias("Boards")
		@XStreamConverter(BoardsConverter.class)
		public List<String> boards;

		private void validate() throws ValidationException {
			if (!"Keyed".equals(type))
				throw new ValidationException("type is not keyed");
			if (boards == null)
				throw new ValidationException("boards is null");

			try {
				FreenetURI fURI = new FreenetURI(identity);
				if (!fURI.isSSK())
					throw new ValidationException("publicKey not SSK");
				identity = fURI.setDocName("").setMetaString(null).toASCIIString();
			} catch (MalformedURLException e) {
				throw new ValidationException(e);
			}

			ListIterator<String> it = boards.listIterator();
			while (it.hasNext()) {
				String board = it.next();
				board = board.toLowerCase(Locale.US);
				if (board.length() > 60)
					board = board.substring(0, 60);
				it.set(board);
			}
		}
	}

	@XStreamImplicit(itemFieldName = "Message")
	public List<Message> message;

	@XStreamImplicit(itemFieldName = "ExternalMessage")
	public List<ExternalMessage> externalMessage;

	private void validate() throws ValidationException {
		if (message != null) {
			Iterator<Message> it = message.iterator();
			while (it.hasNext()) {
				try {
					it.next().validate();
				} catch (ValidationException v) {
					it.remove();
				}
			}
		}

		if (externalMessage != null) {
			Iterator<ExternalMessage> it = externalMessage.iterator();
			while (it.hasNext()) {
				try {
					it.next().validate();
				} catch (ValidationException v) {
					it.remove();
				}
			}
		}
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
	public static MessageList fromFetchResult(Integer identityId, FreenetURI uri, FetchResult fr)
			throws ValidationException {
		MessageList ml;

		try {
			InputStream is = fr.asBucket().getInputStream();
			ml = fromXML(is);
			is.close();
		} catch (Exception e) {
			throw new ValidationException(e);
		} finally {
			fr.asBucket().free();
		}

		ml.validate();

		return ml;
	}

	private static final XStream xstream = new XStream(new NVDomDriver());
	static {
		xstream.processAnnotations(Message.class);
		xstream.processAnnotations(ExternalMessage.class);
		xstream.processAnnotations(MessageList.class);
		xstream.setMode(XStream.NO_REFERENCES);
	}

	public static MessageList fromXML(InputStream xml) throws ValidationException {
		MessageList m = (MessageList) xstream.fromXML(xml, new MessageList());
		m.validate();
		return m;
	}

	public static MessageList fromXML(String xml) throws ValidationException {
		MessageList m = (MessageList) xstream.fromXML(xml, new MessageList());
		m.validate();
		return m;
	}

	public String toXML() {
		return xstream.toXML(this);
	}
}
