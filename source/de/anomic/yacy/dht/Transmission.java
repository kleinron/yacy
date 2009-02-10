// Transmission.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.02.2009 on http://yacy.net
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

package de.anomic.yacy.dht;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.index.indexContainer;
import de.anomic.index.indexContainerCache;
import de.anomic.index.indexRI;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexRepositoryReference;
import de.anomic.index.indexURLReference;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.util.Log;
import de.anomic.server.serverProcessorJob;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public class Transmission  {

    private Log log;
    private indexRepositoryReference repository;
    private yacySeedDB seeds;
    private indexRI backend;
    private boolean gzipBody4Transfer;
    private int timeout4Transfer;
    
    public Transmission(
            Log log,
            indexRepositoryReference repository, 
            yacySeedDB seeds,
            indexRI backend,
            boolean gzipBody4Transfer,
            int timeout4Transfer) {
        this.log = log;
        this.repository = repository;
        this.seeds = seeds;
        this.backend = backend;
        this.gzipBody4Transfer = gzipBody4Transfer;
        this.timeout4Transfer = timeout4Transfer;
    }

    public Chunk newChunk(
                String primaryTarget,
                final ArrayList<yacySeed> targets,
                final Row payloadrow) {
        return new Chunk(primaryTarget, targets, payloadrow);
    }
    
    public class Chunk extends serverProcessorJob implements Iterable<indexContainer> {
        /**
         * a dispatcher entry contains
         * - the primary target, which is a word hash, as marker for the entry
         * - a set of indexContainers in a cache,
         * - the associated URLs in a set to have a cache for the transmission
         *   to multiple peers and to ensure that all entries in the indexContainers
         *   have a reference in the urls
         * - a set of yacy seeds which will shrink as the containers are transmitted to them
         * - a counter that gives the number of sucessful and unsuccessful transmissions so far
         */
        private String                             primaryTarget;
        private indexContainerCache                containers;
        private HashMap<String, indexURLReference> references;
        private HashSet<String>                    badReferences;
        private ArrayList<yacySeed>                targets;
        private int                                hit, miss;
        
        /**
         * generate a new dispatcher target. such a target is defined with a primary target and 
         * a set of target peers that shall receive the entries of the containers
         * the payloadrow defines the structure of container entries
         * @param primaryTarget
         * @param targets
         * @param payloadrow
         */
        public Chunk(
                String primaryTarget,
                final ArrayList<yacySeed> targets,
                final Row payloadrow) {
            super();
            this.primaryTarget = primaryTarget;
            this.containers = new indexContainerCache(payloadrow);
            this.containers.initWriteMode();
            this.references = new HashMap<String, indexURLReference>();
            this.badReferences = new HashSet<String>();
            this.targets    = targets;
            this.hit = 0;
            this.miss = 0;
        }
    
        /**
         * add a container to the Entry cache.
         * all entries in the container are checked and only such are stored which have a reference entry
         * @param container
         */
        public void add(indexContainer container) {
            // iterate through the entries in the container and check if the reference is in the repository
            Iterator<indexRWIRowEntry>  i = container.entries();
            ArrayList<String> notFound = new ArrayList<String>();
            while (i.hasNext()) {
                indexRWIRowEntry e = i.next();
                if (references.containsKey(e.urlHash()) || badReferences.contains(e.urlHash())) continue;
                indexURLReference r = repository.load(e.urlHash(), null, 0);
                if (r == null) {
                    notFound.add(e.urlHash());
                    badReferences.add(e.urlHash());
                } else {
                    references.put(e.urlHash(), r);
                }
            }
            // now delete all references that were not found
            for (String s : notFound) container.remove(s);
            // finally add the remaining container to the cache
            containers.addEntries(container);
        }
        
        /**
         * get all containers from the entry. This method may be used to flush remaining entries
         * if they had been finished transmission without success (not enough peers arrived)
         */
        public Iterator<indexContainer> iterator() {
            return this.containers.iterator();
        }
        
        public int containersSize() {
            return this.containers.size();
        }
        
        public String primaryTarget() {
            return this.primaryTarget;
        }
        
        /**
         * return the number of successful transmissions
         * @return
         */
        public int hit() {
            return this.hit;
        }
        
        /**
         * return the number of unsuccessful transmissions
         * @return
         */
        public int miss() {
            return this.miss;
        }
        
        /**
         * return the number of targets that are left in the target cache
         * if this is empty, there may be no more use of this object and it should be flushed
         * with the iterator method
         * @return
         */
        public int targets() {
            return this.targets.size();
        }
        
        public boolean transmit() {
            if (this.targets.size() == 0) return false;
            yacySeed target = this.targets.remove(0);
            // transferring selected words to remote peer
            if (target == seeds.mySeed() || target.hash.equals(seeds.mySeed().hash)) {
            	// target is my own peer. This is easy. Just restore the indexContainer
            	restore();
            	this.hit++;
            	log.logInfo("Transfer of chunk to myself-target");
            	return true;
            }
            log.logInfo("starting new index transmission thread " + this.primaryTarget);
            long start = System.currentTimeMillis();
            final HashMap<String, Object> ohm = yacyClient.transferIndex(target, this.containers, this.references, gzipBody4Transfer, timeout4Transfer);
            final String result = (String) ohm.get("result");
            if (result == null) {
                // words successfully transfered
                long transferTime = System.currentTimeMillis() - start;                
                int payloadSize = ((Integer) ohm.get("payloadSize")).intValue();
                Iterator<indexContainer> i = this.containers.iterator();
                indexContainer firstContainer = (i == null) ? null : i.next();
                log.logInfo("Index transfer of " + this.containers.size() + 
                                 " words [" + ((firstContainer == null) ? null : firstContainer.getWordHash()) + " .. " + this.primaryTarget + "]" + 
                                 " and " + this.references.size() + " URLs" +
                                 " to peer " + target.getName() + ":" + target.hash + 
                                 " in " + (transferTime / 1000) + 
                                 " seconds successful ("  + (1000 * this.containers.size() / (transferTime + 1)) + 
                                 " words/s, " + payloadSize + " Bytes)");
                
                // if the peer has set a pause time and we are in flush mode (index transfer)
                // then we pause for a while now
                log.logInfo("Transfer finished of chunk to target " + target.hash + "/" + target.getName());
                this.hit++;
                return true;
            }
            this.miss++;
            seeds.peerActions.peerDeparture(target, "did not accept index");
            log.logInfo("Transfer failed of chunk to target " + target.hash + "/" + target.getName());
            return false;
        }
        
        public boolean isFinished() {
        	System.out.println("canFinish: hit = " + this.hit + ", redundancy = " + seeds.redundancy() + ", targets.size() = " + targets.size());
            return this.hit >= seeds.redundancy();
        }
        
        public boolean canFinish() {
        	System.out.println("canFinish: hit = " + this.hit + ", redundancy = " + seeds.redundancy() + ", targets.size() = " + targets.size());
            return this.targets.size() >= seeds.redundancy() - this.hit;
        }

        public void restore() {
            for (indexContainer ic : this) try {
                backend.addEntries(ic);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}