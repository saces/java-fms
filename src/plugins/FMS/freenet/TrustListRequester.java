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

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml2.Identity;
import plugins.FMS.xml2.TrustList;
import plugins.FMS.xml2.TrustList.Trust;
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
				int updateTrust = 0;
				int newTrust = 0;
				int newId = 0;

				TrustList tl = TrustList.fromFetchResult(req.iid, uri, result);
				for (Trust t : tl.trust) {
					Savepoint trustSave = conn.setSavepoint();
					Identity targetId = Identity.load(conn, t.identity);
					if (targetId == null) {
						targetId = Identity.create(conn, t.identity, "Trust List of " + id.name);
						newId++;
					}
					// try to update existing
					try {
						pFindTrust.setInt(1, req.iid);
						pFindTrust.setInt(2, targetId.id);
						ResultSet rs = pFindTrust.executeQuery();
						try {
							if (rs.next()) {
								rs.updateObject(1, t.messageTrustLevel, Types.INTEGER);
								rs.updateObject(2, t.trustListTrustLevel, Types.INTEGER);
								rs.updateString(3, t.messageTrustComment);
								rs.updateString(4, t.trustListTrustComment);
								rs.updateRow();
								updateTrust++;

								continue;
							}
						} finally {
							rs.close();
						}
					} catch (SQLException e) {
						Logger.normal(this, "fetchSuccess(): SQLException: [msgTL=" + t.messageTrustLevel + ", msgTC="
								+ t.messageTrustComment + ", tlTL=" + t.trustListTrustLevel + ", tlTC="
								+ t.trustListTrustComment + "] : " + e, e);
					}

					// try to insert new
					try {
						pAddTrust.setInt(1, req.iid);
						pAddTrust.setInt(2, targetId.id);
						pAddTrust.setObject(3, t.messageTrustLevel, Types.INTEGER);
						pAddTrust.setObject(4, t.trustListTrustLevel, Types.INTEGER);
						pAddTrust.setString(5, t.messageTrustComment);
						pAddTrust.setString(6, t.trustListTrustComment);
						pAddTrust.execute();

						newTrust++;
						Logger.debug(this, "Trust IdentityId="
								+ req.iid //
								+ ", TargetId="
								+ targetId //
								+ ", trust=[msgTL=" + t.messageTrustLevel + ", msgTC=" + t.messageTrustComment
								+ ", tlTL=" + t.trustListTrustLevel + ", tlTC=" + t.trustListTrustComment + "]");
					} catch (SQLException e) {
						Logger.normal(this, "fetchSuccess(): SQLException: [msgTL=" + t.messageTrustLevel + ", msgTC="
								+ t.messageTrustComment + ", tlTL=" + t.trustListTrustLevel + ", tlTC="
								+ t.trustListTrustComment + "] : " + e, e);
						conn.rollback(trustSave);
					}
				}
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
