package plugins.FMS.freenet;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import plugins.FMS.Database;
import plugins.FMS.SQLUtil;
import plugins.FMS.Util;
import plugins.FMS.xml.Identity;
import plugins.FMS.xml.Message;
import plugins.FMS.xml.MessageXMLParser;
import freenet.client.FetchResult;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Base64;
import freenet.support.Logger;

public class MessageRequester extends AbstractFetcher {
	public MessageRequester(PluginRespirator pr, ScheduledExecutorService executor) {
		super(pr, executor, "tblMessageRequests");
	}

	@Override
	protected void fetchSuccess(final Connection conn, final Request req, FreenetURI uri, FetchResult result)
			throws Exception {
		Logger.minor(this, "Got Message from " + req + " uri=" + uri);

		Message msg = MessageXMLParser.parse(uri, result);
		String routingKey = "@" + Base64.encode(uri.getRoutingKey()).replaceAll("[~\\-]", "");
		if (!msg.uuid.endsWith(routingKey)) {
			Logger.normal(this, "Invalid UUID suffix expected=" + routingKey + ", uuid=" + msg.uuid + ", uri=" + uri);
			return;
		}

		int messageId;
		PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tblMessage "
				+ " (UUID,IdentityID,ReplyBoard,PostTime,Subject,Body)" // 
				+ " VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
		try {
			pstmt.setString(1, msg.uuid);
			pstmt.setInt(2, req.iid);
			pstmt.setInt(3, SQLUtil.findOrCreateBoard(conn, msg.replyBoard));
			pstmt.setTimestamp(4, new Timestamp(msg.date + msg.time));
			pstmt.setString(5, msg.subject);
			pstmt.setString(6, msg.body);
			pstmt.executeUpdate();
			ResultSet rs = pstmt.getGeneratedKeys();
			try {
				rs.next();
				messageId = rs.getInt(1);
			} finally {
				rs.close();
			}
		} finally {
			pstmt.close();
		}

		pstmt = conn
				.prepareStatement("INSERT INTO tblMessageParent (MessageID,ParentOrder,ParentUUID,ParentMessageID) VALUES (?,?,?,?)");
		try {
			for (Map.Entry<Integer, String> u : msg.parentPost.entrySet()) {
				pstmt.setInt(1, messageId);
				pstmt.setInt(2, u.getKey());
				pstmt.setString(3, u.getValue());
				pstmt.setObject(1, SQLUtil.findMessageByUUID(conn, u.getValue()), Types.INTEGER);
				pstmt.execute();
			}
		} finally {
			pstmt.close();
		}

		System.out.println(new Date(msg.date + msg.time));
		System.out.println(msg.subject);
		System.out.println(msg);
	}

	@Override
	protected List<Request> getPendingRequest() throws SQLException {
		List<Request> list = new ArrayList<Request>();
		Connection conn = Database.getConnection();
		try {
			PreparedStatement pstmt = conn.prepareStatement( //
					"SELECT IdentityID, Day, RequestIndex FROM tblMessageRequests"
							+ " WHERE Loaded <> 1 ORDER BY Day DESC, FailureCount");
			try {
				ResultSet rs = pstmt.executeQuery();
				try {
					while (rs.next()) {
						Request req = new Request(rs.getInt("IdentityID"), rs.getDate("Day"));
						req.idx = rs.getInt("RequestIndex");
						list.add(req);
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
		String u = req.publicKey + Util.msgBase //
				+ '|' + req.date.toString() + "|Message-" + req.idx;
		try {
			return new FreenetURI(u);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected void setupRequest(Connection conn, Request req) throws SQLException {
		Identity id = Identity.load(conn, req.iid);
		req.publicKey = id.getPublicKey();
	}

	@Override
	protected int getMaxRequests() {
		return Util.MaxIdentityRequests;
	}
}
