package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml.Identity;
import plugins.FMS.xml.MessageListXMLParser;
import plugins.FMS.xml.MessageListXMLParser.MessageListCallback;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

public class MessageListRequester extends AbstractFetcher {
	public MessageListRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblMessageListRequests");
	}

	// factored out to avoid nested try{} block
	private static class SQL {
		private PreparedStatement pStmtFind;
		private PreparedStatement pStmtAdd;

		void init(Connection conn) throws SQLException {
			pStmtFind = conn
					.prepareStatement(
							"SELECT SourceIdentityID,Loaded,FailureCount FROM tblMessageRequests WHERE IdentityID=? AND Day=? AND RequestIndex=?",
							ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
			pStmtAdd = conn
					.prepareStatement("INSERT INTO tblMessageRequests (IdentityID,SourceIdentityID,Day,RequestIndex) VALUES (?,?,?,?)");
		}

		void close() {
			try {
				if (pStmtAdd != null)
					pStmtAdd.close();
			} catch (SQLException e) {
			}

			try {
				if (pStmtFind != null)
					pStmtFind.close();
			} catch (SQLException e) {
			}
		}
	}

	@Override
	protected void fetchSuccess(final Connection conn, final Request req, FreenetURI uri, FetchResult result)
			throws Exception {
		Logger.minor(this, "Got MessageList from " + req + " uri=" + uri);

		final SQL sql = new SQL();
		try {
			sql.init(conn);
			MessageListXMLParser.parse(uri, result, new MessageListCallback() {
				public void foundExternalMessage(String ssk, Date day, int idx, List<String> boards) throws Exception {
					Logger.debug(this, "FoundExternalMessage ssk=" + ssk + ", day=" + day + ", idx=" + idx
							+ ", boards=" + boards);
					// TODO cache identity
					Identity postId = Identity.load(conn, ssk);
					if (postId == null)
						return;
					int id = postId.getId();

					// FIXME check the board list, date, etc

					sql.pStmtFind.setInt(1, id);
					sql.pStmtFind.setDate(2, day);
					sql.pStmtFind.setInt(3, idx);
					ResultSet rs = sql.pStmtFind.executeQuery();
					try {
						if (!rs.next()) {
							sql.pStmtAdd.setInt(1, id);
							sql.pStmtAdd.setInt(2, req.iid);
							sql.pStmtAdd.setDate(3, day);
							sql.pStmtAdd.setInt(4, idx);
							sql.pStmtAdd.executeUpdate();
						}
					} finally {
						rs.close();
					}
				}

				public void foundMessage(Date day, int idx, List<String> boards) throws Exception {
					Logger.debug(this, "FoundMessage day=" + day + ", idx=" + idx + ", boards=" + boards);

					sql.pStmtFind.setInt(1, req.iid);
					sql.pStmtFind.setDate(2, day);
					sql.pStmtFind.setInt(3, idx);
					ResultSet rs = sql.pStmtFind.executeQuery();
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
							sql.pStmtAdd.setInt(1, req.iid);
							sql.pStmtAdd.setObject(2, null, Types.INTEGER);
							sql.pStmtAdd.setDate(3, day);
							sql.pStmtAdd.setInt(4, idx);
							sql.pStmtAdd.executeUpdate();
						}
					} finally {
						rs.close();
					}
				}
			});
		} finally {
			sql.close();
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
		return Util.MaxIdentityRequests;
	}
}
