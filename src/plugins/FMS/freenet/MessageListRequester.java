package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml2.Identity;
import plugins.FMS.xml2.MessageList;
import plugins.FMS.xml2.MessageList.ExternalMessage;
import plugins.FMS.xml2.MessageList.Message;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

public class MessageListRequester extends AbstractFetcher {
	public MessageListRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblMessageListRequests");
	}

	@Override
	protected void fetchSuccess(final Connection conn, final Request req, FreenetURI uri, FetchResult result)
			throws Exception {
		Logger.minor(this, "Got MessageList from " + req + " uri=" + uri);

		final PreparedStatement pStmtFind = conn
				.prepareStatement(
						"SELECT SourceIdentityID,Loaded,FailureCount FROM tblMessageRequests WHERE IdentityID=? AND Day=? AND RequestIndex=?",
						ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		try {
			final PreparedStatement pStmtAdd = conn
					.prepareStatement("INSERT INTO tblMessageRequests (IdentityID,SourceIdentityID,Day,RequestIndex) VALUES (?,?,?,?)");

			try {
				MessageList ml = MessageList.fromFetchResult(req.iid, uri, result);

				if (ml.message != null) {
					for (Message m : ml.message) {
						pStmtFind.setInt(1, req.iid);
						pStmtFind.setDate(2, m.date);
						pStmtFind.setInt(3, m.index);
						ResultSet rs = pStmtFind.executeQuery();

						// FIXME check the board list, date, etc
						try {
							if (rs.next()) {
								// check existing
								if (!rs.getBoolean("Loaded") && rs.getObject("SourceIdentityID") != null) {
									rs.updateObject("SourceIdentityID", null, Types.INTEGER);
									rs.updateInt("FailureCount", 0);
									rs.updateRow();
								}
							} else {
								// insert new
								pStmtAdd.setInt(1, req.iid);
								pStmtAdd.setObject(2, null, Types.INTEGER);
								pStmtAdd.setDate(3, m.date);
								pStmtAdd.setInt(4, m.index);
								pStmtAdd.executeUpdate();
							}
						} finally {
							rs.close();
						}
					}
				}

				if (ml.externalMessage != null) {
					for (ExternalMessage m : ml.externalMessage) {
						// TODO cache identity
						Identity postId = Identity.load(conn, m.identity);
						if (postId == null)
							continue;
						int id = postId.id;

						// FIXME check the board list, date, etc

						pStmtFind.setInt(1, id);
						pStmtFind.setDate(2, m.date);
						pStmtFind.setInt(3, m.index);
						ResultSet rs = pStmtFind.executeQuery();
						try {
							if (!rs.next()) {
								pStmtAdd.setInt(1, id);
								pStmtAdd.setInt(2, req.iid);
								pStmtAdd.setDate(3, m.date);
								pStmtAdd.setInt(4, m.index);
								pStmtAdd.executeUpdate();
							}
						} finally {
							rs.close();
						}
					}
				}
			} finally {
				pStmtAdd.close();
			}
		} finally {
			pStmtFind.close();
		}
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		java.sql.Date today = Util.getSQLToday();

		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT IdentityID FROM tblIdentity WHERE"
					+ "  Name IS NOT NULL AND Name <> '' AND PublicKey IS NOT NULL AND"
					+ "  PublicKey <> '' AND LastSeen>=? AND FailureCount<=1000" // XXX
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
		String u = req.publicKey.replace("SSK@", "USK@") //
				+ Util.msgBase //
				+ '|' //
				+ req.date.toString().replace('-', '.') //
				+ "|MessageList/" //
				+ req.idx //
				+ "/MessageList.xml";
		try {
			return new FreenetURI(u);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected int getMaxRequests() {
		return Util.MaxMessageListRequests;
	}
}
