package plugins.FMS.xml2;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;

@XStreamAlias("TrustList")
public class TrustList {
	@XStreamAlias("Trust")
	public static class Trust {
		@XStreamAlias("Identity")
		public String identity;
		@XStreamAlias("MessageTrustLevel")
		public Integer messageTrustLevel;
		@XStreamAlias("TrustListTrustLevel")
		public Integer trustListTrustLevel;
		@XStreamAlias("MessageTrustComment")
		public String messageTrustComment;
		@XStreamAlias("TrustListTrustComment")
		public String trustListTrustComment;

		void validate() throws ValidationException {
			try {
				FreenetURI fURI = new FreenetURI(identity);
				if (!fURI.isSSK())
					throw new ValidationException("publicKey not SSK");

				identity = fURI.setDocName("").setMetaString(null).toASCIIString();
			} catch (MalformedURLException e) {
				throw new ValidationException(e);
			}

			if (messageTrustComment != null && messageTrustComment.length() > 60)
				messageTrustComment = messageTrustComment.substring(0, 60);
			if (trustListTrustComment != null && trustListTrustComment.length() > 60)
				trustListTrustComment = trustListTrustComment.substring(0, 60);
		}
	}

	@XStreamImplicit(itemFieldName = "Trust")
	public List<Trust> trust;

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
	public static TrustList fromFetchResult(Integer identityId, FreenetURI uri, FetchResult fr)
			throws ValidationException {
		TrustList tl;

		try {
			InputStream is = fr.asBucket().getInputStream();
			tl = fromXML(is);
			is.close();
		} catch (Exception e) {
			throw new ValidationException(e);
		} finally {
			fr.asBucket().free();
		}

		tl.uri = uri;
		tl.validate();

		return tl;
	}

	private void validate() {
		Iterator<Trust> it = trust.iterator();
		while (it.hasNext()) {
			try {
				it.next().validate();
			} catch (Exception e) {
				it.remove();
			}
		}
	}

	private static final XStream xstream = new XStream(new NVDomDriver());
	static {
		xstream.processAnnotations(TrustList.class);
		xstream.setMode(XStream.NO_REFERENCES);
	}

	public static TrustList fromXML(InputStream xml) {
		TrustList tl = (TrustList) xstream.fromXML(xml, new TrustList());
		tl.validate();
		return tl;
	}

	public static TrustList fromXML(String xml) {
		TrustList tl = (TrustList) xstream.fromXML(xml, new TrustList());
		tl.validate();
		return tl;
	}

	public String toXML() {
		return xstream.toXML(this);
	}
}
