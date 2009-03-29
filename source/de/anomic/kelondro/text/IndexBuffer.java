// IndexCache.java
// (C) 2005, 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2005 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.ScoreCluster;
import de.anomic.kelondro.util.Log;

/**
 * A IndexCache is a ReferenceContainerCache with an attached cache flush logic
 *
 */
public final class IndexBuffer extends AbstractIndex implements Index, IndexReader, Iterable<ReferenceContainer> {

    // class variables
    private final ScoreCluster<String> hashScore;
    private final ScoreCluster<String> hashDate;
    private long  initTime;
    private int   cacheEntityMaxCount;       // the maximum number of cache slots for RWI entries
    public  int   cacheReferenceCountLimit;  // the maximum number of references to a single RWI entity
    public  long  cacheReferenceAgeLimit;    // the maximum age (= time not changed) of a RWI entity
    private final Log log;
    private final File dumpFile;
    private ReferenceContainerCache heap;
    
    @SuppressWarnings("unchecked")
    public IndexBuffer(
            final File databaseRoot,
            final ByteOrder wordOrdering,
            final Row payloadrow,
            final int entityCacheMaxSize,
            final int wCacheReferenceCountLimitInit,
            final long wCacheReferenceAgeLimitInit,
            final String newHeapName,
            final Log log) {

        // creates a new index cache
        // the cache has a back-end where indexes that do not fit in the cache are flushed
        this.hashScore = new ScoreCluster<String>();
        this.hashDate  = new ScoreCluster<String>();
        this.initTime = System.currentTimeMillis();
        this.cacheEntityMaxCount = entityCacheMaxSize;
        this.cacheReferenceCountLimit = wCacheReferenceCountLimitInit;
        this.cacheReferenceAgeLimit = wCacheReferenceAgeLimitInit;
        this.log = log;
        this.dumpFile = new File(databaseRoot, newHeapName);
        this.heap = new ReferenceContainerCache(payloadrow, wordOrdering);
        
        // read in dump of last session
        boolean initFailed = false;
        if (dumpFile.exists()) try {
            heap.initWriteModeFromBLOB(dumpFile);
        } catch (IOException e) {
            initFailed = true;
            e.printStackTrace();
        }
        if (initFailed) {
            log.logSevere("unable to restore cache dump");
            // get empty dump
            heap.initWriteMode();
        } else if (dumpFile.exists()) {
            // initialize scores for cache organization
            for (final ReferenceContainer ic : (Iterable<ReferenceContainer>) heap.references(null, false)) {
                this.hashDate.setScore(ic.getWordHash(), intTime(ic.lastWrote()));
                this.hashScore.setScore(ic.getWordHash(), ic.size());
            }
        } else {
            heap.initWriteMode();
        }
    }
    
    /**
     * clear the content
     * @throws IOException 
     */
    public void clear() {
        hashScore.clear();
        hashDate.clear();
        initTime = System.currentTimeMillis();
        heap.clear();
    }

    public int minMem() {
        // there is no specific large array that needs to be maintained
        // this value is just a guess of the possible overhead
        return 100 * 1024; // 100 kb
    }
    
    // cache settings
    public int getBufferMaxReferences() {
        if (hashScore.size() == 0) return 0;
        return hashScore.getMaxScore();
    }

    public long getBufferMinAge() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMaxScore());
    }

    public long getBufferMaxAge() {
        if (hashDate.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(hashDate.getMinScore());
    }

    public void setMaxWordCount(final int maxWords) {
        this.cacheEntityMaxCount = maxWords;
    }
    
    public int getMaxWordCount() {
        return this.cacheEntityMaxCount;
    }
    
    public int size() {
    	if (heap == null) return 0;
        return heap.size();
    }

    public synchronized CloneableIterator<ReferenceContainer> references(final String startWordHash, final boolean rot) {
        // we return an iterator object that creates top-level-clones of the indexContainers
        // in the cache, so that manipulations of the iterated objects do not change
        // objects in the cache.
        return heap.references(startWordHash, rot);
    }

    public synchronized String maxScoreWordHash() {
        if (heap == null || heap.size() == 0) return null;
        try {
            return hashScore.getMaxObject();
        } catch (final Exception e) {
            log.logSevere("flushFromMem: " + e.getMessage(), e);
        }
        return null;
    }
    
    public String bestFlushWordHash() {
        // select appropriate hash
        // we have 2 different methods to find a good hash:
        // - the oldest entry in the cache
        // - the entry with maximum count
        if (heap == null || heap.size() == 0) return null;
        try {
            //return hashScore.getMaxObject();
            String hash = null;
            final int count = hashScore.getMaxScore();
            if ((count >= cacheReferenceCountLimit) &&
                ((hash = hashScore.getMaxObject()) != null)) {
                // we MUST flush high-score entries, because a loop deletes entries in cache until this condition fails
                // in this cache we MUST NOT check wCacheMinAge
                return hash;
            }
            final long oldestTime = longEmit(hashDate.getMinScore());
            if (((System.currentTimeMillis() - oldestTime) > cacheReferenceAgeLimit) &&
                ((hash = hashDate.getMinObject()) != null)) {
                // flush out-dated entries
                return hash;
            }
            // cases with respect to memory situation
            if (MemoryControl.free() < 100000) {
                // urgent low-memory case
                hash = hashScore.getMaxObject(); // flush high-score entries (saves RAM)
            } else {
                // not-efficient-so-far case. cleans up unnecessary cache slots
                hash = hashDate.getMinObject(); // flush oldest entries
            }
            if (hash == null) {
                final ReferenceContainer ic = heap.references(null, false).next();
                if (ic != null) hash = ic.getWordHash();
            }
            return hash;
            
        } catch (final Exception e) {
            log.logSevere("flushFromMem: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized ArrayList<ReferenceContainer> bestFlushContainers(final int count) {
        final ArrayList<ReferenceContainer> containerList = new ArrayList<ReferenceContainer>();
        String hash;
        ReferenceContainer container;
        for (int i = 0; i < count; i++) {
            hash = bestFlushWordHash();
            if (hash == null) return containerList;
            container = heap.delete(hash);
            assert (container != null);
            if (container == null) return containerList;
            hashScore.deleteScore(hash);
            hashDate.deleteScore(hash);
            containerList.add(container);
        }
        return containerList;
    }
    
    private int intTime(final long longTime) {
        return (int) Math.max(0, ((longTime - initTime) / 1000));
    }

    private long longEmit(final int intTime) {
        return (((long) intTime) * (long) 1000) + initTime;
    }
    
    public boolean has(final String wordHash) {
        return heap.has(wordHash);
    }
    
    public int count(String key) {
        return this.heap.count(key);
    }
    
    public synchronized ReferenceContainer get(final String wordHash, final Set<String> urlselection) {
        if (wordHash == null) return null;
        
        // retrieve container
        ReferenceContainer container = heap.get(wordHash, null);
        
        // We must not use the container from cache to store everything we find,
        // as that container remains linked to in the cache and might be changed later
        // while the returned container is still in use.
        // create a clone from the container
        if (container != null) container = container.topLevelClone();
        
        // select the urlselection
        if ((urlselection != null) && (container != null)) container.select(urlselection);

        return container;
    }

    public synchronized ReferenceContainer delete(final String wordHash) {
        // returns the index that had been deleted
    	if (wordHash == null || heap == null) return null;
        final ReferenceContainer container = heap.delete(wordHash);
        hashScore.deleteScore(wordHash);
        hashDate.deleteScore(wordHash);
        return container;
    }

    public synchronized boolean remove(final String wordHash, final String urlHash) {
        final boolean removed = heap.remove(wordHash, urlHash);
        if (removed) {
            if (heap.has(wordHash)) {
                hashScore.decScore(wordHash);
                hashDate.setScore(wordHash, intTime(System.currentTimeMillis()));
            } else {
                hashScore.deleteScore(wordHash);
                hashDate.deleteScore(wordHash);
            }
            return true;
        }
        return false;
    }
    
    public synchronized int remove(final String wordHash, final Set<String> urlHashes) {
        if (urlHashes.size() == 0) return 0;
        final int c = heap.remove(wordHash, urlHashes);
        if (c > 0) {
            // removal successful
            if (heap.has(wordHash)) {
                hashScore.addScore(wordHash, -c);
                hashDate.setScore(wordHash, intTime(System.currentTimeMillis()));
            } else {
                hashScore.deleteScore(wordHash);
                hashDate.deleteScore(wordHash);
            }
            return c;
        }
        return 0;
    }
    
    public synchronized void add(final ReferenceContainer container) {
        if (container == null || container.size() == 0 || heap == null) return;

        // put new words into cache
        heap.add(container);
        hashScore.setScore(container.getWordHash(), heap.count(container.getWordHash()));
        hashDate.setScore(container.getWordHash(), intTime(System.currentTimeMillis()));
    }

    public void add(final String wordHash, final ReferenceRow entry) throws IOException {
        if (entry == null || heap == null) return;

        // put new words into cache
        heap.add(wordHash, entry);
        hashScore.incScore(wordHash);
        hashDate.setScore(wordHash, intTime(System.currentTimeMillis()));
    }

    public synchronized void close() {
        // dump cache
        try {
            //heap.dumpold(this.oldDumpFile);
            heap.dump(this.dumpFile, true);
        } catch (final IOException e){
            log.logSevere("unable to dump cache: " + e.getMessage(), e);
        }
        heap = null;
        hashScore.clear();
        hashDate.clear();
    }

    public Iterator<ReferenceContainer> iterator() {
        return references(null, false);
    }
    
    public ByteOrder ordering() {
        return heap.ordering();
    }

    public synchronized long getBufferSizeBytes() {
        // calculate the real size in bytes of the index cache
        long cacheBytes = 0;
        final long entryBytes = ReferenceRow.urlEntryRow.objectsize;
        final Iterator<ReferenceContainer> it = references(null, false);
        while (it.hasNext()) cacheBytes += it.next().size() * entryBytes;
        return cacheBytes;
    }

}