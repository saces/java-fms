package plugins.FMS.xml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SortedMap;

public class Message {
	public String uuid;
	public long date;
	public long time;
	public String subject;
	public List<String> boards;
	public String replyBoard;
	public SortedMap<Integer, String> parentPost;
	public String body;
	public LinkedHashMap<String, Long> attachments;
}
