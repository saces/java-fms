package plugins.FMS.freenet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import plugins.FMS.Database;
import plugins.FMS.Util;
import freenet.pluginmanager.PluginRespirator;

public class UnknownIdentityRequester extends IdentityRequester {
	public UnknownIdentityRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor);
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		java.sql.Date today = Util.getSQLToday();

		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn
					.prepareStatement("SELECT IdentityID FROM tblIdentity WHERE LastSeen IS NULL ORDER BY RANDOM()");
			try {
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
	protected int getMaxRequests() {
		return Util.MaxIdentityRequests / 5;
	}
}
