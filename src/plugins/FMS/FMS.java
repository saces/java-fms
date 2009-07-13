package plugins.FMS;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import plugins.FMS.freenet.BoardListRequester;
import plugins.FMS.freenet.IdentityRequester;
import plugins.FMS.freenet.MessageListRequester;
import plugins.FMS.freenet.MessageRequester;
import plugins.FMS.freenet.TrustListRequester;
import plugins.FMS.freenet.UnknownIdentityRequester;
import freenet.node.Node;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class FMS implements FredPlugin, FredPluginHTTP, FredPluginThreadless, FredPluginVersioned,
		FredPluginRealVersioned {
	private ScheduledExecutorService executor;
	private IdentityRequester identityRequester;
	private UnknownIdentityRequester unknownIdentityRequester;
	private TrustListRequester trustListRequester;
	private Node node;
	private BoardListRequester boardListRequester;
	private MessageListRequester messageListRequester;
	private MessageRequester messageRequester;

	private static class FMSThreadFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "FMSClone");
			t.setDaemon(true);
			return t;
		}
	}

	public void runPlugin(PluginRespirator pr) {
		node = pr.getNode();
		executor = new ScheduledThreadPoolExecutor(4, new FMSThreadFactory());

		identityRequester = new IdentityRequester(pr, executor);
		unknownIdentityRequester = new UnknownIdentityRequester(pr, executor);
		trustListRequester = new TrustListRequester(pr, executor);
		boardListRequester = new BoardListRequester(pr, executor);
		messageListRequester = new MessageListRequester(pr, executor);
		messageRequester = new MessageRequester(pr, executor);

		identityRequester.start();
		unknownIdentityRequester.start();
		trustListRequester.start();
		boardListRequester.start();
		messageListRequester.start();
		messageRequester.start();
	}

	public void terminate() {
		messageRequester.stop();
		messageListRequester.stop();
		boardListRequester.stop();
		trustListRequester.stop();
		unknownIdentityRequester.stop();
		identityRequester.stop();
		executor.shutdownNow();
		Database.shutdown();
	}

	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		HTMLNode pageNode = new HTMLNode.HTMLDoctype("html", "-//W3C//DTD XHTML 1.1//EN");
		HTMLNode htmlNode = pageNode.addChild("html");
		htmlNode.addChild("head");
		HTMLNode bodyNode = htmlNode.addChild("body");
		HTMLNode formNode = bodyNode.addChild("form", "method", "post");
		formNode.addChild("input", new String[] { "name", "type", "value" }, new String[] { "formPassword", "hidden",
				node.clientCore.formPassword });

		String sql = request.getParam("sql", null);
		if (sql == null)
			sql = request.getPartAsString("sql", 4096);

		HTMLNode textNode = formNode.addChild("textarea", new String[] { "name", "rows", "cols" }, new String[] {
				"sql", "10", "80" });
		if (sql != null)
			textNode.addChild("#", sql);
		formNode.addChild("input", "type", "submit");

		if (sql != null && sql.length() != 0) {
			try {
				Connection conn = Database.getConnection();
				try {
					Statement stmt = conn.createStatement();

					boolean hasResult = stmt.execute(sql);
					if (hasResult) {
						ResultSet rs = stmt.getResultSet();
						try {
							ResultSetMetaData m = rs.getMetaData();
							int cc = m.getColumnCount();

							HTMLNode tableNode = bodyNode.addChild("table", "border", "1");
							HTMLNode tableHead = tableNode.addChild("tr");
							for (int i = 1; i <= cc; i++)
								tableHead.addChild("th", m.getColumnLabel(i));

							while (rs.next()) {
								HTMLNode tableRow = tableNode.addChild("tr");
								for (int i = 1; i <= cc; i++) {
									Object v = rs.getObject(i);
									if (v != null)
										tableRow.addChild("td", v.toString());
									else
										tableRow.addChild("td", "style", "color: gray").addChild("#", "NULL");

								}
							}
						} finally {
							rs.close();
						}
					} else {
						bodyNode.addChild("div", "style", "color: black").addChild("%",
								stmt.getUpdateCount() + " rows updated");
					}
					SQLWarning warn = conn.getWarnings();
					while (warn != null) {
						bodyNode.addChild("div", "style", "color: blue").addChild("%", "WARNING: " + warn.getMessage());
						warn = warn.getNextWarning();
					}
					conn.commit();
				} finally {
					conn.close();
				}
			} catch (SQLException e) {
				do {
					bodyNode.addChild("div", "style", "color: red").addChild("%", "ERROR: " + e.getMessage());
					e = e.getNextException();
				} while (e != null);
			}
		}

		return pageNode.generate();
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return handleHTTPGet(request);
	}

	public String getVersion() {
		Package pack = FMS.class.getPackage();
		return pack == null ? "@ukn@" : pack.getImplementationVersion();
	}

	public long getRealVersion() {
		return 0;
	}
}
