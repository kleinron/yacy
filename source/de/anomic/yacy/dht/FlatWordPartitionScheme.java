// FlatWordPartitionScheme.java 
// ------------------------------
// part of YaCy
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 28.01.2009
//
// $LastChangedDate: 2009-01-23 16:32:27 +0100 (Fr, 23 Jan 2009) $
// $LastChangedRevision: 5514 $
// $LastChangedBy: orbiter $
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

package de.anomic.yacy.dht;

import de.anomic.kelondro.order.Base64Order;
import de.anomic.yacy.yacySeed;

public class FlatWordPartitionScheme implements PartitionScheme {

    public static final FlatWordPartitionScheme std = new FlatWordPartitionScheme();
    
    public FlatWordPartitionScheme() {
        // nothing to initialize
    }

    public int verticalPartitions() {
        return 1;
    }
    
    public long dhtPosition(String wordHash, String urlHash) {
        // the urlHash has no relevance here
        // normalized to Long.MAX_VALUE
        long c = Base64Order.enhancedCoder.cardinal(wordHash.getBytes());
        assert c != Long.MAX_VALUE;
        if (c == Long.MAX_VALUE) return Long.MAX_VALUE - 1;
        return c;
    }

    public final long dhtDistance(final String word, final String urlHash, final yacySeed peer) {
        return dhtDistance(word, urlHash, peer.hash);
    }
    
    private final long dhtDistance(final String from, final String urlHash, final String to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = dhtPosition(to, null);
        final long fromPos = dhtPosition(from, urlHash);
        return dhtDistance(fromPos, toPos);
    }

    public long dhtPosition(String wordHash, int verticalPosition) {
        return dhtPosition(wordHash, null);
    }

    public long[] dhtPositions(String wordHash) {
        long[] l = new long[1];
        l[1] = dhtPosition(wordHash, null);
        return l;
    }

    public int verticalPosition(String urlHash) {
        return 0; // this is not a method stub, this is actually true for all FlatWordPartitionScheme
    }

    public final static long dhtDistance(final long fromPos, final long toPos) {
        final long d = toPos - fromPos;
        return (d >= 0) ? d : (d + Long.MAX_VALUE) + 1;
    }
    
    public static String positionToHash(final long l) {
        // transform the position of a peer position into a close peer hash
        return new String(Base64Order.enhancedCoder.uncardinal(l)) + "AA";
    }

}