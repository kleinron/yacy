// PMHReader
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.09.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
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

package de.anomic.content.oai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import de.anomic.content.DCEntry;
import de.anomic.content.file.SurrogateReader;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.LoaderDispatcher;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;
import de.anomic.yacy.yacyURL;

public class PMHReader {

    LoaderDispatcher loader;
    
    public PMHReader(LoaderDispatcher loader) {
        this.loader = loader;
    }
    
    public void load(yacyURL source) throws IOException {
        Response response = this.loader.load(source, true, true, CrawlProfile.CACHE_STRATEGY_NOCACHE);
        load(response);
    }
    
    public static void load0(yacyURL source) throws IOException {
        Response response = HTTPLoader.load(new Request(source, null));
        load(response);
    }
    
    private static void load(Response response) throws IOException {
        byte[] b = response.getContent();
        SurrogateReader sr = new SurrogateReader(new ByteArrayInputStream(b), 100);
        Thread srt = new Thread(sr);
        srt.start();
        DCEntry dce;
        while ((dce = sr.take()) != DCEntry.poison) {
            System.out.println(dce.toString());
        }
        try {
            srt.join();
        } catch (InterruptedException e) {}
    }
    
    public static void main(String[] args) {
        // get one server with
        // http://roar.eprints.org/index.php?action=csv
        // list records from oai-pmh like
        // http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc
        try {
            load0(new yacyURL(args[0], null));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
/*

http://an.oa.org/OAI-script?verb=GetRecord&identifier=oai:arXiv.org:hep-th/9901001&metadataPrefix=oai_dc

special characters in URIs must be encoded, the correct form of the above GET request URL is:

http://an.oa.org/OAI-script?verb=GetRecord&identifier=oai%3AarXiv.org%3Ahep-th%2F9901001&metadataPrefix=oai_dc

"/","%2F"
"?","%3F"
"#","%23"
"=","%3D"
"&","%26"
":","%3A"
";","%3B"
" ","%20"
"%","%25"
"+","%2B"

GetRecord
http://arXiv.org/oai2?verb=GetRecord&identifier=oai:arXiv.org:cs/0112017&metadataPrefix=oai_dc
http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=GetRecord&identifier=oai:opus.bsz-bw.de-fhhv:6&metadataPrefix=oai_dc

Identify
http://memory.loc.gov/cgi-bin/oai?verb=Identify

ListIdentifiers
http://an.oa.org/OAI-script?verb=ListIdentifiers&from=1998-01-15&metadataPrefix=oldArXiv&set=physics:hep
http://an.oa.org/OAI-script?verb=ListIdentifiers&resumptionToken=xxx45abttyz
http://www.perseus.tufts.edu/cgi-bin/pdataprov?verb=ListIdentifiers&metadataPrefix=olac&from=2001-01-01&until=2001-01-01&set=Perseus:collection:PersInfo

ListMetadataFormats
http://www.perseus.tufts.edu/cgi-bin/pdataprov?verb=ListMetadataFormats&identifier=oai:perseus.tufts.edu:Perseus:text:1999.02.0119
http://memory.loc.gov/cgi-bin/oai?verb=ListMetadataFormats
http://memory.loc.gov/cgi-bin/oai?verb=ListMetadataFormats&identifier=oai:lcoa1.loc.gov:loc.rbc/rbpe.00000111
http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListMetadataFormats

ListRecords
http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc
http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&resumptionToken=455
http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&resumptionToken=890
http://an.oa.org/OAI-script?verb=ListRecords&from=1998-01-15&set=physics:hep&metadataPrefix=oai_rfc1807
http://www.perseus.tufts.edu/cgi-b:in/pdataprov?verb=ListRecords&from=2002-05-01T14:15:00Z&until=2002-05-01T14:20:00Z&metadataPrefix=oai_dc
http://memory.loc.gov/cgi-bin/oai?verb=ListRecords&from=2002-06-01T02:00:00Z&until=2002-06-01T03:00:00Z&metadataPrefix=oai_marc

ListSets
http://an.oa.org/OAI-script?verb=ListSets
http://purl.org/alcme/etdcat/servlet/OAIHandler?verb=ListSets

urn identifier koennen ueber den resolver der d-nb aufgeloest werden:
http://nbn-resolving.de/urn:nbn:de:bsz:960-opus-1860

<?xml version="1.0" encoding="UTF-8"?>
<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/
         http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd">
 <responseDate>2009-10-01T22:20:04Z</responseDate>
 <request verb="ListRecords" metadataPrefix="oai_dc">http://opus.bsz-bw.de/fhhv/oai2/oai2.php</request>
 <ListRecords>
  <record>
   <header>
    <identifier>oai:opus.bsz-bw.de-fhhv:1</identifier>
    <datestamp>2008-03-04T12:17:33Z</datestamp>
    <setSpec>ddc:020</setSpec>
    <setSpec>pub-type:2</setSpec>
    <setSpec>has-source-swb:false</setSpec>
   </header>
   <metadata>
     <oai_dc:dc
       xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
       xmlns:dc="http://purl.org/dc/elements/1.1/"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/oai_dc/
       http://www.openarchives.org/OAI/2.0/oai_dc.xsd">
      <dc:title>Teaching Information Literacy with the Lerninformationssystem</dc:title>
      <dc:creator>Hauschke, Christian</dc:creator>
      <dc:creator>Ullmann, Nadine</dc:creator>
      <dc:subject>Informationskompetenz</dc:subject>
      <dc:subject>E-Learning</dc:subject>
      <dc:subject>Bibliothek</dc:subject>
      <dc:subject>Informationsvermittlung</dc:subject>
      <dc:subject>Wissenschaftliches Arbeiten</dc:subject>
      <dc:subject>information literacy</dc:subject>
      <dc:subject>e-learning</dc:subject>
      <dc:subject>library</dc:subject>
      <dc:subject>information dissemination</dc:subject>
      <dc:subject>Library and information sciences</dc:subject>
      <dc:description>A German university has developed a learning information system to improve information literacy among German students. An online tutorial based on this Lerninformationssystem has been developed. The structure of this learning information system is described, an online tutorial based on it is illustrated, and the different learning styles that it supports are indicated.</dc:description>
      <dc:publisher>Fachhochschule Hannover</dc:publisher>
      <dc:publisher>Sonstige Einrichtungen. Sonstige Einrichtungen</dc:publisher>
      <dc:date>2006</dc:date>
      <dc:type>Article</dc:type>
      <dc:format>application/pdf</dc:format>
      <dc:identifier>urn:nbn:de:bsz:960-opus-10</dc:identifier>
      <dc:identifier>http://opus.bsz-bw.de/fhhv/volltexte/2008/1/</dc:identifier>
      <dc:source>Australian Academic &amp; Research Libraries, 37 (1), S. 55-60</dc:source>
      <dc:language>eng</dc:language>
      <dc:rights>http://creativecommons.org/licenses/by/2.0/de/deed.de</dc:rights>
     </oai_dc:dc>
   </metadata>
  </record>
  <resumptionToken
     expirationDate="2009-10-02T20:20:04Z"
     completeListSize="226"
     cursor="0">119</resumptionToken>
 </ListRecords>
</OAI-PMH>

*/