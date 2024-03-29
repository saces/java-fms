/*
 * Copyright (C) 2004, 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 07. March 2004 by Joe Walnes
 * 
 * Modified by Felinephile to disable DTD validation
 */
package plugins.FMS.xml2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.AbstractXmlDriver;
import com.thoughtworks.xstream.io.xml.DomReader;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XmlFriendlyReplacer;


public class NVDomDriver extends AbstractXmlDriver {

    private final String encoding;
    private final DocumentBuilderFactory documentBuilderFactory;

    /**
     * Construct a DomDriver.
     */
    public NVDomDriver() {
		this("UTF-8");
    }

    /**
     * Construct a DomDriver with a specified encoding. The created DomReader will ignore any
     * encoding attribute of the XML header though.
     */
    public NVDomDriver(String encoding) {
        this(encoding, new XmlFriendlyReplacer());
    }

    /**
     * @since 1.2
     */
    public NVDomDriver(String encoding, XmlFriendlyReplacer replacer) {
        super(replacer);
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			documentBuilderFactory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
		} catch (ParserConfigurationException e) {
			// impossible
		}
		documentBuilderFactory.setValidating(false);
        this.encoding = encoding;
    }

    public HierarchicalStreamReader createReader(Reader xml) {
        return createReader(new InputSource(xml));
    }

    public HierarchicalStreamReader createReader(InputStream xml) {
        return createReader(new InputSource(xml));
    }

    private HierarchicalStreamReader createReader(InputSource source) {
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            if (encoding != null) {
                source.setEncoding(encoding);
            }
            Document document = documentBuilder.parse(source);
            return new DomReader(document, xmlFriendlyReplacer());
        } catch (FactoryConfigurationError e) {
            throw new StreamException(e);
        } catch (ParserConfigurationException e) {
            throw new StreamException(e);
        } catch (SAXException e) {
            throw new StreamException(e);
        } catch (IOException e) {
            throw new StreamException(e);
        }
    }

    public HierarchicalStreamWriter createWriter(Writer out) {
        return new PrettyPrintWriter(out, xmlFriendlyReplacer());
    }

    public HierarchicalStreamWriter createWriter(OutputStream out) {
        try {
            return createWriter(encoding != null
                ? new OutputStreamWriter(out, encoding)
                : new OutputStreamWriter(out));
        } catch (UnsupportedEncodingException e) {
            throw new StreamException(e);
        }
    }
}
