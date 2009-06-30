package plugins.FMS;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLUtil {
	public static int findOrCreateBoard(Connection conn, String name) throws SQLException {
		final PreparedStatement pFindBoard = conn.prepareStatement("SELECT BoardID FROM tblBoard WHERE BoardName=?");
		try {
			pFindBoard.setString(1, name);
			ResultSet rs = pFindBoard.executeQuery();
			try {
				if (rs.next()) {
					return rs.getInt("BoardID");
				} else {
					final PreparedStatement pAddBoard = conn.prepareStatement(
							"INSERT INTO tblBoard (BoardName) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
					pAddBoard.setString(1, name);
					pAddBoard.executeUpdate();
					ResultSet grs = pAddBoard.getGeneratedKeys();
					try {
						grs.next();
						return grs.getInt(1);
					} finally {
						grs.close();
					}
				}
			} finally {
				rs.close();
			}
		} finally {
			pFindBoard.close();
		}
	}

	/**
	 * Find a message by UUID
	 * 
	 * @param conn
	 *            SQL Connection
	 * @param uuid
	 *            message uuid
	 * @return messageID if found; <code>null</code> otherwise.
	 * @throws SQLException
	 */
	public static Integer findMessageByUUID(Connection conn, String uuid) throws SQLException {
		final PreparedStatement pFindBoard = conn.prepareStatement("SELECT MessageID FROM tblMessage WHERE UUID=?");
		try {
			pFindBoard.setString(1, uuid);
			ResultSet rs = pFindBoard.executeQuery();
			try {
				if (rs.next()) {
					return rs.getInt("MessageID");
				} else {
					return null;
				}
			} finally {
				rs.close();
			}
		} finally {
			pFindBoard.close();
		}
	}
}
