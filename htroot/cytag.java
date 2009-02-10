// cytag.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 06.02.2009 on http://www.yacy.net
//
// This is a part of YaCy.
// The Software shall be used for Good, not Evil.
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

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.Date;

import de.anomic.htmlFilter.htmlFilterCharacterCoding;
import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.order.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageImageParser;

public class cytag {
    
    public static Image respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        
        final plasmaSwitchboard sb = (plasmaSwitchboard)env;

        // harvest request information
        StringBuilder connect = new StringBuilder();
        connect.append('{');
        addJSON(connect, "time", DateFormatter.formatShortMilliSecond(new Date()));
        addJSON(connect, "trail", header.get("Referer", ""));
        addJSON(connect, "nick",  (post == null) ? "" : post.get("nick", ""));
        addJSON(connect, "tag",   (post == null) ? "" : post.get("tag", ""));
        addJSON(connect, "icon",  (post == null) ? "" : post.get("icon", ""));
        addJSON(connect, "ip",    header.get("CLIENTIP", ""));
        addJSON(connect, "agent", header.get("User-Agent", ""));
        connect.append('}');
        
        sb.trail.add(connect.toString());
        //Log.logInfo("CYTAG", "catched trail - " + connect.toString());
        
        String defaultimage = "redpillmini.png";
        if (post != null && post.get("icon", "").equals("invisible")) defaultimage = "invisible.png";
        File iconfile = new File(sb.getRootPath(), "/htroot/env/grafics/" + defaultimage);
        
        byte[] imgb = null;
        try {
            imgb = FileUtils.read(iconfile);
        } catch (final IOException e) {
             return null;
        }
        if (imgb == null) return null;
        
        // read image
        final Image image = ymageImageParser.parse("cytag.png", imgb);

        return image;
    }
    
    private static final void addJSON(StringBuilder sb, String k, String v) {
        if (sb.length() > 2) sb.append(',');
        sb.append('\"');
        sb.append(k);
        sb.append("\":\"");
        sb.append(htmlFilterCharacterCoding.unicode2xml(v, true));
        sb.append('\"');
    }
}