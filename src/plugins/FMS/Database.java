package plugins.FMS;

import static java.sql.Connection.TRANSACTION_SERIALIZABLE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.derby.jdbc.EmbeddedDriver;

import freenet.support.Logger;
import freenet.support.io.Closer;

public class Database {
	private static Connection conn;
	private static boolean shutdown;

	/**
	 * Get the database connection. Create a new database or upgrade if needed
	 * 
	 * @return the database connection
	 * @throws SQLException
	 *             if error occur
	 */
	public static synchronized Connection getConnection() throws SQLException {
		if (shutdown)
			throw new SQLException("Database shutdown");

		new EmbeddedDriver();
		try {
			conn = DriverManager.getConnection("jdbc:derby:directory:FMSClone");
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			if ("XJ004".equals(e.getSQLState())) {
				conn = DriverManager.getConnection("jdbc:derby:directory:FMSClone;create=true");
				conn.setAutoCommit(false);
				initDB();
			} else {
				throw e;
			}
		}

		conn.setTransactionIsolation(TRANSACTION_SERIALIZABLE);
		return conn;
	}

	private static void initDB() throws SQLException {
		Statement stmt = conn.createStatement();
		try {
			InputStream initSQL = Database.class.getResourceAsStream("initdb.sql");
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(initSQL));

				String line = null;
				StringBuilder sql = new StringBuilder();
				while ((line = br.readLine()) != null) {
					sql.append(line);
					if (line.endsWith(";")) {
						sql.deleteCharAt(sql.length() - 1);
						Logger.debug(Database.class, "initDB(): " + sql);
						stmt.execute(sql.toString());
						sql = new StringBuilder();
					}
				}
			} catch (IOException e) {
				// Missing initSQL file?
				throw new RuntimeException(e);
			} finally {
				Closer.close(initSQL);
			}
			conn.commit();
		} finally {
			stmt.close();
		}
	}

	/**
	 * Shutdown database system
	 */
	public synchronized static void shutdown() {
		shutdown = true;
		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (Exception e) {
		}
	}
}
