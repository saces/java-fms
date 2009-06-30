package plugins.FMS.xml2;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;

@XStreamAlias("BoardList")
public class BoardList {
	@XStreamAlias("Board")
	public static class Board {
		@XStreamAlias("Name")
		public String name;
		@XStreamAlias("Description")
		public String description;

		public Board(String name, String description) {
			super();
			this.name = name;
			this.description = description;
		}

		private void validate() {
			name = name.toLowerCase(Locale.US);
			if (name.length() > 60)
				name = name.substring(0, 60);
			if (description.length() > 127)
				description = description.substring(0, 127);
		}
	}

	@XStreamImplicit(itemFieldName = "Board")
	public List<Board> board;

	@XStreamOmitField
	public FreenetURI uri;

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
	public static BoardList fromFetchResult(Integer identityId, FreenetURI uri, FetchResult fr)
			throws ValidationException {
		BoardList bl;

		try {
			InputStream is = fr.asBucket().getInputStream();
			bl = fromXML(is);
			is.close();
		} catch (Exception e) {
			throw new ValidationException(e);
		} finally {
			fr.asBucket().free();
		}

		bl.uri = uri;
		bl.validate();

		return bl;
	}

	private void validate() {
		for (Board b : board)
			b.validate();
	}

	private static final XStream xstream = new XStream(new NVDomDriver());
	static {
		xstream.processAnnotations(BoardList.class);
		xstream.setMode(XStream.NO_REFERENCES);
	}

	public static BoardList fromXML(InputStream xml) {
		BoardList bl = (BoardList) xstream.fromXML(xml, new BoardList());
		bl.validate();
		return bl;
	}

	public static BoardList fromXML(String xml) {
		BoardList bl = (BoardList) xstream.fromXML(xml, new BoardList());
		bl.validate();
		return bl;
	}

	public String toXML() {
		return xstream.toXML(this);
	}
}
