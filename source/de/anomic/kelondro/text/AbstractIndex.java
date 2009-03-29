// AbstractIndex.java
// -----------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.3.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.kelondro.text;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.kelondro.order.Order;

public abstract class AbstractIndex implements Index {
    
    public int remove(final Set<String> wordHashes, final String urlHash) throws IOException {
        // remove the same url hashes for multiple words
        // this is mainly used when correcting a index after a search
        final Iterator<String> i = wordHashes.iterator();
        int c = 0;
        while (i.hasNext()) {
            if (remove(i.next(), urlHash)) c++;
        }
        return c;
    }
    
    public void remove(final Set<String> wordHashes, final Set<String> urlHashes) throws IOException {
        // remove the same url hashes for multiple words
        // this is mainly used when correcting a index after a search
        final Iterator<String> i = wordHashes.iterator();
        while (i.hasNext()) {
            remove(i.next(), urlHashes);
        }
    }
    
    public synchronized TreeSet<ReferenceContainer> references(final String startHash, final boolean rot, int count) throws IOException {
        // creates a set of indexContainers
        // this does not use the cache
        final Order<ReferenceContainer> containerOrder = new ReferenceContainerOrder(this.ordering().clone());
        containerOrder.rotate(ReferenceContainer.emptyContainer(startHash, 0));
        final TreeSet<ReferenceContainer> containers = new TreeSet<ReferenceContainer>(containerOrder);
        final Iterator<ReferenceContainer> i = references(startHash, rot);
        //if (ram) count = Math.min(size(), count);
        ReferenceContainer container;
        // this loop does not terminate using the i.hasNex() predicate when rot == true
        // because then the underlying iterator is a rotating iterator without termination
        // in this case a termination must be ensured with a counter
        // It must also be ensured that the counter is in/decreased every loop
        while ((count > 0) && (i.hasNext())) {
            container = i.next();
            if ((container != null) && (container.size() > 0)) {
                containers.add(container);
            }
            count--; // decrease counter even if the container was null or empty to ensure termination
        }
        return containers; // this may return less containers as demanded
    }
}