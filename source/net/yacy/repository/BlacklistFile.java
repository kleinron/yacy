// BlacklistFile.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-29 23:28:49 +0200 (Di, 29 Sep 2009) $
// $LastChangedRevision: 6359 $
// $LastChangedBy: low012 $
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

package net.yacy.repository;

import java.util.Arrays;
import java.util.HashSet;

public class BlacklistFile {
    
    private final String filename;
    private final String type;
    
    public BlacklistFile(final String filename, final String type) {
        this.filename = filename;
        this.type = type;
    }
    
    public String getFileName() { return this.filename; }
    
    
    /**
     * Construct a unified array of file names from comma seperated file name
     * list.
     * 
     * @return unified String array of file names
     */
    public String[] getFileNamesUnified() {
        final HashSet<String> hs = new HashSet<String>(Arrays.asList(this.filename.split(",")));
        
        return hs.toArray(new String[hs.size()]);
    }
    
    public String getType() { return this.type; }
}