// ReferenceContainerCache.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.ranking.Rating;
import net.yacy.kelondro.blob.HeapWriter;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.util.ByteArray;
import net.yacy.kelondro.util.FileUtils;

/**
 * A ReferenceContainerCache is the ram cache for word indexes or other entity type indexes
 * The <ReferenceType> defines the index reference specification and attributes that can be
 * accessed during a search without using the metadata reference that shall be contained within
 * the <ReferenceType>. A ReferenceContainerCache has no active backup in a file, it must be flushed to
 * a file to save the content of the cache.
 *
 * @param <ReferenceType>
 */
public final class ReferenceContainerCache<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements Index<ReferenceType>, IndexReader<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>> {

    private final int termSize;
    private final ByteOrder termOrder;
    private final ContainerOrder<ReferenceType> containerOrder;
    private ConcurrentHashMap<ByteArray, ReferenceContainer<ReferenceType>> cache;

    /**
     * open an existing heap file in undefined mode
     * after this a initialization should be made to use the heap:
     * either a read-only or read/write mode initialization
     * @param factory the factory for payload reference objects
     * @param termOrder the order on search terms for the cache
     * @param termSize the fixed size of search terms
     */
    public ReferenceContainerCache(final ReferenceFactory<ReferenceType> factory, final ByteOrder termOrder, final int termSize) {
        super(factory);
        assert termOrder != null;
        this.termOrder = termOrder;
        this.termSize = termSize;
        this.containerOrder = new ContainerOrder<ReferenceType>(this.termOrder);
        this.cache = new ConcurrentHashMap<ByteArray, ReferenceContainer<ReferenceType>>();
    }

    public Row rowdef() {
        return this.factory.getRow();
    }


    /**
     * every index entry is made for a term which has a fixed size
     * @return the size of the term
     */
    public int termKeyLength() {
        return this.termSize;
    }

    public void clear() {
        if (this.cache != null) this.cache.clear();
    }

    public void close() {
    	this.cache = null;
    }

    /**
     * dump the cache to a file. This method can be used in a destructive way
     * which means that memory can be freed during the dump. This may be important
     * because the dump is done in such situations when memory gets low. To get more
     * memory during the dump helps to solve tight memory situations.
     * @param heapFile
     * @param writeBuffer
     * @param destructive - if true then the cache is cleaned during the dump causing to free memory
     */
    public void dump(final File heapFile, final int writeBuffer, final boolean destructive) {
        assert this.cache != null;
        if (this.cache == null) return;
        Log.logInfo("indexContainerRAMHeap", "creating rwi heap dump '" + heapFile.getName() + "', " + this.cache.size() + " rwi's");
        if (heapFile.exists()) FileUtils.deletedelete(heapFile);
        final File tmpFile = new File(heapFile.getParentFile(), heapFile.getName() + ".prt");
        HeapWriter dump;
        try {
            dump = new HeapWriter(tmpFile, heapFile, this.termSize, this.termOrder, writeBuffer);
        } catch (final IOException e1) {
            Log.logException(e1);
            return;
        }
        final long startTime = System.currentTimeMillis();

        // sort the map
        final List<ReferenceContainer<ReferenceType>> cachecopy = sortedClone();

        // write wCache
        long wordcount = 0, urlcount = 0;
        byte[] term = null, lwh;
        assert this.termKeyOrdering() != null;
        for (final ReferenceContainer<ReferenceType> container: cachecopy) {
            // get entries
            lwh = term;
            term = container.getTermHash();
            if (term == null) continue;

            // check consistency: entries must be ordered
            assert (lwh == null || this.termKeyOrdering().compare(term, lwh) > 0);

            // put entries on heap
            if (container != null && term.length == this.termSize) {
                //System.out.println("Dump: " + wordHash);
                try {
                    dump.add(term, container.exportCollection());
                } catch (final IOException e) {
                    Log.logException(e);
                } catch (final RowSpaceExceededException e) {
                    Log.logException(e);
                }
                if (destructive) container.clear(); // this memory is not needed any more
                urlcount += container.size();
            }
            wordcount++;
        }
        try {
            dump.close(true);
            Log.logInfo("indexContainerRAMHeap", "finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + (System.currentTimeMillis() - startTime) + " milliseconds");
        } catch (final IOException e) {
            Log.logSevere("indexContainerRAMHeap", "failed rwi heap dump: " + e.getMessage(), e);
        } finally {
            dump = null;
        }
    }

    /**
     * create a clone of the cache content that is sorted using the this.containerOrder
     * @return the sorted ReferenceContainer[]
     */
    protected List<ReferenceContainer<ReferenceType>> sortedClone() {
        final List<ReferenceContainer<ReferenceType>> cachecopy = new ArrayList<ReferenceContainer<ReferenceType>>(this.cache.size());
        synchronized (this.cache) {
            for (final Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> entry: this.cache.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getTermHash() != null) cachecopy.add(entry.getValue());
            }
        }
        Collections.sort(cachecopy, this.containerOrder);
        return cachecopy;
    }

    protected List<Rating<ByteArray>> ratingList() {
        final List<Rating<ByteArray>> list = new ArrayList<Rating<ByteArray>>(this.cache.size());
        synchronized (this.cache) {
            for (final Map.Entry<ByteArray, ReferenceContainer<ReferenceType>> entry: this.cache.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getTermHash() != null) list.add(new Rating<ByteArray>(entry.getKey(), entry.getValue().size()));
            }
        }
        return list;
    }

    public int size() {
        return (this.cache == null) ? 0 : this.cache.size();
    }

    public boolean isEmpty() {
        if (this.cache == null) return true;
        return this.cache.isEmpty();
    }

    public int maxReferences() {
        // iterate to find the max score
        int max = 0;
        for (final ReferenceContainer<ReferenceType> container : this.cache.values()) {
            if (container.size() > max) max = container.size();
        }
        return max;
    }

    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return referenceContainerIterator(null, false);
    }

    /**
     * return an iterator object that creates top-level-clones of the indexContainers
     * in the cache, so that manipulations of the iterated objects do not change
     * objects in the cache.
     */
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> referenceContainerIterator(final byte[] startWordHash, final boolean rot) {
        return new ReferenceContainerIterator(startWordHash, rot);
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class ReferenceContainerIterator implements CloneableIterator<ReferenceContainer<ReferenceType>>, Iterable<ReferenceContainer<ReferenceType>> {

        // this class exists, because the wCache cannot be iterated with rotation
        // and because every indexContainer Object that is iterated must be returned as top-level-clone
        // so this class simulates wCache.tailMap(startWordHash).values().iterator()
        // plus the mentioned features

        private final boolean rot;
        private final List<ReferenceContainer<ReferenceType>> cachecopy;
        private int p;
        private byte[] latestTermHash;

        public ReferenceContainerIterator(byte[] startWordHash, final boolean rot) {
            this.rot = rot;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            this.cachecopy = sortedClone();
            assert this.cachecopy != null;
            assert ReferenceContainerCache.this.termOrder != null;
            this.p = 0;
            if (startWordHash != null) {
                while ( this.p < this.cachecopy.size() &&
                        ReferenceContainerCache.this.termOrder.compare(this.cachecopy.get(this.p).getTermHash(), startWordHash) < 0
                      ) this.p++;
            }
            this.latestTermHash = null;
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }

        public ReferenceContainerIterator clone(final Object secondWordHash) {
            return new ReferenceContainerIterator((byte[]) secondWordHash, this.rot);
        }

        public boolean hasNext() {
            if (this.rot) return this.cachecopy.size() > 0;
            return this.p < this.cachecopy.size();
        }

        public ReferenceContainer<ReferenceType> next() {
            if (this.p < this.cachecopy.size()) {
                final ReferenceContainer<ReferenceType> c = this.cachecopy.get(this.p++);
                this.latestTermHash = c.getTermHash();
                try {
                    return c.topLevelClone();
                } catch (final RowSpaceExceededException e) {
                    Log.logException(e);
                    return null;
                }
            }
            // rotation iteration
            if (!this.rot) {
                return null;
            }
            if (this.cachecopy.isEmpty()) return null;
            this.p = 0;
            final ReferenceContainer<ReferenceType> c = this.cachecopy.get(this.p++);
            this.latestTermHash = c.getTermHash();
            try {
                return c.topLevelClone();
            } catch (final RowSpaceExceededException e) {
                Log.logException(e);
                return null;
            }
        }

        public void remove() {
            System.arraycopy(this.cachecopy, this.p, this.cachecopy, this.p - 1, this.cachecopy.size() - this.p);
            ReferenceContainerCache.this.cache.remove(new ByteArray(this.latestTermHash));
        }

        public Iterator<ReferenceContainer<ReferenceType>> iterator() {
            return this;
        }

    }

    @Override
    public CloneableIterator<Rating<byte[]>> referenceCountIterator(final byte[] startHash, final boolean rot) {
        return new ReferenceCountIterator(startHash, rot);
    }

    /**
     * cache iterator: iterates objects within the heap cache. This can only be used
     * for write-enabled heaps, read-only heaps do not have a heap cache
     */
    public class ReferenceCountIterator implements CloneableIterator<Rating<byte[]>>, Iterable<Rating<byte[]>> {

        private final boolean rot;
        private final List<Rating<ByteArray>> cachecounts;
        private int p;
        private byte[] latestTermHash;

        public ReferenceCountIterator(byte[] startWordHash, final boolean rot) {
            this.rot = rot;
            if (startWordHash != null && startWordHash.length == 0) startWordHash = null;
            this.cachecounts = ratingList();
            assert this.cachecounts != null;
            assert ReferenceContainerCache.this.termOrder != null;
            this.p = 0;
            if (startWordHash != null) {
                while ( this.p < this.cachecounts.size() &&
                        ReferenceContainerCache.this.termOrder.compare(this.cachecounts.get(this.p).getObject().asBytes(), startWordHash) < 0
                      ) this.p++;
            }
            this.latestTermHash = null;
            // The collection's iterator will return the values in the order that their corresponding keys appear in the tree.
        }

        public ReferenceCountIterator clone(final Object secondWordHash) {
            return new ReferenceCountIterator((byte[]) secondWordHash, this.rot);
        }

        public boolean hasNext() {
            if (this.rot) return this.cachecounts.size() > 0;
            return this.p < this.cachecounts.size();
        }

        public Rating<byte[]> next() {
            if (this.p < this.cachecounts.size()) {
                final Rating<ByteArray> c = this.cachecounts.get(this.p++);
                this.latestTermHash = c.getObject().asBytes();
                return new Rating<byte[]>(c.getObject().asBytes(), c.getScore());
            }
            // rotation iteration
            if (!this.rot) {
                return null;
            }
            if (this.cachecounts.isEmpty()) return null;
            this.p = 0;
            final Rating<ByteArray> c = this.cachecounts.get(this.p++);
            this.latestTermHash = c.getObject().asBytes();
            return new Rating<byte[]>(c.getObject().asBytes(), c.getScore());
        }

        public void remove() {
            System.arraycopy(this.cachecounts, this.p, this.cachecounts, this.p - 1, this.cachecounts.size() - this.p);
            ReferenceContainerCache.this.cache.remove(new ByteArray(this.latestTermHash));
        }

        public Iterator<Rating<byte[]>> iterator() {
            return this;
        }

    }

    /**
     * test if a given key is in the heap
     * this works with heaps in write- and read-mode
     * @param key
     * @return true, if the key is used in the heap; false otherwise
     */
    public boolean has(final byte[] key) {
        return this.cache.containsKey(new ByteArray(key));
    }

    /**
     * get a indexContainer from a heap
     * @param key
     * @return the indexContainer if one exist, null otherwise
     * @throws
     */
    public ReferenceContainer<ReferenceType> get(final byte[] key, final HandleSet urlselection) {
        final ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
        if (urlselection == null) return c;
        if (c == null) return null;
        // because this is all in RAM, we must clone the entries (flat)
        try {
            final ReferenceContainer<ReferenceType> c1 = new ReferenceContainer<ReferenceType>(this.factory, c.getTermHash(), c.size());
            final Iterator<ReferenceType> e = c.entries();
            ReferenceType ee;
            while (e.hasNext()) {
                ee = e.next();
                if (urlselection.has(ee.urlhash())) {
                    c1.add(ee);
                }
            }
            return c1;
        } catch (final RowSpaceExceededException e2) {
            Log.logException(e2);
        }
        return null;
    }

    /**
     * return the size of the container with corresponding key
     * @param key
     * @return
     */
    public int count(final byte[] key) {
        final ReferenceContainer<ReferenceType> c = this.cache.get(new ByteArray(key));
        if (c == null) return 0;
        return c.size();
    }

    /**
     * delete a indexContainer from the heap cache. This can only be used for write-enabled heaps
     * @param wordHash
     * @return the indexContainer if the cache contained the container, null otherwise
     */
    public ReferenceContainer<ReferenceType> delete(final byte[] termHash) {
        // returns the index that had been deleted
        assert this.cache != null;
        if (this.cache == null) return null;
        return this.cache.remove(new ByteArray(termHash));
    }

    public void removeDelayed(final byte[] termHash, final byte[] urlHashBytes) {
        remove(termHash, urlHashBytes);
    }
    public boolean remove(final byte[] termHash, final byte[] urlHashBytes) {
        assert this.cache != null;
        if (this.cache == null) return false;
        final ByteArray tha = new ByteArray(termHash);
        synchronized (this.cache) {
	        final ReferenceContainer<ReferenceType> c = this.cache.get(tha);
	        if (c != null && c.delete(urlHashBytes)) {
	            // removal successful
	            if (c.isEmpty()) {
	                delete(termHash);
	            } else {
	                this.cache.put(tha, c);
	            }
	            return true;
	        }
        }
        return false;
    }

    public void removeDelayed(final byte[] termHash, final HandleSet urlHashes) {
        remove(termHash, urlHashes);
    }

    public int remove(final byte[] termHash, final HandleSet urlHashes) {
        assert this.cache != null;
        if (this.cache == null) return  0;
        if (urlHashes.isEmpty()) return 0;
        final ByteArray tha = new ByteArray(termHash);
        int count;
        synchronized (this.cache) {
            final ReferenceContainer<ReferenceType> c = this.cache.get(tha);
            if ((c != null) && ((count = c.removeEntries(urlHashes)) > 0)) {
                // removal successful
                if (c.isEmpty()) {
                    delete(termHash);
                } else {
                    this.cache.put(tha, c);
                }
                return count;
            }
        }
        return 0;
    }

    public void removeDelayed() {}

    public void add(final ReferenceContainer<ReferenceType> container) throws RowSpaceExceededException {
        // this puts the entries into the cache
        if (this.cache == null || container == null || container.isEmpty()) return;

        // put new words into cache
        final ByteArray tha = new ByteArray(container.getTermHash());
        int added = 0;
        synchronized (this.cache) {
            ReferenceContainer<ReferenceType> entries = this.cache.get(tha); // null pointer exception? wordhash != null! must be cache==null
            if (entries == null) {
                entries = container.topLevelClone();
                added = entries.size();
            } else {
                added = entries.putAllRecent(container);
            }
            if (added > 0) {
                this.cache.put(tha, entries);
            }
            entries = null;
            return;
        }
    }

    public void add(final byte[] termHash, final ReferenceType newEntry) throws RowSpaceExceededException {
        assert this.cache != null;
        if (this.cache == null) return;
        final ByteArray tha = new ByteArray(termHash);

        // first access the cache without synchronization
        ReferenceContainer<ReferenceType> container = this.cache.remove(tha);
        if (container == null) container = new ReferenceContainer<ReferenceType>(this.factory, termHash, 1);
        container.put(newEntry);

        // synchronization: check if the entry is still empty and set new value
        final ReferenceContainer<ReferenceType> container0 = this.cache.put(tha, container);
        if (container0 != null) synchronized (this.cache) {
            // no luck here, we get a lock exclusively to sort this out
            final ReferenceContainer<ReferenceType> containerNew = this.cache.put(tha, container0);
            if (containerNew == null) return;
            if (container0 == containerNew) {
                // The containers are the same, so nothing needs to be done
                return;
            }
            // Now merge the smaller container into the lager.
            // The other way around can become very slow
            if (container0.size() >= containerNew.size()) {
                container0.putAllRecent(containerNew);
       	        this.cache.put(tha, container0);
            } else {
                containerNew.putAllRecent(container0);
                this.cache.put(tha, containerNew);
            }
        }
    }

    public int minMem() {
        return 0;
    }

    public ByteOrder termKeyOrdering() {
        return this.termOrder;
    }

    public static class ContainerOrder<ReferenceType extends Reference> implements Comparator<ReferenceContainer<ReferenceType>> {
        private final ByteOrder o;
        public ContainerOrder(final ByteOrder order) {
            this.o = order;
        }
        public int compare(final ReferenceContainer<ReferenceType> arg0, final ReferenceContainer<ReferenceType> arg1) {
            if (arg0 == arg1) return 0;
            if (arg0 == null) return -1;
            if (arg1 == null) return 1;
            return this.o.compare(arg0.getTermHash(), arg1.getTermHash());
        }
    }

}
