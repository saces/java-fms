/**
 * Identity XML Parser
 */
package plugins.FMS.xml;

import java.util.LinkedList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class IdentityXMLParser extends DefaultHandler {
	private final Identity identity;

	/**
	 * @param identity
	 */
	IdentityXMLParser(Identity identity) {
		this.identity = identity;
	}

	private LinkedList<String> lastTag;
	private LinkedList<StringBuilder> text;

	@Override
	public void startDocument() {
		lastTag = new LinkedList<String>();
		text = new LinkedList<StringBuilder>();
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		lastTag.addLast(qName);
		text.addLast(new StringBuilder());
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		String tag = lastTag.removeLast();
		StringBuilder content = text.removeLast();

		if ("Name".equals(tag)) {
			this.identity.setName(content.toString());
		} else if ("SingleUse".equals(tag)) {
			this.identity.setSingleUse(Boolean.parseBoolean(content.toString()));
		} else if ("PublishTrustList".equals(tag)) {
			this.identity.setPublishTrustList(Boolean.parseBoolean(content.toString()));
		} else if ("PublishBoardList".equals(tag)) {
			this.identity.setPublishBoardList(Boolean.parseBoolean(content.toString()));
		} else if ("FreesiteEdition".equals(tag)) {
			int ed = Integer.parseInt(content.toString());
			if (ed >= 0)
				this.identity.setFreesiteEdition(ed);
		} else if ("Identity".equals(tag)) {
		} else { // unknown tag
		}
	}

	@Override
	public void characters(char ch[], int start, int length) throws SAXException {
		StringBuilder sb = text.getLast();
		sb.append(ch, start, length);
	}
}