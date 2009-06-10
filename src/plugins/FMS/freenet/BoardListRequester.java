package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml.BoardListXMLParser;
import plugins.FMS.xml.BoardListXMLParser.BoardListCallback;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

public class BoardListRequester extends AbstractFetcher {
	public BoardListRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblBoardListRequests");
	}

	@Override
	protected void fetchSuccess(Connection conn, Request req, FreenetURI uri, FetchResult result) throws Exception {
		Logger.minor(this, "Got BoardList from " + req);

		final PreparedStatement pFindBoard = conn.prepareStatement("SELECT BoardName, BoardDescription"
				+ " FROM tblBoard WHERE BoardName=? FOR UPDATE", ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE);
		try {
			final PreparedStatement pAddBoard = conn
					.prepareStatement("INSERT INTO tblBoard (BoardName,BoardDescription) VALUES (?,?)");
			try {
				final AtomicInteger addedBoard = new AtomicInteger();
				final AtomicInteger updatedBoard = new AtomicInteger();

				BoardListXMLParser.parse(uri, result, new BoardListCallback() {
					public void foundBoard(String name, String desc) throws SQLException {
						pFindBoard.setString(1, name);
						ResultSet rs = pFindBoard.executeQuery();
						try {
							if (rs.next()) {
								Logger.minor(this, "Update Board " + name + " " + desc);
								String oldDesc = rs.getString(2);
								if (oldDesc == null || oldDesc.length() == 0) {
									rs.updateString(2, desc);
									rs.updateRow();
									updatedBoard.incrementAndGet();
								}
							} else {
								Logger.minor(this, "New Board " + name + " " + desc);
								pAddBoard.setString(1, name);
								pAddBoard.setString(2, desc);
								pAddBoard.executeUpdate();
								addedBoard.incrementAndGet();
							}
						} finally {
							rs.close();
						}
					}
				});
				Logger.minor(this, "Total " + addedBoard + " new boards, and " + updatedBoard + " updated");
			} finally {
				pAddBoard.close();
			}
		} finally {
			pFindBoard.close();
		}
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		java.sql.Date today = Util.getSQLToday();

		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT IdentityID FROM tblIdentity" + " WHERE"
					+ "  Name IS NOT NULL AND" + "  Name <> '' AND" + "  PublicKey IS NOT NULL AND"
					+ "  PublicKey <> '' AND" + "  PublishBoardList <> 0 AND" + "  LastSeen>=? AND"
					+ "  FailureCount<=1000" // XXX 1000
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
		String u = req.publicKey + Util.msgBase + '|' + req.date + "|BoardList|" + req.idx + ".xml";
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
