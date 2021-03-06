/**
 *  OAIListFriendsLoader
 *  Copyright 2010 by Michael Peter Christen
 *  First released 29.04.2010 at http://yacy.net
 *
 *  This is a part of YaCy, a peer-to-peer based web search engine
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.LoaderDispatcher;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.crawler.retrieval.Response;

public class OAIListFriendsLoader {

    private static final long serialVersionUID = -8705115274655024604L;

    private static final HashMap<String, File> listFriends = new HashMap<String, File>();

    public static void init(final LoaderDispatcher loader, final Map<String, File> moreFriends) {
        listFriends.putAll(moreFriends);
        if (loader != null) for (final Map.Entry<String, File> oaiFriend: listFriends.entrySet()) {
            try {
                loader.loadIfNotExistBackground(new DigestURI(oaiFriend.getKey()), oaiFriend.getValue(), Integer.MAX_VALUE);
            } catch (final MalformedURLException e) {
            }
        }
    }

    public static Map<String, File> loadListFriendsSources(final File initFile, final File dataPath) {
        final Properties p = new Properties();
        final Map<String, File> m = new HashMap<String, File>();
        try {
            p.loadFromXML(new FileInputStream(initFile));
        } catch (final IOException e) {
            Log.logException(e);
            return m;
        }
        for (final Entry<Object, Object> e: p.entrySet()) m.put((String) e.getKey(), new File(dataPath, (String) e.getValue()));
        return m;
    }


    public static Map<String, String> getListFriends(final LoaderDispatcher loader) {
        final Map<String, String> map = new TreeMap<String, String>();
        Map<String, String> m;
        for (final Map.Entry<String, File> oaiFriend: listFriends.entrySet()) try {
            if (!oaiFriend.getValue().exists()) {
                final Response response = loader == null ? null : loader.load(loader.request(new DigestURI(oaiFriend.getKey()), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, true);
                if (response != null) FileUtils.copy(response.getContent(), oaiFriend.getValue());
            }

            if (oaiFriend.getValue().exists()) {
                final byte[] b = FileUtils.read(oaiFriend.getValue());
                if (b != null) {
                    m = new Parser(b).map;
                    if (m != null) map.putAll(m);
                }
            }

        } catch (final IOException e) {}
        return map;
    }

    // get a resumption token using a SAX xml parser from am input stream
    private static class Parser extends DefaultHandler {

        // class variables
        private final StringBuilder buffer;
        private boolean parsingValue;
        private SAXParser saxParser;
        private final InputStream stream;
        private Attributes atts;
        private int recordCounter;
        private final TreeMap<String, String> map;

        public Parser(final byte[] b) throws IOException {
            this.map = new TreeMap<String, String>();
            this.recordCounter = 0;
            this.buffer = new StringBuilder();
            this.parsingValue = false;
            this.atts = null;
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            this.stream = new ByteArrayInputStream(b);
            try {
                this.saxParser = factory.newSAXParser();
                this.saxParser.parse(this.stream, this);
            } catch (final SAXException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + UTF8.String(b));
            } catch (final IOException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + UTF8.String(b));
            } catch (final ParserConfigurationException e) {
                Log.logException(e);
                Log.logWarning("OAIListFriendsLoader.Parser", "OAIListFriends was not parsed:\n" + UTF8.String(b));
                throw new IOException(e.getMessage());
            } finally {
                try {
                    this.stream.close();
                } catch (final IOException e) {
                    Log.logException(e);
                }
            }
        }

        /*
         <?xml version="1.0" encoding="UTF-8"?>
         <BaseURLs>
         <baseURL id="http://roar.eprints.org/id/eprint/102">http://research.nla.gov.au/oai</baseURL>
         <baseURL id="http://roar.eprints.org/id/eprint/174">http://oai.bibsys.no/repository</baseURL>
         <baseURL id="http://roar.eprints.org/id/eprint/1064">http://oai.repec.openlib.org/</baseURL>
         </BaseURLs>
         */

        public void startElement(final String uri, final String name, final String tag, final Attributes atts) throws SAXException {
            if ("baseURL".equals(tag)) {
                this.recordCounter++;
                this.parsingValue = true;
                this.atts = atts;
            }
        }

        public void endElement(final String uri, final String name, final String tag) {
            if (tag == null) return;
            if ("baseURL".equals(tag)) {
                this.map.put(this.buffer.toString(), this.atts.getValue("id"));
                this.buffer.setLength(0);
                this.parsingValue = false;
            }
        }

        public void characters(final char ch[], final int start, final int length) {
            if (this.parsingValue) {
                this.buffer.append(ch, start, length);
            }
        }

    }

}
