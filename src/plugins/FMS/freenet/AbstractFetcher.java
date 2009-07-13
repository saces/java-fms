package plugins.FMS.freenet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import plugins.FMS.Database;
import plugins.FMS.xml2.Identity;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;

public abstract class AbstractFetcher implements ClientGetCallback, RequestClient, Runnable {
	protected static class Request {
		protected String publicKey;
		final protected int iid;
		final protected java.sql.Date date;
		protected int idx;

		protected Request(int id, java.sql.Date date) {
			this.iid = id;
			this.date = date;
		}

		@Override
		public String toString() {
			return "[id=" + iid + ", date=" + date + ", idx=" + idx + "]";
		}
	}

	protected AtomicBoolean scheduled = new AtomicBoolean();
	protected HighLevelSimpleClient hlsc;
	protected FetchContext ctx;
	protected ScheduledExecutorService exec;
	protected List<Request> pending = new ArrayList<Request>();
	protected Map<ClientGetter, Request> running = Collections.synchronizedMap(new HashMap<ClientGetter, Request>());
	protected final String reqTbl;
	protected boolean terminated;

	protected abstract List<Request> getPendingRequest() throws SQLException;

	protected abstract void fetchSuccess(Connection conn, Request req, FreenetURI uri, FetchResult result)
			throws Exception;

	protected abstract FreenetURI getURI(Request req);

	protected abstract int getMaxRequests();

	public AbstractFetcher(PluginRespirator pr, ScheduledExecutorService executor, String requestTable) {
		hlsc = pr.getHLSimpleClient();
		ctx = hlsc.getFetchContext().clone();
		reqTbl = requestTable;
		exec = executor;
	}

	public void start() {
		schedule(120);
	}

	public synchronized void stop() {
		terminated = true;
		for (ClientGetter getter : running.keySet()) {
			try {
				getter.cancel();
			} catch (Exception e) {
				Logger.error(this, "Error canceling request: " + e, e);
			}
		}
	}

	public void run() {
		if (terminated)
			return;
		if (!scheduled.getAndSet(false))
			return; // scheduled is false, some other instance have ran
		if (running.size() >= getMaxRequests())
			return;

		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

		if (pending.isEmpty()) {
			try {
				Logger.debug(this, "run(): re-populate list");
				pending.addAll(getPendingRequest());
			} catch (Exception e) {
				Logger.error(this, "run(): " + e, e);
			}
		}
		try {
			Connection conn = Database.getConnection();
			Iterator<Request> li = pending.iterator();
			synchronized (running) {
				try {
					while (running.size() < getMaxRequests() && li.hasNext()) {
						Request req = li.next();
						li.remove();
						try {
							setupRequest(conn, req);
							FreenetURI uri = getURI(req);
							Logger.debug(this, "run(): request for " + uri);
							try {
								ClientGetter getter = hlsc.fetch(uri, -1, this, this, ctx);
								running.put(getter, req);
							} catch (FetchException fe) {
								fetchFail(req, uri, fe);
							}
						} catch (Exception e) {
							Logger.error(this, "run(): " + e, e);
						}
						conn.commit();
					}
				} finally {
					conn.close();
				}

				// nothing is running, let's schedule myself 10 minutes later
				if (running.isEmpty())
					schedule(600);
			}
		} catch (Exception e) {
			Logger.error(this, "run(): " + e, e);
		}
	}

	protected void fetchFail(Request req, FreenetURI uri, FetchException e) {
		Logger.debug(this, "fetchFail(): uri=" + uri + " e=" + e);
		try {
			Connection conn = Database.getConnection();
			try {
				insertRequestTable(conn, req, false);
				conn.commit();
			} finally {
				conn.close();
			}
		} catch (SQLException se) {
			Logger.error(this, "fetchFail(): " + se, se);
		}
	}

	protected void fetchSuccess(Request req, FreenetURI uri, FetchResult result) {
		Logger.debug(this, "fetchSuccess(): uri=" + uri);
		try {
			Connection conn = Database.getConnection();
			try {
				try {
					fetchSuccess(conn, req, uri, result);
					insertRequestTable(conn, req, true);
					conn.commit();
					Logger.debug(this, "fetchSuccess(): Finish uri=" + uri);
				} catch (Exception e) {
					Logger.error(this, "fetchSuccess(): Fail uri=" + uri + " : " + e, e);
					conn.rollback();
					insertRequestTable(conn, req, false);
					conn.commit();
				}
			} finally {
				conn.close();
			}
		} catch (SQLException se) {
			Logger.error(this, "fetchSuccess(): Fail uri=" + uri + " : " + se, se);
		}
	}

	protected void setupRequest(Connection conn, Request req) throws SQLException {
		Identity id = Identity.load(conn, req.iid);
		req.publicKey = id.publicKey;

		PreparedStatement pstmt = conn.prepareStatement("SELECT RequestIndex, FailureCount, Loaded " //
				+ " FROM " + reqTbl + " A INNER JOIN " //
				+ " (" //
				+ "  SELECT IdentityID, Day, MAX(RequestIndex)" // 
				+ "    FROM " + reqTbl //
				+ "    WHERE Day=? AND IdentityID=?" //
				+ "   GROUP BY IdentityID,Day" //
				+ " ) B" //
				+ " ON A.IdentityID=B.IdentityID AND A.Day=B.Day AND A.RequestIndex=B.A.RequestIndex");
		try {
			pstmt.setDate(1, req.date);
			pstmt.setInt(2, req.iid);
			ResultSet rs = pstmt.executeQuery();
			try {
				if (rs.next()) {
					int rId = rs.getInt("RequestIndex");
					int fC = rs.getInt("FailureCount");
					boolean ld = rs.getBoolean("Loaded");

					if (ld || fC > 100) // already loaded OR too many failure
						rId++;
					req.idx = rId;
				} else
					req.idx = 0;
			} finally {
				rs.close();
			}
		} finally {
			pstmt.close();
		}
	}

	private void insertRequestTable(Connection conn, Request req, boolean success) throws SQLException {
		PreparedStatement pstmt = conn.prepareStatement("SELECT Loaded,FailureCount FROM " + reqTbl
				+ " WHERE IdentityID=? AND Day=? AND RequestIndex=? FOR UPDATE", ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE);
		try {
			pstmt.setInt(1, req.iid);
			pstmt.setDate(2, req.date);
			pstmt.setInt(3, req.idx);
			ResultSet rs = pstmt.executeQuery();
			try {
				if (rs.next()) {
					rs.updateBoolean("Loaded", success);
					if (!success)
						rs.updateInt("FailureCount", rs.getInt(2) + 1);
					rs.updateRow();
					return;
				}
			} finally {
				rs.close();
			}
		} finally {
			pstmt.close();
		}

		// not found, insert new row
		String sql;
		if (success)
			sql = "INSERT INTO " + reqTbl + " (Loaded,IdentityID,Day,RequestIndex) VALUES (1,?,?,?)";
		else
			sql = "INSERT INTO " + reqTbl + " (FailureCount,IdentityID,Day,RequestIndex) VALUES (1,?,?,?)";

		pstmt = conn.prepareStatement(sql);
		try {
			pstmt.setInt(1, req.iid);
			pstmt.setDate(2, req.date);
			pstmt.setInt(3, req.idx);
			pstmt.executeUpdate();
		} finally {
			pstmt.close();
		}
	}

	public final void onFailure(final FetchException e, final ClientGetter state, ObjectContainer container) {
		exec.execute(new Runnable() {
			public void run() {
				try {
					Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
					Request req = running.remove(state);
					fetchFail(req, state.getURI(), e);
				} finally {
					schedule(600);
				}
			}
		});
	}

	public final void onSuccess(final FetchResult result, final ClientGetter state, ObjectContainer container) {
		exec.execute(new Runnable() {
			public void run() {
				try {
					Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
					Request req = running.remove(state);
					fetchSuccess(req, state.getURI(), result);
				} finally {
					schedule(60);
				}
			}
		});
	}

	private void schedule(int time) {
		if (!scheduled.getAndSet(true))
			exec.schedule(this, time, TimeUnit.SECONDS);
	}

	public final void onMajorProgress(ObjectContainer container) {
		// ignore
	}

	public final boolean persistent() {
		return false;
	}

	public final void removeFrom(ObjectContainer container) {
		// no-op
	}
}
