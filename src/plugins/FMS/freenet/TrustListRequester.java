package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml.Identity;
import plugins.FMS.xml.TrustListXMLParser;
import plugins.FMS.xml.TrustListXMLParser.TrustListCallback;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

public class TrustListRequester extends AbstractFetcher {
	public TrustListRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblTrustListRequests");
	}

	@Override
	protected void fetchSuccess(final Connection conn, final Request req, FreenetURI uri, FetchResult result)
			throws Exception {
		final Identity id = Identity.load(conn, req.iid);
		Logger.minor(this, "Got TrustList from " + id + ", uri=" + uri);

		final PreparedStatement pFindTrust = conn.prepareStatement(
				"SELECT MessageTrust,TrustListTrust,MessageTrustComment,TrustListTrustComment"
						+ " FROM tblPeerTrust WHERE IdentityID=? AND TargetIdentityID=? FOR UPDATE",
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		try {
			final PreparedStatement pAddTrust = conn
					.prepareStatement("INSERT INTO tblPeerTrust"
							+ " (IdentityID,TargetIdentityID,MessageTrust,TrustListTrust,MessageTrustComment,TrustListTrustComment)"
							+ " VALUES (?,?,?,?,?,?)");
			try {
				final AtomicInteger updateTrust = new AtomicInteger();
				final AtomicInteger newTrust = new AtomicInteger();
				final AtomicInteger newId = new AtomicInteger();

				TrustListXMLParser.parse(uri, result, new TrustListCallback() {
					public void foundTrust(String publicKey, Integer messageTrustLevel, Integer trustListTrustLevel,
							String messageTrustComment, String trustListTrustComment) throws Exception {
						Savepoint trustSave = conn.setSavepoint();
						Identity targetId = Identity.load(conn, publicKey);
						if (targetId == null) {
							targetId = Identity.create(conn, publicKey, "Trust List of " + id.getName());
							newId.incrementAndGet();
						}
						// try to update existing
						try {
							pFindTrust.setInt(1, req.iid);
							pFindTrust.setInt(2, targetId.getId());
							ResultSet rs = pFindTrust.executeQuery();
							try {
								if (rs.next()) {
									rs.updateObject(1, messageTrustLevel, Types.INTEGER);
									rs.updateObject(2, trustListTrustLevel, Types.INTEGER);
									rs.updateString(3, messageTrustComment);
									rs.updateString(4, trustListTrustComment);
									rs.updateRow();
									updateTrust.incrementAndGet();

									return;
								}
							} finally {
								rs.close();
							}
						} catch (SQLException e) {
							Logger.normal(this, "fetchSuccess(): SQLException: [msgTL=" + messageTrustLevel
									+ ", msgTC=" + messageTrustComment + ", tlTL=" + trustListTrustLevel + ", tlTC="
									+ trustListTrustComment + "] : " + e, e);
						}

						// try to insert new
						try {
							pAddTrust.setInt(1, req.iid);
							pAddTrust.setInt(2, targetId.getId());
							pAddTrust.setObject(3, messageTrustLevel, Types.INTEGER);
							pAddTrust.setObject(4, trustListTrustLevel, Types.INTEGER);
							pAddTrust.setString(5, messageTrustComment);
							pAddTrust.setString(6, trustListTrustComment);
							pAddTrust.execute();

							newTrust.incrementAndGet();
							Logger.debug(this, "Trust IdentityId=" + req.iid //
									+ ", TargetId=" + targetId //
									+ ", trust=[msgTL=" + messageTrustLevel //
									+ ", msgTC=" + messageTrustComment //
									+ ", tlTL=" + trustListTrustLevel //
									+ ", tlTC=" + trustListTrustComment //
									+ "]");
						} catch (SQLException e) {
							Logger.normal(this, "fetchSuccess(): SQLException: [msgTL=" + messageTrustLevel//
									+ ", msgTC=" + messageTrustComment //
									+ ", tlTL=" + trustListTrustLevel //
									+ ", tlTC=" + trustListTrustComment //
									+ "] : " + e, e);
							conn.rollback(trustSave);
						}
					}
				});
				Logger.minor(this, "fetchSuccess(): Added" //
						+ " newTrust=" + newTrust //
						+ ", updateTrust=" + updateTrust //
						+ ", newId=" + newId);
			} finally {
				pAddTrust.close();
			}
		} finally {
			pFindTrust.close();
		}
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		java.sql.Date today = Util.getSQLToday();

		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT IdentityID FROM tblIdentity" //
					+ " WHERE" //
					+ "  Name IS NOT NULL AND" //
					+ "  Name <> '' AND" // 
					+ "  PublicKey IS NOT NULL AND" //
					+ "  PublicKey <> '' AND" //
					+ "  PublishTrustList <> 0 AND" //
					+ "  LastSeen>=? AND" + "  FailureCount<=1000" // XXX 1000
					+ " ORDER BY LastSeen");
			try {
				pstmt.setDate(1, today);
				ResultSet rs = pstmt.executeQuery();
				try {
					while (rs.next()) {
						list.add(new Request(rs.getInt("IdentityID"), today));
					}
				} finally {
					rs.close();
				}
			} finally {
				pstmt.close();
			}
			conn.commit();
		} finally {
			conn.close();
		}

		return list;
	}

	@Override
	protected FreenetURI getURI(Request req) {
		String u = req.publicKey + Util.msgBase + '|' + req.date + "|TrustList|" + req.idx + ".xml";
		try {
			return new FreenetURI(u);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected int getMaxRequests() {
		return Util.MaxIdentityRequests;
	}
}
