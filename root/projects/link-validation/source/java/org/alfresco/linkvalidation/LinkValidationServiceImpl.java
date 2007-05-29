/*-----------------------------------------------------------------------------
*  Copyright 2007 Alfresco Inc.
*  
*  This program is free software; you can redistribute it and/or modify
*  it under the terms of the GNU General Public License as published by
*  the Free Software Foundation; either version 2 of the License, or
*  (at your option) any later version.
*  
*  This program is distributed in the hope that it will be useful, but
*  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
*  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
*  for more details.
*  
*  You should have received a copy of the GNU General Public License along
*  with this program; if not, write to the Free Software Foundation, Inc.,
*  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.  As a special
*  exception to the terms and conditions of version 2.0 of the GPL, you may
*  redistribute this Program in connection with Free/Libre and Open Source
*  Software ("FLOSS") applications as described in Alfresco's FLOSS exception.
*  You should have received a copy of the text describing the FLOSS exception,
*  and it is also available here:   http://www.alfresco.com/legal/licensing
*  
*  
*  Author  Jon Cox  <jcox@alfresco.com>
*  File    LinkValidationServiceImpl.java
*----------------------------------------------------------------------------*/

package org.alfresco.linkvalidation;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.alfresco.config.JNDIConstants;
import org.alfresco.filter.CacheControlFilter;
import org.alfresco.mbeans.VirtServerRegistry;
import org.alfresco.repo.attributes.Attribute;
import org.alfresco.repo.attributes.BooleanAttribute;
import org.alfresco.repo.attributes.BooleanAttributeValue;
import org.alfresco.repo.attributes.IntAttribute;
import org.alfresco.repo.attributes.IntAttributeValue;
import org.alfresco.repo.attributes.ListAttribute;
import org.alfresco.repo.attributes.ListAttributeValue;
import org.alfresco.repo.attributes.MapAttribute;
import org.alfresco.repo.attributes.MapAttributeValue;
import org.alfresco.repo.attributes.StringAttribute;
import org.alfresco.repo.attributes.StringAttributeValue;
import org.alfresco.repo.domain.PropertyValue;
import org.alfresco.sandbox.SandboxConstants;
import org.alfresco.service.cmr.attributes.AttrAndQuery;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.attributes.AttrNotQuery;
import org.alfresco.service.cmr.attributes.AttrOrQuery;
import org.alfresco.service.cmr.attributes.AttrQueryEquals;
import org.alfresco.service.cmr.attributes.AttrQueryGT;
import org.alfresco.service.cmr.attributes.AttrQueryGTE;
import org.alfresco.service.cmr.attributes.AttrQueryLike;
import org.alfresco.service.cmr.attributes.AttrQueryLT;
import org.alfresco.service.cmr.attributes.AttrQueryLTE;
import org.alfresco.service.cmr.attributes.AttrQueryNE;
import org.alfresco.service.cmr.avm.AVMNodeDescriptor;
import org.alfresco.service.cmr.avm.AVMNotFoundException;
import org.alfresco.service.cmr.remote.AVMRemote;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.MD5;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.htmlparser.beans.LinkBean;

/**

Here's a sketch of the algorithm

<pre>

 Before starting, create an empty url cache for this changeset update.
 This will allow us to skip certain redundant ops like status checks.

 For each file F, calulate a url U.

  [1]   Given a source file, what hrefs appear in it explicitly/implicitly:
            md5_source_to_md5_href[ md5( file ) ]  --> map { md5( url ) }
            .href/mysite/|mywebapp/-2/md5_source_to_md5_href/

  [2]   Given an href, in what source files does it appear 
        explicitly or implicitly (via dead reconing):
            md5_href_to_md5_source[ md5( url ) ]  --> map { md5( file ) }
            .href/mysite/|mywebapp/-2/status_to_md5_href/

  [3]   Given an href, what's its status?
            md5_href_to_status[   md5( url ) ]  --> 200/404, etc.
            .href/mysite/|mywebapp/-2/md5_href_to_status/

  [4]   Given a status, what hrefs have it?
            status_to_md5_href[   status  ]  --> map { md5( url ) }
            .href/mysite/|mywebapp/-2/status_to_md5_href/

  [5]   Given an md5, what's the filename?
            md5_to_file[          md5( file ) ]  --> String( file )
            .href/mysite/|mywebapp/-2/md5_to_file/
        
  [6]   Given an md5, what's the href?
            md5_to_href[          md5( url ) ]  --> String( url )
            .href/mysite/|mywebapp/-2/md5_to_href/

  [7]   Given an href what files does it depend on?
            md5_href_to_md5_fdep[ md5( url ) ]  --> map { md5( file ) } 
            .href/mysite/|mywebapp/-2/md5_href_to_md5_fdep/

  [8]   Given a file, what hrefs depend on it
            md5_fdep_to_md5_href[ md5( file ) ]  --> map { md5( url ) }     
            .href/mysite/|mywebapp/-2/md5_fdep_to_md5_href/

  On file creation Fj
  --------------------
  [c0]  Calculate href Ui for Fj, and enter md5(Ui) into ephemeral url cache
  [c1]  Pull on Ui, get file set accessed F 
        This is usually just Fj but could be Fj,...Fk).
            If F = {} or does not include Fj either we failed to compute 
            Ui from Fj or an error has occured when we pulled on Ui.  
            skip all remaining work on Fj & track the error (possibly
            report to gui or just log).  This is a "soft" failure because
            the link's existence is implied by the file's existence 
            (we didn't *actually* extract it from a returned html page).

  [c2]  Update [1] with md5(Fj) -> md5(Ui)  (handles implicit link)
  [c3]  Update [2] with md5(Ui) -> md5(Fj)  (handles implicit link)
  [c4]  Update [3] with md5(Ui) -> status
  [c5]  Update [4] with status  -> md5(Ui)
  [c6]  Update [5] with md5(Fj) -> Fj,       md5(Fk) -> Fk,  ...
  [c7]  Update [6] with md5(Ui) -> Ui
  [c8]  Update [7] with md5(Ui) -> md5(Fj),  md5(Ui) -> md5(Fk), ...
  [c9]  Update [8] with md5(Fj) -> md5(Ui),  md5(Fk) -> md5(Ui), ... 

        For every url Ux in the page (but not the dead-reconed one Ui),
        If Ux is already in the ephemeral url cache,
  [c10]     Next Ux
        Else
  [c11]     Pull on Ux and regardless of what happens:
  [c12]     Update [1] with md5(Fj) -> md5(Ux)
  [c13]     Update [2] with md5(Ux) -> md5(Fj)
  [c14]     Update [3] with md5(Ux) -> status
  [c15]     Update [4] with status  -> md5(Ux)
  [c16] 
        If status==success, determine which files are accessed Fx,Fy, ...
  [c17]     Update [5] with md5(Fx) -> Fx,  md5(Fy) -> Fy, ...
  [c18]     Update [6] with md5(Ux) -> Ux
  [c19]     Update [7] with md5(Ux) -> Fx,  md5(Ux) -> Fy, ...
  [c20]     Update [8] with md5(Fx) -> md5(Ux), md5(Fy) -> md5(Ux), ...


  On file modification
  --------------------

  [m0] Calculate href Ui for Fj, if already in ephermal cache, do next Fj
  [m1] == [c1]
  [m2] == [c2] but it's a no-op
  [m3] == [c3] but it's a no-op
  [m4] == [c4]
  [m5] == [c5]
  [m6] == [c6] but it's a no-op for md5(Fj)
  [m7] == [c7] but it's a no-op
  [m8] == [c8] but it's a no-op for md5(Ui) -> md5(Fj)
  [m9] == [c9] but it's a no-op for md5(Fj) -> md5(Ui)

  [m10]  Parse & get list of hrefs curently in page, plus link to self: Ucurr
  [m11]  Using [1], get previous href list:    Uprev
  [m12]  Subtracing lists, find urls now gone: Ugone
         Note:  implicit link to self never 
                appears in Ugone on modifiction
                becuse [m10] includes implicit 
                link to self.  
        
         For each unique URL Ux in Ugone (no cache check):
  [m13]         Update [1], removing  md5(Fj) -> md5(Ux)
  [m14]         Update [2], removing  md5(Ux) -> md5(Fj)
  [m15]         If [2] shows that href no longer appears anywhere, then:
  [m16]              Remove from [2]  md5(Ux)
  [m17]              Remove from [6]  md5(Ux) -> Ux
  [m18]              Using [7], 
                     for each file md5(Fx) depending on the defunct md5(Ux):
                          Remove from [8]  md5(Fx) -> md5(Ux)
  [m19]              Remove from [7] md5(Ux)
  [m20]              Using [3], fetch status of Ux. If there's a status 
                     then:
  [m21]                      Remove from [3]  md5(Ux) -> status
  [m22]                      Remove from [4]  status  -> md5(Ux)

         For each unique URL Ux in Ucurr
         If Ux isn't already in the ephermal url cache, do:
  [m24..m34] ==  [c10..20]



  On proposed file deletion
  -------------------------
   [p0]  Use [8] to get raw list of URLs that depend on Fj.

         For each URL Ux in [8]:
   [p1]     Use [2] to get list of files with newly broken links
            but omit from this list the Fj itself
            (because it's going away).


  On file deletion Fj
  --------------------
  [d0]  Use [1] to get list of hrefs that appear in Fj explicitly 
        and implicitly.  Call this set Ugone.  

        For each URL Ux in Ugone
  [d1-d9] == [m14..m22] 

  [d10-d11] == [p0-p1]

  [d12]  For each broken link discovered in [d10-d11], update [3] and [4] 
         with 404 status (assumed)... or perhaps some other 4xx status
         to denote "presumed broken" if you don't really test it.

  [d13]  Update [1] by removing md5( Fj )   (remove all urls in one shot)

  [d14]  Using [8], if nothing depends on md5(Fj), 
         then remove md5(Fj) from [5] and [8].

</pre>
*/


public class LinkValidationServiceImpl implements LinkValidationService
{
    private static Log log = LogFactory.getLog(LinkValidationServiceImpl.class);

    static String HREF                 = ".href";    // top level href key

    static String LATEST_VERSION       = "latest";   // numerical version
    static String LATEST_VERSION_ALIAS = "-2";       // alias for numerical

    static String SOURCE_TO_HREF       = "source_to_href";  // key->list
    static String HREF_TO_SOURCE       = "href_to_source";  // key->map

    static String HREF_TO_STATUS       = "href_to_status";  // key->int
    static String STATUS_TO_HREF       = "status_to_href";  // key->map

    static String MD5_TO_FILE          = "md5_to_file";     // key->string
    static String MD5_TO_HREF          = "md5_to_href";     // key->string

    static String HREF_TO_FDEP         = "href_to_fdep";    // key->list
    static String FILE_TO_HDEP         = "file_to_hdep";    // key->map


    AVMRemote          avm_;
    AttributeService   attr_;
    VirtServerRegistry virtreg_;

    public LinkValidationServiceImpl() { }


    public void   getBrokenHrefs(String path) 
           throws AVMNotFoundException 
    {
        ValidationPathParser p = new ValidationPathParser(avm_, path);
        String store           = p.getStore();
        String webapp_name     = p.getWebappName();
        String app_base        = p.getAppBase();
        String dns_name        = p.getDnsName();

        // Example value:  ".href/mysite"
        String store_attr_base =  getAttributeStemForDnsName( dns_name, 
                                                              false, 
                                                              true
                                                            );

        int version = avm_.getLatestSnapshotID( store );
        //--------------------------------------------------------------------
        // NEON:       faking latest snapshot version and just using HEAD
        version = -1;  // NEON:  TODO remove this line & replace with a JMX
                       //        call to load the version if it isn't already.
                       //
                       //        Question:  how long should the version stay
                       //                   loaded if a load was required?
        //--------------------------------------------------------------------

        if  ( webapp_name != null )
        {
            // NEON - do something with result
            getBrokenHrefsFromWebapp( webapp_name,
                                      store_attr_base );
        }
        else
        {
            Map<String, AVMNodeDescriptor> webapp_entries = null;

            // e.g.:   42, "mysite:/www/avm_webapps"
            webapp_entries = avm_.getDirectoryListing(version, app_base );

            for ( Map.Entry<String, AVMNodeDescriptor> webapp_entry  :
                          webapp_entries.entrySet()
                        )
            {
                webapp_name = webapp_entry.getKey();  // my_webapp
                AVMNodeDescriptor avm_node    = webapp_entry.getValue();

                if ( webapp_name.equalsIgnoreCase("META-INF")  ||
                     webapp_name.equalsIgnoreCase("WEB-INF")
                   )
                {
                    continue;
                }

                if  ( avm_node.isDirectory() )
                {
                    // NEON - do something with result
                    getBrokenHrefsFromWebapp( webapp_name,
                                              store_attr_base );
                }
            }
        }
    }

    public void  getBrokenHrefsFromWebapp( String webapp_name,
                                           String store_attr_base
                                         )
                                          
    {
        // Example value: .href/mysite/|ROOT/-2
        String href_attr =  store_attr_base    + 
                            "/|" + webapp_name +
                            "/"  + LATEST_VERSION_ALIAS;

         
        List<Pair<String, Attribute>> broken_links = 
            attr_.query( href_attr + "/" + STATUS_TO_HREF, 
                         new AttrQueryGTE("400"));

        if  ( broken_links == null ) {return;}

        for ( Pair<String, Attribute> broken : broken_links  )
        {
            String      response_code = broken.getFirst();
            Set<String> href_md5_set  = broken.getSecond().keySet();
            for ( String href_md5 : href_md5_set )
            {
                String href_str = 
                   attr_.getAttribute( href_attr   + "/" + 
                                       MD5_TO_HREF + "/" + 
                                       href_md5
                                     ).getStringValue();

                System.out.println("Status:  " + response_code + "  " + href_str);

                Set<String> file_md5_set = 
                        attr_.getAttribute( 
                                            href_attr      + "/" + 
                                            HREF_TO_SOURCE + "/" + 
                                            href_md5
                                          ).keySet();
                
                for ( String file_md5 : file_md5_set )
                {
                    String file_str = 
                       attr_.getAttribute( href_attr   + "/" + 
                                           MD5_TO_FILE + "/" + 
                                           file_md5
                                         ).getStringValue();

                    System.out.println("         " + file_str);
                }
            }
        }
    }




    public void setAttributeService(AttributeService svc) { attr_ = svc; }
    public AttributeService getAttributeService()         { return attr_;}

    public void setAvmRemote(AVMRemote svc) { avm_ = svc; }
    public AVMRemote getAvmRemote()         { return avm_;}

    public void setVirtServerRegistry(VirtServerRegistry reg)
    { 
        virtreg_ = reg;
    }
    public VirtServerRegistry getVirtServerRegistry()
    { 
        return virtreg_;
    }



    /**
    *   Updates all href info under the specified path.
    *   The 'path' parameter should be the path to either:
    *
    *   <ul>
    *      <li> A store name (e.g.:  mysite)
    *      <li> The path to the dir containing all webapps in a store
    *           (e.g.:  mysite:/www/avm_webapps/ROOT)
    *      <li> The path to a particular webapp, or within a webapp
    *           (e.g.:  mysite:/www/avm_webapps/ROOT/moo)
    *   </ul>
    *
    *   If you give a path to a particular webapp (or within one), 
    *   only that webapp's href info is updated.  If you give a 
    *   store name, or the dir containing all webapps ina store 
    *   (e.g.: mysite:/www/avm_webapps), then the href info for 
    *   all webapps in the store are updated.
    */
    public void updateHrefInfo( String  path, 
                                boolean incremental,
                                int     connect_timeout,
                                int     read_timeout)
                                //  int threads

                throws AVMNotFoundException
    {
        ValidationPathParser p = new ValidationPathParser(avm_, path);
        String store           = p.getStore();
        String webapp_name     = p.getWebappName();
        String app_base        = p.getAppBase();
        String dns_name        = p.getDnsName();


        // Example value:  ".href/mysite"
        String store_attr_base =  getAttributeStemForDnsName( dns_name, 
                                                              true, 
                                                              incremental 
                                                             );

        int version = avm_.getLatestSnapshotID( store );
        //--------------------------------------------------------------------
        // NEON:       faking latest snapshot version and just using HEAD
        version = -1;  // NEON:  TODO remove this line & replace with a JMX
                       //        call to load the version if it isn't already.
                       //
                       //        Question:  how long should the version stay
                       //                   loaded if a load was required?
        //--------------------------------------------------------------------



        // In the future, it should be possible for different webapp 
        // urls should resolve to different servers.
        // http://<dns>.www--sandbox.version-v<vers>.<virt-domain>:<port>

        String virt_domain    = virtreg_.getVirtServerFQDN();
        int    virt_port      = virtreg_.getVirtServerHttpPort();
        String store_url_base = "http://" + 
                                dns_name  + 
                                ".www--sandbox.version--v" + version  + "." + 
                                virt_domain + ":" + virt_port;

        MD5 md5 = new MD5();
        if  ( webapp_name != null )
        {
            String webapp_url_base = null;
            try 
            {
                webapp_url_base = 
                       store_url_base + (webapp_name.equals("ROOT") ? "" : 
                       URLEncoder.encode( webapp_name, "UTF-8"));
            }
            catch (Exception e) { /* UTF-8 is supported */ }

            revalidateWebapp( store, 
                              version,
                              true,           // is_latest_version
                              store_attr_base,
                              webapp_name,
                              app_base + "/" + webapp_name,
                              webapp_url_base,
                              md5,
                              connect_timeout,
                              read_timeout
                            );
        }
        else
        {
            Map<String, AVMNodeDescriptor> webapp_entries = null;

            // e.g.:   42, "mysite:/www/avm_webapps"
            webapp_entries = avm_.getDirectoryListing(version, app_base );


            for ( Map.Entry<String, AVMNodeDescriptor> webapp_entry  :
                          webapp_entries.entrySet()
                        )
            {
                webapp_name = webapp_entry.getKey();  // my_webapp
                AVMNodeDescriptor avm_node    = webapp_entry.getValue();

                if ( webapp_name.equalsIgnoreCase("META-INF")  ||
                     webapp_name.equalsIgnoreCase("WEB-INF")
                   )
                {
                    continue;
                }

                // In the future, it should be possible for different webapp 
                // urls should resolve to different servers.
                // http://<dns>.www--sandbox.version-v<vers>.<virt-domain>:<port>

                String webapp_url_base = null;
                try 
                {
                    webapp_url_base = 
                           store_url_base + (webapp_name.equals("ROOT") ? "" : 
                           URLEncoder.encode( webapp_name, "UTF-8"));
                }
                catch (Exception e) { /* UTF-8 is supported */ }

                if  ( avm_node.isDirectory() )
                {
                     revalidateWebapp( store, 
                                       version,
                                       true,           // is_latest_version
                                       store_attr_base,
                                       webapp_name,
                                       app_base + "/" + webapp_name,
                                       webapp_url_base,
                                       md5,
                                       connect_timeout,
                                       read_timeout
                                     );
                }
            }
        }
    }

    void revalidateWebapp( String  store, 
                           int     version,
                           boolean is_latest_version,
                           String  store_attr_base,
                           String  webapp_name,
                           String  webapp_avm_base,
                           String  webapp_url_base,
                           MD5     md5,
                           int     connect_timeout,
                           int     read_timeout
                         )

    {
        HashMap<String,String>  gen_url_cache    = new HashMap<String,String>();
        HashMap<String,String>  parsed_url_cache = new HashMap<String,String>();
        HashMap<Integer,String> status_cache     = new HashMap<Integer,String>();
        HashMap<String,String>  file_hdep_cache  = new HashMap<String,String>();


        // The following convention is used:  
        //           version <==> (version - max - 2) %(max+2)
        //
        // The only case that ever matters for now is that:
        // -2 is an alias for the last snapshot
        //
        // Thus href attribute info for the  "last snapshot" of 
        // a store with the dns name:  preview.alice.mysite is
        // stored within attribute service under the keys: 
        //
        //      .href/mysite/alice/preview/|mywebapp/-2
        //
        // This allows entire projects and/or webapps to removed in 1 step

        // Example:               ".href/mysite/|ROOT"
        String webapp_attr_base =  store_attr_base  +  "/|"  +  webapp_name;

        if ( ! attr_.exists( webapp_attr_base ) ) 
        {
            attr_.setAttribute(store_attr_base, "|" + webapp_name, 
                               new MapAttributeValue());
        }
        
        String href_attr;
        if ( ! is_latest_version )  // add key:  .href/mysite/|mywebapp/latest/42
        {
            // Because we're not validating the last snapshot,
            // don't clobber "last snapshot" info.  Instead
            // just make the href_attr info live under the
            // version specified.  

            //Example:  .href/mysite/|mywebapp/99
            attr_.setAttribute( webapp_attr_base , version, 
                                new MapAttributeValue() );

            //  href data uses the raw version key
            href_attr = webapp_attr_base +  "/" + version;
        }
        else
        {
            // Validating the latest snapshot.  Therefore, record the actual 
            // LATEST_SNAPSHOT version info, but store data under the
            // LATEST_VERSION_ALIAS key ("-2") rather than the version number.
            // This make it possible to do incremental updates more easily
            // because we're not constantly shuffling data around from 
            // exlicit version key to explicit version key.

            //Example:  .href/mysite/|mywebapp/latest -> version

            attr_.setAttribute( webapp_attr_base , 
                                LATEST_VERSION, 
                                new IntAttributeValue( version )
                              );
        
            //Example:  .href/mysite/|mywebapp/-2

            attr_.setAttribute( webapp_attr_base ,  LATEST_VERSION_ALIAS, 
                                new MapAttributeValue() );

            //  href data goes under the "-2" key:    .href/mysite/|myproject/-2
            href_attr = webapp_attr_base +  "/" + LATEST_VERSION_ALIAS;
        }


        attr_.setAttribute( href_attr, SOURCE_TO_HREF, new MapAttributeValue());
        attr_.setAttribute( href_attr, HREF_TO_SOURCE, new MapAttributeValue());
        attr_.setAttribute( href_attr, HREF_TO_STATUS, new MapAttributeValue());
        attr_.setAttribute( href_attr, STATUS_TO_HREF, new MapAttributeValue());
        attr_.setAttribute( href_attr, MD5_TO_FILE,    new MapAttributeValue());
        attr_.setAttribute( href_attr, MD5_TO_HREF,    new MapAttributeValue());
        attr_.setAttribute( href_attr, HREF_TO_FDEP,   new MapAttributeValue());
        attr_.setAttribute( href_attr, FILE_TO_HDEP,   new MapAttributeValue());

        // Info for latest snapshot (42) of mywebapp within mysite is now:
        //
        //      .href/mysite/|mywebapp/latest -> 42
        //      .href/mysite/|mywebapp/-2/source_to_href/   
        //      .href/mysite/|mywebapp/-2/href_to_source/  
        //      .href/mysite/|mywebapp/-2/href_to_status/
        //      .href/mysite/|mywebapp/-2/status_to_href/
        //      .href/mysite/|mywebapp/-2/md5_to_file/
        //      .href/mysite/|mywebapp/-2/md5_to_href/
        //      .href/mysite/|mywebapp/-2/href_to_fdep/
        //      .href/mysite/|mywebapp/-2/file_to_hdep/
        //
        // Info for latest snapshot (42) of mywebapp within mysite/alice is now:
        //
        //      .href/mysite/alice/|mywebapp/latest -> 42
        //      .href/mysite/alice/|mywebapp/-2/source_to_href/   
        //      .href/mysite/alice/|mywebapp/-2/href_to_source/  
        //      .href/mysite/alice/|mywebapp/-2/href_to_status/
        //      .href/mysite/alice/|mywebapp/-2/status_to_href/
        //      .href/mysite/alice/|mywebapp/-2/md5_to_file/
        //      .href/mysite/alice/|mywebapp/-2/md5_to_href/
        //      .href/mysite/alice/|mywebapp/-2/href_to_fdep/
        //      .href/mysite/alice/|mywebapp/-2/file_to_hdep/
        //
        // This makes it easy to delete an entire project or webapp.


        // Find dead reconning URLs for every asset in the system:
        validate_dir( version,
                      webapp_avm_base,
                      webapp_url_base, 
                      href_attr, 
                      md5,
                      gen_url_cache,
                      parsed_url_cache,
                      status_cache,
                      file_hdep_cache,
                      connect_timeout,
                      read_timeout,
                      0 
                    );


        // Now all the generated URLs have had their status checked,
        // but the parsed URLs from these files might not be yet.
        // Pull on every URL that isn't currently in the gen_cache.

        for ( Map.Entry<String,String> entry  :  parsed_url_cache.entrySet() )
        {
            String  parsed_url_md5 = entry.getKey();

            if ( gen_url_cache.containsKey( parsed_url_md5 ) )  // skip if this
            {                                                   // url validated
                continue;                                       // within the dir
            }                                                   // walking phase
            
            String  parsed_url = entry.getValue();

            validate_url( parsed_url,
                          parsed_url_md5, 
                          href_attr,
                          md5,
                          parsed_url.startsWith( webapp_url_base ),
                          status_cache,
                          file_hdep_cache,
                          connect_timeout,
                          read_timeout
                        );
        }
    }

    void validate_dir( int    version, 
                       String dir,
                       String url_base,
                       String href_attr, 
                       MD5    md5,
                       Map<String,String>  gen_url_cache,
                       Map<String,String>  parsed_url_cache,
                       Map<Integer,String> status_cache,
                       Map<String,String>  file_hdep_cache,
                       int    connect_timeout,
                       int    read_timeout,
                       int    depth 
                     )
    {
        Map<String, AVMNodeDescriptor> entries = null;

        // Ensure that the virt server is virtualizing this version

        try
        {
            // e.g.:   42, "mysite:/www/avm_webapps/ROOT/moo"
            entries = avm_.getDirectoryListing( version, dir );
        }
        catch (Exception e)     // TODO: just AVMNotFoundException ?
        {
            if ( log.isErrorEnabled() )
            {
                log.error("Could not list version: " + version + 
                         " of directory: " + dir + "  " + e.getMessage());
            }
            return;
        }

        for ( Map.Entry<String, AVMNodeDescriptor> entry  : entries.entrySet() )
        {
            String            entry_name = entry.getKey();
            AVMNodeDescriptor avm_node   = entry.getValue();

            if ( (depth == 0) &&
                 (entry_name.equalsIgnoreCase("META-INF")  || 
                  entry_name.equalsIgnoreCase("WEB-INF")
                 )
               )
            {
                continue;
            }

            String url_encoded_entry_name = null;
            try 
            {
                url_encoded_entry_name = URLEncoder.encode(entry_name, "UTF-8");
            }
            catch (Exception e) { /* UTF-8 is supported */ }


            if  ( avm_node.isDirectory() )
            {
                // NEON uncomment the next function call:
                //
                validate_dir( version, 
                              dir      + "/"  + entry_name,
                              url_base +  "/" + url_encoded_entry_name,
                              href_attr,
                              md5,
                              gen_url_cache,
                              parsed_url_cache,
                              status_cache,
                              file_hdep_cache,
                              connect_timeout,
                              read_timeout,
                              depth + 1 ) ;
            }
            else
            {
                String implicit_url = url_base  +  "/" + url_encoded_entry_name;

                validate_file( 
                   dir       +  "/" + entry_name,
                   implicit_url,
                   md5.digest(implicit_url.getBytes()),
                   href_attr,
                   md5,
                   gen_url_cache,
                   parsed_url_cache,
                   status_cache,
                   file_hdep_cache,
                   connect_timeout,
                   read_timeout
                );
            }
        }
    }

    void validate_file( String              avm_path,
                        String              gen_url_str, 
                        String              gen_url_md5,
                        String              href_attr,
                        MD5                 md5,
                        Map<String,String>  gen_url_cache,
                        Map<String,String>  parsed_url_cache,
                        Map<Integer,String> status_cache,
                        Map<String,String>  file_hdep_cache,
                        int                 connect_timeout,
                        int                 read_timeout
                      )
    {
        String file_md5= md5.digest(avm_path.getBytes());

        gen_url_cache.put(gen_url_md5,null);

        // A map from the href to the source files that contain it created
        // made as soon a new generated or parsed url is discovered.
        // Because of how directories are traversed when generating urls,
        // in this function we can be certain that the href being processed
        // has never been generated before, but it may have been *parsed*
        // from an earlier file.
        //
        // Therefore, a call to see if the HREF_TO_SOURCE key for this URL
        // exists can be avoided if parsed_url_cache already contains it.
        // Otherwise, we've got to do the actual 'exists' test.

        if ( ! parsed_url_cache.containsKey( gen_url_md5 )   &&
             ! attr_.exists( href_attr + "/" + HREF_TO_SOURCE + "/" + gen_url_md5)
           )
        {
            attr_.setAttribute( href_attr + "/" + HREF_TO_SOURCE,
                                gen_url_md5,
                                new MapAttributeValue()
                              );
        }


        // Claim the url to self "appears" in this source file

        attr_.setAttribute( href_attr + "/" + HREF_TO_SOURCE + "/" + gen_url_md5,
                            file_md5,
                            new BooleanAttributeValue( true )
                          );


        attr_.setAttribute( href_attr + "/" + MD5_TO_FILE, 
                            file_md5, 
                            new StringAttributeValue( avm_path )
                          );


        URL[] urls = validate_url( gen_url_str, 
                                   gen_url_md5, 
                                   href_attr, 
                                   md5,
                                   true,            // get lookup dependencies
                                   status_cache,
                                   file_hdep_cache,
                                   connect_timeout,
                                   read_timeout);

        if ( urls == null ) 
        {
            return;
        }

        // Collect list of hrefs contained by this source file
        // If the generated URL is not already contained in the 
        // parsed URL list, add it.

        ListAttribute href_list_attrib_value = new ListAttributeValue();
        boolean       saw_gen_url            = false;

        for (URL  resp_url  : urls )
        {
            String response_url     = resp_url.toString();
            String response_url_md5 = md5.digest(response_url.getBytes());

            if ( ! saw_gen_url && response_url_md5.equals( gen_url_md5) )
            {
                saw_gen_url = true;
            }
            
            href_list_attrib_value.add( 
                new StringAttributeValue(response_url_md5 ));


            if ( ! parsed_url_cache.containsKey( response_url_md5 ) )
            {
                parsed_url_cache.put(response_url_md5, response_url);

                if ( ! gen_url_cache.containsKey( response_url_md5 ) &&
                     ! attr_.exists( href_attr      + "/" + 
                                     HREF_TO_SOURCE + "/" + 
                                     response_url_md5 )
                   )
                {
                    attr_.setAttribute( href_attr + "/" + HREF_TO_SOURCE,
                                        response_url_md5,
                                        new MapAttributeValue()
                                      );
                }
            }

            attr_.setAttribute( 
                href_attr + "/" + HREF_TO_SOURCE  + "/" + response_url_md5 ,
                file_md5,
                new BooleanAttributeValue( true ));
        }

        if ( ! saw_gen_url )
        {
            href_list_attrib_value.add( new StringAttributeValue( gen_url_md5 ));
        }

        attr_.setAttribute( href_attr + "/" + SOURCE_TO_HREF,
                            file_md5, 
                            href_list_attrib_value
                          );
    }


    URL[]  validate_url( String  url_str,
                         String  url_md5,
                         String  href_attr,
                         MD5     md5,
                         boolean get_lookup_dependencies,
                         Map<Integer,String> status_cache,
                         Map<String,String>  file_hdep_cache,
                         int  connect_timeout,
                         int  read_timeout
                       )
    {
        URL               url  = null;
        HttpURLConnection conn = null; 
        int      response_code = 500;  // uninit

        try 
        { 
            url = new URL( url_str );

            // Oddly, url.openConnection() does not actually
            // open a connection; it merely creates a connection
            // object that is later opened via connect() within
            // the LinkBean.  Go figure.
            //
            conn = (HttpURLConnection) url.openConnection(); 
        }
        catch (Exception e )
        {
            if ( log.isErrorEnabled() )
            {
                log.error("Cannot connect to :" + url_str );
            }

            // Rather than update the URL status just let it retain 
            // whatever status it had before, and assume this is
            // an ephemeral network failure;  "ephemeral" here means 
            // "on all instances of this url for this validation."

            return null;
        }

        if ( get_lookup_dependencies )
        {
            conn.addRequestProperty(
                CacheControlFilter.LOOKUP_DEPENDENCY_HEADER, "true" );
        }

        // "Infinite" timeouts that aren't really infinite
        // are a bad idea in this context.  If it takes more 
        // than 15 seconds to connect or more than 60 seconds 
        // to read a response, give up.  
        //
        LinkBean lb = new LinkBean();

        conn.setConnectTimeout( connect_timeout );  // e.g.:  10000 milliseconds
        conn.setReadTimeout(    read_timeout    );  // e.g.:  30000 milliseconds
        conn.setUseCaches( false );                 // handle caching manually


        if ( log.isInfoEnabled() )
        {
            log.info("About to fetch: " + url_str );
        }

        lb.setConnection( conn );

        try { response_code = conn.getResponseCode(); }
        catch (IOException ioe)
        {
            log.error("Could not fetch response code: " + ioe.getMessage());
            response_code = 500;
        }

        attr_.setAttribute( href_attr + "/" + MD5_TO_HREF, 
                            url_md5, 
                            new StringAttributeValue( url_str )
                          );

        attr_.setAttribute( href_attr + "/" + HREF_TO_STATUS, 
                            url_md5, 
                            new IntAttributeValue( response_code )
                          );

        // Only initialize the response map if absoultely necessary
        // Use the status_cache to eliminate calls to test existence

        if ( ! status_cache.containsKey( response_code ) )  // maybe necessary
        {
            if ( ! attr_.exists( href_attr      + "/" +     // do actual remote
                                 STATUS_TO_HREF + "/" +     // call to see if
                                 response_code )            // this status key
               )                                            // must be created
            {                                                
                attr_.setAttribute( href_attr + "/" + STATUS_TO_HREF, 
                                    "" + response_code,
                                    new MapAttributeValue()
                                  );

            }
            status_cache.put( response_code, null );        // never check again
        }

        attr_.setAttribute( 
                href_attr + "/" + STATUS_TO_HREF + "/" + response_code,
                url_md5,
                new BooleanAttributeValue( true ));


        if ( ! get_lookup_dependencies  ||  
             ( response_code < 200 || response_code >= 300)
           )
        { 
            // The remainder of this function deals with tracking lookup 
            // dependencies.  Because  we only care about the links that
            // URL's page contains if we're tracking dependencies, do
            // an early return here.

            return null; 
        } 


        // Rather than just fetch the 1st LOOKUP_DEPENDENCY_HEADER 
        // in the response, to be paranoid deal with the possiblity that 
        // the information about what AVM files have been accessed is stored 
        // in more than one of these headers (though it *should* be all in 1).
        //
        // Unfortunately, getHeaderFieldKey makes the name of the 0-th header 
        // return null, "even though the 0-th header has a value".  Thus the 
        // loop below is 1-based, not 0-based. 
        //
        // "It's a madhouse! A madhouse!" 
        //            -- Charton Heston playing the character "George Taylor"
        //               Planet of the Apes, 1968
        //

        ArrayList<String> dependencies = new ArrayList<String>();

        String header_key = null;
        for (int i=1; (header_key = conn.getHeaderFieldKey(i)) != null; i++)
        {
            if (!header_key.equals(CacheControlFilter.LOOKUP_DEPENDENCY_HEADER))
            { 
                continue; 
            }

            String header_value = null;
            try 
            { 
                header_value = 
                    URLDecoder.decode( conn.getHeaderField(i), "UTF-8"); 
            }
            catch (Exception e)
            {
                if ( log.isErrorEnabled() )
                {
                    log.error("Skipping undecodable response header: " + 
                              conn.getHeaderField(i));
                }
                continue;
            }

            // Each lookup dependency header consists of a comma-separated
            // list file names.
            String [] lookup_dependencies = header_value.split(", *");

            for (String dep : lookup_dependencies )
            {
                dependencies.add( dep );
            }
        }

        // Now "dependencies" contains a list of all 
        // files upon which url_str URL depends.

        ListAttribute fdep_list_attrib_value = new ListAttributeValue();

        for (String file_dependency : dependencies )
        {
            String fdep_md5 = md5.digest(file_dependency.getBytes());

            if ( ! file_hdep_cache.containsKey( fdep_md5 ) )
            {
                attr_.setAttribute( href_attr + "/" + FILE_TO_HDEP,
                                    fdep_md5,
                                    new MapAttributeValue()
                                  );

                file_hdep_cache.put( fdep_md5, null );
            }

            attr_.setAttribute( href_attr + "/" + FILE_TO_HDEP + "/" + fdep_md5,
                                url_md5,
                                new BooleanAttributeValue( true )
                              );

            fdep_list_attrib_value.add( new StringAttributeValue( fdep_md5 ) );
        }


        attr_.setAttribute( href_attr + "/" + HREF_TO_FDEP,
                            url_md5, 
                            fdep_list_attrib_value
                          );


        URL[] urls = lb.getLinks();
        return urls;
    }



    /**
    *  If 'create' is false, fetches the key bath associated 
    *  with the dns name.
    *  <p>
    *
    *  If 'create' is true, creates keys corresponding to 
    *  the store dns_name being validated, and returns the 
    *  final key path.   If the leaf key already exists 
    *  and 'incremental' is true, then the pre-existing 
    *  leaf key will be reused;  otherwise,  this function 
    *  creates a new leaf (potentially clobbering the 
    *  pre-existing one).
    */
    String getAttributeStemForDnsName( String  dns_name,
                                       boolean create,
                                       boolean incremental )
    {
        // Given a store name X has a dns name   a.b.c
        // The attribute key pattern used is:   .href/c/b/a
        // 
        // This guarantees if a segment contains a ".", it's not a part 
        // of the store's fqdn.  Thus, "." can be used to delimit the end 
        // of the store, and the begnining of the version-specific info.
        // 

        // Construct path & create coresponding attrib entries
        StringBuilder str  = new StringBuilder( dns_name.length() );
        str.append( HREF );

        // Create top level .href key if necessary
        if ( create && ! attr_.exists( HREF ) )
        {
            MapAttribute map = new MapAttributeValue();
            attr_.setAttribute("", HREF, map );
        }

        String [] seg = dns_name.split("\\.");
        String pth;
        for (int i= seg.length -1 ; i>=0; i--) 
        { 
            if (create)
            {
                pth = str.toString();
                if ( ((i==0) && incremental == false ) ||
                     ! attr_.exists( pth + "/" + seg[i] )
                   )
                {
                    MapAttribute map = new MapAttributeValue();
                    attr_.setAttribute( pth , seg[i], map );
                }
            }

            str.append("/" + seg[i] ); 
        }
        String result = str.toString();
        if ( result == null )
        {
            throw new IllegalArgumentException("Invalid DNS name: " + dns_name);
        }
        return result;
    }
}

class ValidationPathParser
{
    static String App_Dir_ = "/" + JNDIConstants.DIR_DEFAULT_WWW   +
                             "/" + JNDIConstants.DIR_DEFAULT_APPBASE;

    String  store_       = null;
    String  app_base_    = null;
    String  webapp_name_ = null;
    String  dns_name_    = null;

    String getStore()      { return store_; }
    String getAppBase()    { return app_base_;}
    String getWebappName() { return webapp_name_;}
    String getDnsName()    { return dns_name_;}

    ValidationPathParser(AVMRemote avm, String path) 
        throws IllegalArgumentException,
               AVMNotFoundException
    {
        int store_index = path.indexOf(':');
        if ( store_index < 0) 
        {
            // We were passed a store path
            store_ = path;
            app_base_ = store_ + ":" + App_Dir_;
        }
        else if ( ! path.startsWith( App_Dir_, store_index+1 )  )
        {
            throw new IllegalArgumentException("Invalid webapp path: " + path);
        }
        else
        {
            store_ = path.substring(0,store_index);
            int webapp_start = 
                path.indexOf('/', store_index + 1 + App_Dir_.length()); 

            if (webapp_start >= 0)
            {
                int webapp_end = path.indexOf('/', webapp_start + 1);
                webapp_name_ = path.substring( webapp_start +1, 
                                              (webapp_end < 1)
                                              ?  path.length()
                                              :  webapp_end
                                            );
                if  ((webapp_name_ != null) && 
                     (webapp_name_.length() == 0)
                    ) 
                { 
                    webapp_name_ = null; 
                }
            }
        }

        dns_name_ = lookupStoreDNS( avm, store_ );
        if ( dns_name_ == null ) 
        { 
            throw new AVMNotFoundException(
                       "No DNS entry for AVM store: " + store_);
        }
    }

    String lookupStoreDNS(AVMRemote avm,  String store )
    {
        Map<QName, PropertyValue> props = 
                avm.queryStorePropertyKey(store, 
                     QName.createQName(null, SandboxConstants.PROP_DNS + '%'));

        return ( props.size() != 1 
                 ? null
                 : props.keySet().iterator().next().getLocalName().
                         substring(SandboxConstants.PROP_DNS.length())
               );
    }
}
