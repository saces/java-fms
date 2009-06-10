package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import plugins.FMS.Database;
import plugins.FMS.Util;
import plugins.FMS.xml.Identity;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;

public class IdentityRequester extends AbstractFetcher {
	public IdentityRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblIdentityRequests");
	}

	@Override
	protected void fetchSuccess(Connection conn, Request req, FreenetURI uri, FetchResult result) throws SQLException {
		Identity id = new Identity(req.iid, uri, result);
		id.update(conn);

		if (!id.isPublishTrustList()) {
			PreparedStatement pstmt = conn.prepareStatement("DELETE FROM tblPeerTrust WHERE IdentityId=?");
			try {
				pstmt.setInt(1, req.iid);
				pstmt.execute();
			} finally {
				pstmt.close();
			}
		}
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		java.sql.Date today = Util.getSQLToday();

		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT IdentityID" + " FROM tblIdentity" + " WHERE "
					+ "  PublicKey <> ''" + " AND" + "  LastSeen IS NOT NULL" + " AND" + "  LastSeen<? " + " AND"
					+ "  FailureCount<=1000" // XXX 1000
					+ " ORDER BY LastSeen, FailureCount");
			try {
				pstmt.setDate(1, today);
				ResultSet rs = pstmt.executeQuery();
				try {
					while (rs.next())
						list.add(new Request(rs.getInt("IdentityID"), today));
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
		String u = req.publicKey + Util.msgBase + '|' + req.date + "|Identity|" + req.idx + ".xml";
		try {
			return new FreenetURI(u);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected int getMaxRequests() {
		return Util.MaxIdentityRequests * 4 / 5 + Util.MaxIdentityRequests % 5;
	}
}
