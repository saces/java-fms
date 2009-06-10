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
}
