// BDecoder.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2010
// Created 03.01.2010
//
// this is an BDecoder implementation according to http://wiki.theory.org/BitTorrentSpecification
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

package net.yacy.kelondro.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BDecoder {

    private final static byte[] _e = "e".getBytes();
    private final static byte[] _i = "i".getBytes();
    private final static byte[] _d = "d".getBytes();
    private final static byte[] _l = "l".getBytes();
    private final static byte[] _p = ":".getBytes();
    
    private final byte[] b;
    private int pos;
    
    public BDecoder(byte[] b) {
        this.b = b;
        this.pos = 0;
    }
    
    public static enum BType {
        string, integer, list, dictionary;
    }
    
    public static interface BObject {
        public BType getType();
        public byte[] getString();
        public long getInteger();
        public List<BObject> getList();
        public Map<String, BObject> getMap();
        public String toString();
        public void toStream(OutputStream os) throws IOException;
    }
    
    public static abstract class BDfltObject implements BObject {

        public long getInteger() {
            throw new UnsupportedOperationException();
        }

        public List<BObject> getList() {
            throw new UnsupportedOperationException();
        }

        public Map<String, BObject> getMap() {
            throw new UnsupportedOperationException();
        }

        public byte[] getString() {
            throw new UnsupportedOperationException();
        }

        public BType getType() {
            throw new UnsupportedOperationException();
        }

        public String toString() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    public static class BStringObject extends BDfltObject implements BObject {
        private byte[] b;
        public BStringObject(byte[] b) {
            this.b = b;
        }
        public BType getType() {
            return BType.string;
        }
        public byte[] getString() {
            return this.b;
        }
        public String toString() {
            return new String(this.b);
        }
        public void toStream(OutputStream os) throws IOException {
            os.write(Integer.toString(this.b.length).getBytes());
            os.write(_p);
            os.write(this.b);
        }
        public static void toStream(OutputStream os, byte[] b) throws IOException {
            os.write(Integer.toString(b.length).getBytes());
            os.write(_p);
            os.write(b);
        }
        public static void toStream(OutputStream os, String s) throws IOException {
            os.write(Integer.toString(s.length()).getBytes());
            os.write(_p);
            os.write(s.getBytes());
        }
    }
    
    public static class BListObject extends BDfltObject implements BObject {
        private List<BObject> l;
        public BListObject(List<BObject> l) {
            this.l = l;
        }
        public BType getType() {
            return BType.list;
        }
        public List<BObject> getList() {
            return this.l;
        }
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("[");
            for (BObject o: l) s.append(o.toString()).append(",");
            s.setLength(s.length() - 1);
            s.append("]");
            return s.toString();
        }
        public void toStream(OutputStream os) throws IOException {
            os.write(_l);
            for (BObject bo: this.l) bo.toStream(os);
            os.write(_e);
        }
    }
    
    public static class BDictionaryObject extends BDfltObject implements BObject {
        private Map<String, BObject> m;
        public BDictionaryObject(Map<String, BObject> m) {
            this.m = m;
        }
        public BType getType() {
            return BType.dictionary;
        }
        public Map<String, BObject> getMap() {
            return this.m;
        }
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("{");
            for (Map.Entry<String, BObject> e: m.entrySet()) s.append(e.getKey()).append(":").append(e.getValue().toString()).append(","); 
            s.setLength(s.length() - 1);
            s.append("}");
            return s.toString();
        }
        public void toStream(OutputStream os) throws IOException {
            os.write(_d);
            for (Map.Entry<String, BObject> e: this.m.entrySet()) {
                new BStringObject(e.getKey().getBytes()).toStream(os);
                e.getValue().toStream(os);
            }
            os.write(_e);
        }
        public static void toStream(
                OutputStream os,
                String key, byte[] value
                ) throws IOException {
            os.write(_d);
            BStringObject.toStream(os, key);
            BStringObject.toStream(os, value);
            os.write(_e);
        }
        public static void toStream(
                OutputStream os,
                String key0, byte[] value0,
                String key1, byte[] value1
                ) throws IOException {
            os.write(_d);
            BStringObject.toStream(os, key0);
            BStringObject.toStream(os, value0);
            BStringObject.toStream(os, key1);
            BStringObject.toStream(os, value1);
            os.write(_e);
        }
        public static void toStream(
                OutputStream os,
                String key0, byte[] value0,
                String key1, byte[] value1,
                String key2, byte[] value2
                ) throws IOException {
            os.write(_d);
            BStringObject.toStream(os, key0);
            BStringObject.toStream(os, value0);
            BStringObject.toStream(os, key1);
            BStringObject.toStream(os, value1);
            BStringObject.toStream(os, key2);
            BStringObject.toStream(os, value2);
            os.write(_e);
        }
        public static void toStream(
                OutputStream os,
                String key0, byte[] value0,
                String key1, byte[] value1,
                String key2, byte[] value2,
                String key3, byte[] value3
                ) throws IOException {
            os.write(_d);
            BStringObject.toStream(os, key0);
            BStringObject.toStream(os, value0);
            BStringObject.toStream(os, key1);
            BStringObject.toStream(os, value1);
            BStringObject.toStream(os, key2);
            BStringObject.toStream(os, value2);
            BStringObject.toStream(os, key3);
            BStringObject.toStream(os, value3);
            os.write(_e);
        }
    }
    
    public static class BIntegerObject extends BDfltObject implements BObject {
        private long i;
        public BIntegerObject(long i) {
            this.i = i;
        }
        public BType getType() {
            return BType.integer;
        }
        public long getInteger() {
            return this.i;
        }
        public String toString() {
            return Long.toString(this.i);
        }
        public void toStream(OutputStream os) throws IOException {
            os.write(_i);
            os.write(Long.toString(this.i).getBytes());
            os.write(_e);
        }
    }
    
    private Map<String, BObject> convertToMap(final List<BObject> list) {
        final Map<String, BObject> m = new LinkedHashMap<String, BObject>();
        final int length = list.size();
        for (int i = 0; i < length; i += 2) {
            final byte[] key = list.get(i).getString();
            BObject value = null;
            if (i + 1 < length) {
                value = list.get(i + 1);
            }
            m.put(new String(key), value);
        }
        return m;
    }

    private List<BObject> readList() {
        final List<BObject> list = new ArrayList<BObject>();
        char ch = (char) b[pos];
        while (ch != 'e') {
            BObject bo = parse();
            if (bo == null) {pos++; break;}
            list.add(bo);
            ch = (char) b[pos];
        }
        pos++;
        return list;
    }
    
    public BObject parse() {
        if (pos >= b.length) return null;
        char ch = (char) b[pos];
        if ((ch >= '0') && (ch <= '9')) {
            int end = pos;
            end++;
            while (b[end] != ':') ++end;
            final int len = Integer.parseInt(new String(b, pos, end - pos));
            final byte[] s = new byte[len];
            System.arraycopy(b, end + 1, s, 0, len);
            pos = end + len + 1;
            return new BStringObject(s);
        } else if (ch == 'l') {
            pos++;
            return new BListObject(readList());
        } else if (ch == 'd') {
            pos++;
            return new BDictionaryObject(convertToMap(readList()));
        } else if (ch == 'i') {
            pos++;
            int end = pos;
            while (b[end] != 'e') ++end;
            BIntegerObject io = new BIntegerObject(Long.parseLong(new String(b, pos, end - pos)));
            pos = end + 1;
            return io;
        } else {
            return null;
        }
    }
    /*
    public static BObject parse(InputStream is) {
        if (is.available() < 1) return null;
        char ch = (char) is.read();
        if ((ch >= '0') && (ch <= '9')) {
            StringBuilder s = new StringBuilder();
            s.append(ch);
            while ((ch = (char) is.read()) != ':') s.append(ch);
            int len = Integer.parseInt(s.toString());
            byte[] b = new byte[len];
            is.read(b);
            return new BStringObject(new String(b));
        } else if (ch == 'l') {
            pos++;
            return new BListObject(readList());
        } else if (ch == 'd') {
            pos++;
            return new BDictionaryObject(convertToMap(readList()));
        } else if (ch == 'i') {
            pos++;
            int end = pos;
            while (b[end] != 'e') ++end;
            BIntegerObject io = new BIntegerObject(Long.parseLong(new String(b, pos, end - pos)));
            pos = end + 1;
            return io;
        } else {
            return null;
        }
    }
    */
    
    public static void print(BObject bo, int t) {
        for (int i = 0; i < t; i++) System.out.print(" ");
        if (bo.getType() == BType.integer) System.out.println(bo.getInteger());
        if (bo.getType() == BType.string) System.out.println(bo.getString());
        if (bo.getType() == BType.list) {
            System.out.println("[");
            //for (int i = 0; i < t + 1; i++) System.out.print(" ");
            for (BObject o: bo.getList()) print(o, t + 1);
            for (int i = 0; i < t; i++) System.out.print(" ");
            System.out.println("]");
        }
        if (bo.getType() == BType.dictionary) {
            System.out.println("{");
            for (Map.Entry<String, BObject> e: bo.getMap().entrySet()) {
                for (int i = 0; i < t + 1; i++) System.out.print(" ");
                System.out.print(e.getKey());
                System.out.println(":");
                print(e.getValue(), t + 2);
            }
            for (int i = 0; i < t; i++) System.out.print(" ");
            System.out.println("}");
        }
    }

    public static void main(String[] args) {
        try {
            byte[] b = FileUtils.read(new File(args[0]));
            BDecoder bdecoder = new BDecoder(b);
            BObject o = bdecoder.parse();
            print(o, 0);
            System.out.println("Object: " + o.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}