/**
 *  OpenGeoDBLocalization
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 04.10.2009 on http://yacy.net
 *
 *  This file is part of YaCy Content Integration
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

package net.yacy.document.geolocalization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import net.yacy.document.StringBuilderComparator;
import net.yacy.kelondro.logging.Log;


/**
 * this class loads and parses database dumps from the OpenGeoDB project
 * files can be loaded from http://sourceforge.net/projects/opengeodb/files/
 * this class is used by the LibraryProvider, which expects input files inside
 * DATA\DICTIONARIES\source
 *
 * ATTENTION:
 * if this class is used, expect an extra memory usage of more than 100MB!
 *
 * This class will provide a super-fast access to the OpenGeoDB,
 * since all request are evaluated using data in the RAM.
 */
public class OpenGeoDBLocalization implements Localization {

    private final Map<Integer, String>       locTypeHash2locType;
    private final Map<Integer, Location>     id2loc;
    private final Map<Integer, Integer>      id2locTypeHash;
    private final TreeMap<StringBuilder, List<Integer>> name2ids;
    private final TreeMap<StringBuilder, List<Integer>> kfz2ids;
    private final Map<String, List<Integer>> predial2ids;
    private final Map<String, Integer>       zip2id;
    private final File file;

    public OpenGeoDBLocalization(final File file, final boolean lonlat) {

        this.file                = file;
        this.locTypeHash2locType = new HashMap<Integer, String>();
        this.id2loc              = new HashMap<Integer, Location>();
        this.id2locTypeHash      = new HashMap<Integer, Integer>();
        this.name2ids            = new TreeMap<StringBuilder, List<Integer>>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
        this.kfz2ids             = new TreeMap<StringBuilder, List<Integer>>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
        this.predial2ids         = new HashMap<String, List<Integer>>();
        this.zip2id              = new HashMap<String, Integer>();

        if (file == null || !file.exists()) return;
        BufferedReader reader = null;
        try {
            InputStream is = new FileInputStream(file);
            if (file.getName().endsWith(".gz")) is = new GZIPInputStream(is);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;

            // read lines
            String[] v;
            Integer id;
            String h;
            float lon, lat;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("INSERT INTO ")) continue;
                if (!line.endsWith(");")) continue;
                line = line.substring(12);
                //p = line.indexOf(' '); if (p < 0) continue;
                if (line.startsWith("geodb_coordinates ")) {
                    line = line.substring(18 + 7);v = line.split(",");
                    v = line.split(",");
                    if (lonlat) {
                        lon = Float.parseFloat(v[2]);
                        lat = Float.parseFloat(v[3]);
                    } else {
                        lat = Float.parseFloat(v[2]);
                        lon = Float.parseFloat(v[3]);
                    }
                    this.id2loc.put(Integer.parseInt(v[0]), new Location(lon, lat));
                }
                if (line.startsWith("geodb_textdata ")) {
                    line = line.substring(15 + 7);
                    v = line.split(",");
                    if (v[1].equals("500100000")) { // Ortsname
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.name2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.name2ids.put(new StringBuilder(h), l);
                        final Location loc = this.id2loc.get(id);
                        if (loc != null) loc.setName(h);
                    } else if (v[1].equals("500400000")) { // Vorwahl
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.predial2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.predial2ids.put(h, l);
                    } else if (v[1].equals("400300000")) { // Ortstyp
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        final Integer hc = h.hashCode();
                        final String t = this.locTypeHash2locType.get(hc);
                        if (t == null) this.locTypeHash2locType.put(hc, h);
                        this.id2locTypeHash.put(id, hc);
                    } else if (v[1].equals("500300000")) { // PLZ
                        this.zip2id.put(removeQuotes(v[2]), Integer.parseInt(v[0]));
                    } else if (v[1].equals("500500000")) { // KFZ-Kennzeichen
                        id = Integer.parseInt(v[0]);
                        h = removeQuotes(v[2]);
                        List<Integer> l = this.kfz2ids.get(h);
                        if (l == null) l = new ArrayList<Integer>(1);
                        l.add(id);
                        this.kfz2ids.put(new StringBuilder(h), l);
                    }
                }
                continue;
            }
            reader.close();
        } catch (final IOException e) {
            Log.logException(e);
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {}
        }
    }

    private static final String removeQuotes(String s) {
        if (s.length() > 0 && s.charAt(0) != '\'') return s;
        if (s.charAt(s.length() - 1) != '\'') return s;
        s = s.substring(1, s.length() - 1);
        return s;
    }

    public int locations() {
        return this.id2loc.size();
    }

    /**
     * check database tables against occurrences of this entity
     * the anyname - String may be one of:
     * - name of a town, villa, region etc
     * - zip code
     * - telephone prefix
     * - kfz sign
     * @param anyname
     * @return
     */
    public TreeSet<Location> find(final String anyname, final boolean locationexact) {
        final HashSet<Integer> r = new HashSet<Integer>();
        List<Integer> c;
        final StringBuilder an = new StringBuilder(anyname);
        if (locationexact) {
            c = this.name2ids.get(an); if (c != null) r.addAll(c);
        } else {
            final SortedMap<StringBuilder, List<Integer>> cities = this.name2ids.tailMap(an);
            for (final Map.Entry<StringBuilder, List<Integer>> e: cities.entrySet()) {
            	if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(e.getKey(), an)) r.addAll(e.getValue()); else break;
            }
            c = this.kfz2ids.get(an); if (c != null) r.addAll(c);
            c = this.predial2ids.get(anyname); if (c != null) r.addAll(c);
            final Integer i = this.zip2id.get(anyname); if (i != null) r.add(i);
        }
        final TreeSet<Location> a = new TreeSet<Location>();
        for (final Integer e: r) {
            final Location w = this.id2loc.get(e);
            if (w != null) a.add(w);
        }
        return a;
    }

    /**
     * read the dictionary and construct a set of recommendations to a given string
     * @param s input value that is used to match recommendations
     * @return a set that contains all words that start with the input value
     */
    public Set<String> recommend(final String s) {
        final Set<String> a = new HashSet<String>();
        final StringBuilder an = new StringBuilder(s);
        if (s.length() == 0) return a;
        final SortedMap<StringBuilder, List<Integer>> t = this.name2ids.tailMap(an);
        for (final StringBuilder r: t.keySet()) {
            if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, an)) a.add(r.toString()); else break;
        }
        return a;
    }

    public Set<StringBuilder> recommend(final StringBuilder s) {
        final Set<StringBuilder> a = new HashSet<StringBuilder>();
        if (s.length() == 0) return a;
        final SortedMap<StringBuilder, List<Integer>> t = this.name2ids.tailMap(s);
        for (final StringBuilder r: t.keySet()) {
            if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(r, s)) a.add(r); else break;
        }
        return a;
    }

    public String nickname() {
        return this.file.getName();
    }

    public int hashCode() {
        return nickname().hashCode();
    }

    public boolean equals(final Object other) {
        if (!(other instanceof Localization)) return false;
        return nickname().equals(((Localization) other).nickname());
    }
}
