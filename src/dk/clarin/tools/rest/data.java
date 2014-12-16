package dk.clarin.tools.rest;

import dk.clarin.tools.ToolsProperties;
import dk.clarin.tools.workflow;
import dk.cst.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Return result from Tools. E.g. in response to this URL:
 *      https://clarin.dk/tools/data/3892126799-323-step1.xml
 * The file is deleted from the staging area after delivery, so results can
 * only be fetched once.
 *      - illegal fetches are less likely, as the real owner of the result
 *        will notice this
 *      - sharing of results is the owner's responsibility and cannot be done
 *        by sharing the URL
 *      - results don't use diskspace in the staging area once fetched.
 * Results that for some reason are needed by other tasks are not deleted.
 * A call to the 'keep' function in toolsProg.bra checks that by looking for
 * outstanding jobs that take the result as input.
 * Directory listings are forbidden, so the URL
 *      https://clarin.dk/tools/data/
 * returns an informational text.
 * Return codes 200 
 *              404 if a directorly listing is attempted or if the file is no
 *                  longer accessible.
 *              500 if Bracmat could not be loaded
 */
@SuppressWarnings("serial")
public class data extends HttpServlet 
    {
    // Static logger object.  
    private static final Logger logger = LoggerFactory.getLogger(data.class);

    private File destinationDir;
    private bracmat BracMat;
    /// The eSciDoc userHandle
    //private String userHandle = null;
    /// The eSciDoc id of the user
    //private String userId;
    /// The users email
    //private String userEmail;

    //private String date;
    //private String toolsdataURL;



    public void init(javax.servlet.ServletConfig config) throws javax.servlet.ServletException 
        {
        logger.debug("init tools servlet");
        InputStream fis = config.getServletContext().getResourceAsStream("/WEB-INF/classes/properties.xml");
        ToolsProperties.readProperties(fis);		
        //Calendar cal = Calendar.getInstance();
        //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        //date = sdf.format(cal.getTime());
        BracMat = new bracmat(ToolsProperties.bootBracmat);
        //toolsdataURL = ToolsProperties.baseUrlTools + ToolsProperties.stagingArea;
        super.init(config);
        destinationDir = new File(ToolsProperties.documentRoot + ToolsProperties.stagingArea);
        if(!destinationDir.isDirectory()) 
            {
            throw new ServletException("Trying to set \"" + ToolsProperties.documentRoot + ToolsProperties.stagingArea + "\" as directory for temporary storing intermediate and final results, but this is not a valid directory.");
            }
        }

    /*
    Method doGet is called if Tools is used to process resources copied from 
    the repository.
     (In contrast to processing uploaded files.)
    Each resource is represented as an 'item' parameter, e.g.
    https://clarin.dk/tools/create?item=dkclarin:111012&item=dkclarin:111016
    Alternatively, all resources can be listed in a single 'ids' parameter, e.g.
    https://clarin.dk/tools/create?ids=dkclarin:168004,dkclarin:168028,dkclarin:168332 
    */
    public void doGet(HttpServletRequest request,HttpServletResponse response)
        throws ServletException, IOException 
        {
        //userHandle = null;
        //userId = null;
        //userEmail = null;
        logger.info("Calling tools/data");
        response.setStatus(200);
        if(!BracMat.loaded())
            {
            response.setStatus(500);
            throw new ServletException("Bracmat is not loaded. Reason:" + BracMat.reason());
            }
        /*Test:*/
        @SuppressWarnings("unchecked")
        Enumeration<String> parmNames = (Enumeration<String>)request.getParameterNames();
        logger.debug("show parms");
        for (Enumeration<String> e = parmNames ; e.hasMoreElements() ;) 
            {
            String parmName = e.nextElement();
            logger.debug("parmName:"+parmName);
            String vals[] = request.getParameterValues(parmName);
            for(int j = 0;j < vals.length;++j)
                {
                logger.debug("val:"+vals[j]);
                }
            }
        /*:Test*/
        logger.info("getPathInfo() returns {}",request.getPathInfo());              // /blablah/de/blah
        if(request.getPathInfo() == null || request.getPathInfo().equals("/"))
            {
            response.setContentType("text/html; charset=UTF-8");
            //response.sendError(404,"Sorry, no directory listing.");
            response.setStatus(404);
            PrintWriter out = response.getWriter();
            out.println("Sorry, no directory listing.");
            //throw new ServletException("Sorry, no directory listing.");
            }
        else
            {
            response.setContentType("text/plain");
            try
                {
                // With ContentType("text/xml") the md5 checksum for the sender isn't the same as for the receiver.
                // IMPORTANT --- Uncomment if checksum is abolished.
                // (If sent as ContentType("text/plain"), an XML-file doesn't look nice in the receiver's browser.)
                /*checksum IS abolished*/if(request.getPathInfo().endsWith(".xml"))
                    response.setContentType("text/xml");
                else/**/
                    response.setContentType("text/plain");
    
                String fileName = destinationDir + request.getPathInfo();
                File f = new File(fileName);
                //BufferedReader bufR = new BufferedReader(new FileReader(f));
                int nLen = 0;
                //String str = "";
                OutputStream out;
                FileInputStream in;
                in = new FileInputStream(f);
                out = response.getOutputStream();
                byte[] bBuf = new byte[1024];
                try
                    {
                    while ((nLen = in.read(bBuf, 0, 1024)) != -1)
                        {
                        out.write(bBuf, 0, nLen);
                        }
                    }
                finally
                    {
                    in.close();
                    }
                /**
                 * keep$
                 * 
                 * Check whether a result from a tool in the staging area can be
                 * deleted.
                 * 
                 * Results that for some reason are needed by other tasks must
                 * be kept. The function looks for outstanding jobs that take
                 * the argument as input. Argument: file name, may be preceded
                 * by a slash /19231210291
                 * 
                 * NOTICE: If the file need not be kept, the file's name is
                 * deleted from several tables, so calling keep has side
                 * effects! Affected tables in jboss/server/default/data/tools:
                 * jobs.table Uploads.table CTBs.table relations.table
                 * jobAbout.table
                 */
                String svar = BracMat.Eval("keep$("+workflow.quote(request.getPathInfo()) + ")");
                logger.info(svar + " " + request.getPathInfo());
                if(svar.equals("no"))
                    {
                    boolean success = f.delete();
                    if (success)
                        {
                        logger.info(fileName + ": deleted");
                        }
                    else
                        {
                        logger.info(fileName + ": deletion failed");
                        }
                    }
                //f.delete();
                logger.info("doGet:deleted {}{}",destinationDir,request.getPathInfo());              // /blablah/de/blah
                }
            catch (FileNotFoundException e)
                {
                //
                response.setContentType("text/html; charset=UTF-8");
                //response.sendError(404,"File " + request.getPathInfo() + " does not exist.");
                response.setStatus(404);
                PrintWriter out = response.getWriter();
                String name = request.getPathInfo();
                if(name.startsWith("/"))
                    name = name.substring(1);
                out.println("File " + name + " is no longer accessible.");
                //throw new ServletException("File " + request.getPathInfo() + " does not exist.");
                }
            }
        }
    }
