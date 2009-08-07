// httpCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

/*
   Class documentation:
   This class has two purposes:
   1. provide a object that carries path and header information
      that shall be used as objects within a scheduler's stack
   2. static methods for a cache control and cache aging
    the class shall also be used to do a cache-cleaning and index creation
*/

package de.anomic.http.client;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.anomic.http.metadata.ResponseHeader;
import de.anomic.kelondro.blob.ArrayStack;
import de.anomic.kelondro.blob.Compressor;
import de.anomic.kelondro.blob.Heap;
import de.anomic.kelondro.blob.MapView;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class Cache {
    
    private static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    private static final String FILE_DB_NAME = "file.array";

    private static MapView responseHeaderDB = null;
    private static Compressor fileDB = null;
    private static ArrayStack fileDBunbuffered = null;
    
    private static long maxCacheSize = 0l;
    private static File cachePath = null;
    private static String prefix;
    public static final Log log = new Log("HTCACHE");
    
    public static void init(final File htCachePath, String peerSalt, final long CacheSizeMax) {
        
        cachePath = htCachePath;
        maxCacheSize = CacheSizeMax;
        prefix = peerSalt;

        // set/make cache path
        if (!htCachePath.exists()) {
            htCachePath.mkdirs();
        }

        // open the response header database
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        Heap blob = null;
        try {
            blob = new Heap(dbfile, yacySeedDB.commonHashLength, Base64Order.enhancedCoder, 1024 * 1024);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        responseHeaderDB = new MapView(blob, 500, '_');
        try {
            fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, 12, Base64Order.enhancedCoder, 1024 * 1024 * 2);
            fileDBunbuffered.setMaxSize(maxCacheSize);
            fileDB = new Compressor(fileDBunbuffered, 2 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
     */
    public static void setCacheSize(final long newCacheSize) {
        maxCacheSize = newCacheSize;
        fileDBunbuffered.setMaxSize(maxCacheSize);
    }
    
    /**
     * close the databases
     */
    public static void close() {
        responseHeaderDB.close();
        fileDB.close(true);
    }
    
    public static void store(yacyURL url, final ResponseHeader responseHeader, byte[] file) {
        if (responseHeader != null && file != null) try {
            // store the response header into the header database
            final HashMap<String, String> hm = new HashMap<String, String>();
            hm.putAll(responseHeader);
            hm.put("@@URL", url.toNormalform(true, false));
            responseHeaderDB.put(url.hash(), hm);
            fileDB.put(url.hash().getBytes("UTF-8"), file);
            if (log.isFine()) log.logFine("stored in cache: " + url.toNormalform(true, false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * check if the responseHeaderDB and the fileDB has an entry for the given url
     * @param url the url of the resource
     * @return true if the content of the url is in the cache, false othervise
     */
    public static boolean has(final yacyURL url) {
        try {
            return responseHeaderDB.has(url.hash()) && fileDB.has(url.hash().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Returns an object containing metadata about a cached resource
     * @param url the {@link URL} of the resource
     * @return an {@link IResourceInfo info object}
     * @throws <b>IllegalAccessException</b> if the {@link SecurityManager} doesn't allow instantiation
     * of the info object with the given protocol
     * @throws <b>UnsupportedProtocolException</b> if the protocol is not supported and therefore the
     * info object couldn't be created
     */
    public static ResponseHeader getResponseHeader(final yacyURL url) {    
        
        // loading data from database
        Map<String, String> hdb;
        try {
            hdb = responseHeaderDB.get(url.hash());
        } catch (final IOException e) {
            return null;
        }
        if (hdb == null) return null;
        
        return new ResponseHeader(null, hdb);
    }
    
    /**
     * Returns the content of a cached resource as {@link InputStream}
     * @param url the requested resource
     * @return the resource content as {@link InputStream}. In no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public static InputStream getContentStream(final yacyURL url) {
        // load the url as resource from the cache
        byte[] b = getContent(url);
        if (b == null) return null;
        return new ByteArrayInputStream(b);
    }
    
    /**
     * Returns the content of a cached resource as byte[]
     * @param url the requested resource
     * @return the resource content as byte[]. In no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public static byte[] getContent(final yacyURL url) {
        // load the url as resource from the cache
        try {
            return fileDB.get(url.hash().getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * requesting the content length of a resource is discouraged since it may
     * be performed by loading of the resource from the cache and then measuring the
     * size after decompression of the content. This may use a lot of CPU resources
     * and maybe cause also high IO. Please omit usage of this method as much as possible.
     * @param url
     * @return the size of the cached content
     */
    public static long getResourceContentLength(final yacyURL url) {
        // first try to get the length from the response header,
        // this is less costly than loading the content from its gzipped cache
        ResponseHeader responseHeader = getResponseHeader(url);
        if (responseHeader != null) {
            long length = responseHeader.getContentLength();
            if (length > 0) return length; 
        }
        // load the url as resource from the cache (possibly decompress it),
        // and get the length from the content array size
        try {
            return fileDB.length(url.hash().getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * removed response header and cached content from the database
     * @param url
     * @throws IOException
     */
    public static void delete(yacyURL url) throws IOException {
        responseHeaderDB.remove(url.hash());
        fileDB.remove(url.hash().getBytes("UTF-8"));
    }
}