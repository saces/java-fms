package plugins.FMS.xml2;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

@XStreamAlias("Identity")
public class Identity {
	@XStreamOmitField
	public Integer id;
	@XStreamOmitField
	public String publicKey;

	@XStreamAlias("Name")
	public String name;
	@XStreamAlias("SingleUse")
	boolean singleUse;
	@XStreamAlias("PublishTrustList")
	public boolean publishTrustList;
	@XStreamAlias("PublishBoardList")
	boolean publishBoardList;
	@XStreamAlias("FreesiteEdition")
	Integer freesiteEdition;

	/**
	 * Create from public key.
	 * 
	 * @param identityId
	 *            IdentityId, may be <code>null</code>
	 * @param publicKey
	 *            SSK of the public key.
	 * @throws ValidationException
	 */
	public Identity(Integer identityId, String publicKey) throws ValidationException {
		id = identityId;
		this.publicKey = publicKey;

		validate();
	}

	private Identity(FreenetURI publicKey) throws ValidationException {
		this.publicKey = publicKey.toASCIIString();
		validate();
	}

	private Identity() {
	}

	private void validate() throws ValidationException {
		try {
			FreenetURI uri = new FreenetURI(publicKey);

			if (!uri.isSSK() && !uri.isUSK())
				throw new ValidationException("Not an SSK@");
			uri = uri.setKeyType("SSK").setDocName("").setMetaString(null);
		} catch (MalformedURLException e) {
			throw new ValidationException(e);
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
	 */
	// SSK@asdfasdf.../messagebase|yyyy-mm-dd|Identity|#.xml
	public static Identity fromFetchResult(Integer identityId, FreenetURI uri, FetchResult fr)
			throws ValidationException {
		Identity id;

		try {
			InputStream is = fr.asBucket().getInputStream();
			id = fromXML(is, uri);
			is.close();
		} catch (Exception e) {
			throw new ValidationException(e);
		} finally {
			fr.asBucket().free();
		}

		id.id = identityId;
		id.publicKey = uri.toASCIIString();
		id.validate();

		return id;
	}

	public void update(Connection conn) throws SQLException {
		PreparedStatement pstmt = conn.prepareStatement("UPDATE tblIdentity " + " SET Name=?," + "  SingleUse=?,"
				+ "  PublishTrustList=?," + "  PublishBoardList=?," + "  FreesiteEdition=?,"
				+ "  LastSeen=CURRENT_TIMESTAMP" + " WHERE IdentityID=?");
		try {
			pstmt.setString(1, name);
			pstmt.setBoolean(2, singleUse);
			pstmt.setBoolean(3, publishTrustList);
			pstmt.setBoolean(4, publishBoardList);
			pstmt.setObject(5, freesiteEdition, Types.INTEGER);
			pstmt.setInt(6, id);
			pstmt.execute();
		} finally {
			pstmt.close();
		}
	}

	/**
	 * Store to database
	 * 
	 * @param conn
	 *            SQL connection
	 * @param publicKey
	 *            public key
	 * @param addedMethod
	 *            added method
	 * @return the identity, or <code>null</code> if record not found
	 * @throws SQLException
	 * @throws ValidationException
	 */
	public static Identity create(Connection conn, String publicKey, String addedMethod) throws SQLException,
			ValidationException {
		PreparedStatement pStmt = conn.prepareStatement("INSERT INTO tblIdentity (PublicKey,AddedMethod) VALUES (?,?)",
				Statement.RETURN_GENERATED_KEYS);
		try {
			pStmt.setString(1, publicKey);
			pStmt.setString(2, addedMethod);
			pStmt.execute();

			ResultSet rs = pStmt.getGeneratedKeys();
			try {
				if (!rs.next())
					return null;
				Identity newId = new Identity(rs.getInt(1), publicKey);
				Logger.minor(Identity.class, "Adding new identity " + newId + " " + addedMethod);
				return newId;
			} finally {
				rs.close();
			}
		} finally {
			pStmt.close();
		}
	}

	/**
	 * Load from database
	 * 
	 * @param conn
	 *            SQL connection
	 * @param identityId
	 *            identity id
	 * @return the identity, or <code>null</code> if record not found
	 * @throws SQLException
	 */
	public static Identity load(Connection conn, int identityId) throws SQLException {
		PreparedStatement pStmt = conn.prepareStatement("SELECT * FROM tblIdentity WHERE IdentityId=?");
		try {
			pStmt.setInt(1, identityId);
			ResultSet rs = pStmt.executeQuery();
			try {
				if (!rs.next())
					return null;
				return fromResultSet(rs);
			} catch (ValidationException e) {
				Logger.error(Identity.class, "INVALID ID IN DB: id=" + identityId + " : " + e);
				return null;
			} finally {
				rs.close();
			}
		} finally {
			pStmt.close();
		}
	}

	/**
	 * Load from database
	 * 
	 * @param conn
	 *            SQL connection
	 * @param publicKey
	 *            the public key
	 * @return the identity, or <code>null</code> if record not found
	 * @throws SQLException
	 */
	public static Identity load(Connection conn, String publicKey) throws SQLException {
		PreparedStatement pStmt = conn.prepareStatement("SELECT * FROM tblIdentity WHERE PublicKey=?");
		try {
			pStmt.setString(1, publicKey);
			ResultSet rs = pStmt.executeQuery();
			try {
				if (!rs.next())
					return null;
				return fromResultSet(rs);
			} catch (ValidationException e) {
				Logger.error(Identity.class, "IMPOSSIBLE ERROR: " + e, e);
				assert false; // impossible
				return null;
			} finally {
				rs.close();
			}
		} finally {
			pStmt.close();
		}
	}

	private static Identity fromResultSet(ResultSet rs) throws SQLException, ValidationException {
		Identity id = new Identity();

		id.id = rs.getInt("IdentityID");
		id.publicKey = rs.getString("PublicKey");
		id.name = rs.getString("Name");
		id.singleUse = rs.getBoolean("SingleUse");
		id.publishTrustList = rs.getBoolean("PublishTrustList");
		id.publishBoardList = rs.getBoolean("PublishBoardList");
		id.freesiteEdition = (Integer) rs.getObject("FreesiteEdition");

		id.validate();
		return id;
	}

	private static final XStream xstream = new XStream(new NVDomDriver());
	static {
		xstream.processAnnotations(Identity.class);
		xstream.setMode(XStream.NO_REFERENCES);
	}

	public static Identity fromXML(InputStream xml, FreenetURI uri) throws ValidationException {
		Identity id = (Identity) xstream.fromXML(xml, new Identity(uri));
		id.validate();
		return id;
	}

	public static Identity fromXML(String xml, FreenetURI uri) throws ValidationException {
		Identity id = (Identity) xstream.fromXML(xml, new Identity(uri));
		id.validate();
		return id;
	}

	public String toXML() {
		return xstream.toXML(this);
	}
}
