package plugins.FMS.xml;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.support.Logger;

/** Identity */
public class Identity {
	private Integer id;
	protected final String ssk;

	// in XML and DB
	private String name;
	private boolean singleUse;
	private boolean publishTrustList;
	private boolean publishBoardList;
	private Integer freesiteEdition;

	// in DB only
	protected java.sql.Date dateAdded;
	protected java.sql.Date lastSeen;
	protected String addedMethod;
	protected int failureCount;

	/**
	 * Create Identity from public key.
	 * 
	 * @param identityId
	 *            IdentityId, may be <code>null</code>
	 * @param publicKey
	 *            SSK of the public key.
	 * @throws MalformedURLException
	 *             if the public key is malformed.
	 */
	public Identity(Integer identityId, String publicKey) throws MalformedURLException {
		FreenetURI uri = new FreenetURI(publicKey);

		if (!uri.isSSK() && !uri.isUSK())
			throw new MalformedURLException("Not an SSK@");

		setId(identityId);
		uri = uri.setKeyType("SSK").setDocName("").setMetaString(null);
		ssk = uri.toString();
	}

	/**
	 * Create Identity from public key.
	 * 
	 * @param identityId
	 *            IdentityId, may be <code>null</code>
	 * @param uri
	 *            SSK key of the identity.
	 * @param fr
	 *            Fetched result.
	 * @throws IllegalArgumentException
	 *             if the XML is malformed.
	 */
	// SSK@asdfasdf.../messagebase|yyyy-mm-dd|Identity|#.xml
	public Identity(Integer identityId, FreenetURI uri, FetchResult fr) throws IllegalArgumentException {
		assert uri.isSSK() || !uri.isUSK();
		setId(identityId);
		ssk = uri.setKeyType("SSK").setDocName("").setMetaString(null).toString();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			SAXParser parser = factory.newSAXParser();
			parser.parse(fr.asBucket().getInputStream(), new IdentityXMLParser(this));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		} finally {
			fr.asBucket().free();
		}
	}

	public Identity(ResultSet rs) throws SQLException {
		setId(rs.getInt("IdentityID"));
		ssk = rs.getString("PublicKey");
		name = rs.getString("Name");
		singleUse = rs.getBoolean("SingleUse");
		publishTrustList = rs.getBoolean("PublishTrustList");
		publishBoardList = rs.getBoolean("PublishBoardList");
		freesiteEdition = (Integer) rs.getObject("FreesiteEdition");
		dateAdded = rs.getDate("DateAdded");
		lastSeen = rs.getDate("LastSeen");
		addedMethod = rs.getString("AddedMethod");
		failureCount = rs.getInt("FailureCount");
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
			pstmt.setInt(6, getId());
			pstmt.execute();
		} finally {
			pstmt.close();
		}
	}

	/**
	 * Store an new identity from database
	 * 
	 * @param conn
	 *            SQL connection
	 * @param publicKey
	 *            public key
	 * @param addedMethod
	 *            added method
	 * @return the identity, or <code>null</code> if record not found
	 * @throws SQLException
	 * @throws MalformedURLException
	 */
	public static Identity create(Connection conn, String publicKey, String addedMethod) throws SQLException,
			MalformedURLException {
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
	 * Load identity from database
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
				return new Identity(rs);
			} finally {
				rs.close();
			}
		} finally {
			pStmt.close();
		}
	}

	/**
	 * Load identity from database
	 * 
	 * @param conn
	 *            SQL connection
	 * @param publicKey
	 *            public key
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
				return new Identity(rs);
			} finally {
				rs.close();
			}
		} finally {
			pStmt.close();
		}
	}

	public String getPublicKey() {
		return ssk;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setSingleUse(boolean singleUse) {
		this.singleUse = singleUse;
	}

	public boolean isSingleUse() {
		return singleUse;
	}

	public void setPublishTrustList(boolean publishTrustList) {
		this.publishTrustList = publishTrustList;
	}

	public boolean isPublishTrustList() {
		return publishTrustList;
	}

	public void setPublishBoardList(boolean publishBoardList) {
		this.publishBoardList = publishBoardList;
	}

	public boolean isPublishBoardList() {
		return publishBoardList;
	}

	public void setFreesiteEdition(Integer freesiteEdition) {
		this.freesiteEdition = freesiteEdition;
	}

	public Integer getFreesiteEdition() {
		return freesiteEdition;
	}

	@Override
	public String toString() {
		return "[id=" + getId() + ", name=" + name + "]";
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getId() {
		return id;
	}
}
