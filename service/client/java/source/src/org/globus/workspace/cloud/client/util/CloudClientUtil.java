/*
 * Copyright 1999-2008 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.globus.workspace.cloud.client.util;

import org.apache.axis.message.addressing.EndpointReferenceType;
import org.apache.axis.types.URI;
import org.globus.common.CoGProperties;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GlobusCredentialException;
import org.globus.util.Util;
import org.globus.workspace.client_core.ExecutionProblem;
import org.globus.workspace.client_core.ParameterProblem;
import org.globus.workspace.client_core.repr.Workspace;
import org.globus.workspace.client_core.utils.EPRUtils;
import org.globus.workspace.client_core.utils.NimbusCredential;
import org.globus.workspace.client_core.utils.StringUtils;
import org.globus.workspace.cloud.client.AllArgs;
import org.globus.workspace.cloud.client.Props;
import org.globus.workspace.common.print.Print;
import org.globus.wsrf.encoding.DeserializationException;
import org.globus.wsrf.encoding.ObjectDeserializer;
import org.nimbustools.messaging.gt4_0.common.CommonUtil;
import org.nimbustools.messaging.gt4_0.generated.metadata.VirtualWorkspace_Type;
import org.nimbustools.messaging.gt4_0.generated.metadata.definition.BoundDisk_Type;
import org.nimbustools.messaging.gt4_0.generated.metadata.definition.Definition;
import org.nimbustools.messaging.gt4_0.generated.metadata.definition.DiskCollection_Type;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class CloudClientUtil {

    public static String sourceURL(String sourcePath) {
        final String sourceAbsolutePath = absPath(sourcePath);
        return "file:///" + sourceAbsolutePath;
    }

    public static String localTargetURL(String targetPath) {
        // same implementation
        return sourceURL(targetPath);
    }

    public static String serviceURL(String hostport) {
        return "https://" + hostport +
                "/wsrf/services/WorkspaceFactoryService";
    }

    public static String statusServiceURL(String hostport) {
        return "https://" + hostport +
                "/wsrf/services/WorkspaceStatusService";
    }

    public static String destURL(String sourcePath,
                          String userBaseURL) {
        final String sourceAbsolutePath = absPath(sourcePath);
        final String filename = fileName(sourceAbsolutePath);
        return userBaseURL + filename;
    }

    public static String destUserBaseDir(String destBaseDirectory,
                                         String userBaseDirectory) {

        if (destBaseDirectory == null) {
            throw new IllegalArgumentException(
                            "destBaseDirectory may not be null");
        }

        if (userBaseDirectory == null) {
            throw new IllegalArgumentException(
                            "userBaseDirectory may not be null");
        }

        String topdir = destBaseDirectory;
        if (!destBaseDirectory.startsWith("/")) {
            topdir = "/" + destBaseDirectory;
        }

        String userdir = userBaseDirectory;
        if (!topdir.endsWith("/") && !userBaseDirectory.startsWith("/")) {
            userdir = "/" + userBaseDirectory;
        }

        return topdir + userdir + "/";
    }

    public static String destUserBaseURL(String destGridFTPServer,
                                         String destBaseDirectory,
                                         String userBaseDirectory) {

        if (destGridFTPServer == null) {
            throw new IllegalArgumentException(
                            "destGridFTPServer may not be null");
        }

        final String tail = destUserBaseDir(destBaseDirectory,
                                            userBaseDirectory);

        return "gsiftp://" + destGridFTPServer + "/" + tail;
    }

    public static boolean fileExistsAndReadable(File f) {
        return f.exists() && f.isFile() && f.canRead();
    }

    public static boolean fileExistsAndReadwritable(File f) {
        return f.exists() && f.isFile() && f.canRead() && f.canWrite();
    }

    public static boolean fileExists(String sourcePath) {
        if (sourcePath == null) {
            return false;
        }
        final File f = new File(sourcePath);
        return f.exists();
    }

    public static boolean fileExistsAndReadable(String sourcePath) {
        if (sourcePath == null) {
            return false;
        }
        final File f = new File(sourcePath);
        return fileExistsAndReadable(f);
    }

    public static boolean fileExistsAndReadwritable(String sourcePath) {
        if (sourcePath == null) {
            return false;
        }
        final File f = new File(sourcePath);
        return fileExistsAndReadwritable(f);
    }

    public static String absPath(String sourcePath) {
        final File f = new File(sourcePath);
        return f.getAbsolutePath();
    }

    public static String fileName(String sourcePath) {
        final File f = new File(sourcePath);
        return f.getName();
    }

    public static String getProp(Properties props, String key) {
        if (props == null) {
            throw new IllegalArgumentException("props may not be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("key may not be null");
        }
        final String prop = props.getProperty(key);
        if (prop == null || prop.trim().length() == 0) {
            return null;
        }
        return prop;
    }

    public static boolean validURL(String urlString, PrintStream debug) {

        try {

            final URL url = new URL(urlString);
            if (debug != null) {
                debug.println("valid URL: '" + url.toString() + "'");
            }
            return true;

        } catch (MalformedURLException e) {

            // gsiftp is an unknown scheme for Java
            // replace just gsiftp and check URL
            String newTestURL = urlString.trim();
            if (newTestURL.startsWith("gsiftp")) {
                newTestURL = newTestURL.replaceFirst("gsiftp", "http");
                if (debug != null) {
                    debug.println("Protocol replacement for URL check:");
                    debug.println("  -- using '" + newTestURL + "'");
                }

                try {
                    final URL url = new URL(newTestURL);
                    if (debug != null) {
                        debug.println("Valid URL: '" + url.toString() + "'");
                        debug.println("GridFTP URL: '" + urlString + "'");
                    }
                    return true;
                } catch (MalformedURLException e2) {
                    if (debug != null) {
                        debug.println(
                             "URL still invalid after a gsiftp/http switch");
                    }
                }
            }

            if (debug != null) {
                debug.println("URL '" + urlString + "' is not a valid URL:");
                e.printStackTrace(debug);
            }

            return false;
        }
    }

    public static void verifyHistoryDir(String historyDir,
                                        boolean needWrites,
                                        boolean createIfAbsent) 
              throws ParameterProblem {

        if (historyDir == null) {
            throw new IllegalArgumentException("historyDir may not be null");
        }

        final File adir = new File(historyDir);
        if (!adir.exists()) {

            if (createIfAbsent) {
                if (adir.mkdir()) {
                    return; // *** EARLY RETURN ***
                } else {
                    throw new ParameterProblem("History directory ('" +
                            historyDir + "') did not exist. Tried to create " +
                            "it but that failed.");
                }
            } else {
                throw new ParameterProblem(
                    "History directory does not exist: '" + historyDir + "'");
            }
            
        }

        if (!adir.isDirectory()) {
            throw new ParameterProblem(
                "History directory is not a directory: '" + historyDir + "'");
        }

        if (needWrites) {
            if (!adir.canWrite()) {
                throw new ParameterProblem(
                    "History directory is not writeable: '" + historyDir + "'");
            }
        }
    }

    public static File getHistoryDir(String historyDir) 
            throws ParameterProblem {
        verifyHistoryDir(historyDir, true, false); 
        return new File(historyDir);
    }

    public static void printFileList(FileListing[] files, Print pr) {

        if (files == null || files.length == 0) {
            pr.infoln("No files.");
            return;
        }

        int longest = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i] == null) {
                continue;
            }
            final String name = files[i].getName();
            if (name == null) {
                continue;
            }
            final int len = name.length();
            if (len > longest) {
                longest = len;
            }
        }

        final int goodRightJust = 31;
        int rightJust = goodRightJust;
        if (longest > rightJust) {
            rightJust = longest + 2;
        }

        final Hashtable readonly = new Hashtable(files.length);
        final Hashtable readwrite = new Hashtable(files.length);

        for (int i = 0; i < files.length; i++) {
            final FileListing fl = files[i];
            if (fl == null) {
                continue;
            }
            final String name = fl.getName();
            if (name == null) {
                pr.errln("[x] null name from FileListing (?)");
                continue;
            }

            if (fl.isDirectory()) {
                pr.infoln("[Directory] '" + name + "'\n");
                continue;
            }

            final String entry = onePrintStr(name, fl, goodRightJust,
                                             rightJust, fl.isReadWrite());

            if (fl.isReadWrite()) {
                readwrite.put(name, entry);
            } else {
                readonly.put(name, entry);
            }
        }

        final List readwriteNames = new ArrayList(readwrite.size());
        for (Enumeration e = readwrite.keys() ; e.hasMoreElements() ;) {
            readwriteNames.add(e.nextElement());
        }

        final List readonlyNames = new ArrayList(readonly.size());
        for (Enumeration e = readonly.keys() ; e.hasMoreElements() ;) {
            readonlyNames.add(e.nextElement());
        }

        Collections.sort(readwriteNames);
        Collections.sort(readonlyNames);

        final Iterator iter2 = readwriteNames.iterator();
        while (iter2.hasNext()) {
            pr.infoln((String)readwrite.get(iter2.next()));
            pr.infoln();
        }

        if (!readwrite.isEmpty() && !readonly.isEmpty()) {
            pr.infoln("----\n");
        }

        final Iterator iter = readonlyNames.iterator();
        while (iter.hasNext()) {
            pr.infoln((String)readonly.get(iter.next()));
            pr.infoln();
        }
    }

    private static String onePrintStr(String name,
                                      FileListing fl,
                                      int goodRightJust,
                                      int rightJust,
                                      boolean readWrite) {

        if (name == null || fl == null) {
            throw new IllegalArgumentException("args may not be null");
        }

        final int len = name.length();

        int numBlank = 0;
        if (len < goodRightJust) {
            numBlank = goodRightJust - len;
        } else if (len < rightJust) {
            numBlank = rightJust - len;
        }
        final StringBuffer buf = new StringBuffer(numBlank);
        for (int j = 0; j < numBlank; j++) {
            buf.append(" ");
        }
        final String rtText = buf.toString();

        final StringBuffer send = new StringBuffer(512);
        send.append("[Image] '")
            .append(name)
            .append("'")
            .append(rtText);

        if (readWrite) {
            send.append("Read/write");
        } else {
            send.append("Read only");
        }

        send.append("\n        Modified: ");

        final String dateTime = fl.getDate() + " @ " + fl.getTime();
        send.append(dateTime);
        if (dateTime.length() < 14) {
            send.append(" ");
        }

        final long bytes = fl.getSize();
        final long mbEstimate = bytes/1024/1024;
        send.append("   Size: ")
            .append(bytes)
            .append(" bytes (~")
            .append(mbEstimate)
            .append(" MB)");

        return send.toString();
    }

    /**
     * @param workspaces print these
     * @param print pr
     * @param matchLookPath if not null, will look recursively under this dir
     *                      for matching EPR files.  It will suggest the cloud
     *                      handles using the handle-as-directory-name convention.
     * @param statusEPR which status service the information came from
     */
    public static void printCurrent(Workspace[] workspaces,
                                    Print print,
                                    String matchLookPath,
                                    EndpointReferenceType statusEPR) {

        if (print == null) {
            throw new IllegalArgumentException("print may not be null");
        }

        if (!print.enabled()) {
            return; // *** EARLY RETURN ***
        }

        if (workspaces == null || workspaces.length == 0) {

            final String msg =
                    "There's nothing running on this cloud that you own.";
            print.infoln(msg);

            return; // *** EARLY RETURN ***
        }

        String serviceAddr = null;

        final String statusAddress =
                EPRUtils.getServiceURIAsString(statusEPR);
        if (statusAddress != null) {
            final int idx = statusAddress.indexOf("WorkspaceStatusService");
            final String prefix = statusAddress.substring(0, idx);
            serviceAddr = prefix + "WorkspaceService";
        }

        int count = 0;
        for (Workspace workspace : workspaces) {
            final String msg = StringUtils.
                    shortStringReprMultiLine(workspace);

            if (count > 0) {
                print.infoln();
            }
            count += 1;
            
            if (msg == null) {
                final String err = "No string representation?";
                print.errln(err);
            } else {
                print.infoln(msg);
            }

            if (matchLookPath != null) {
                String matches = getMatches(workspace,
                                            print,
                                            matchLookPath,
                                            serviceAddr);
                if (matches == null || matches.trim().length() == 0) {
                    print.infoln("      No matching handle found in " +
                            "history directory.");
                    print.debug("(looked @ history directory '" +
                            matchLookPath + "')");
                } else {
                    String[] parts = matches.trim().split(" ");
                    if (parts != null && parts.length > 1) {
                        print.infoln("      *Possible handles: " + matches);
                    } else {
                        print.infoln("      *Handle: " + matches);
						final String images =
								getImageNames(matchLookPath, matches.trim(), print);
						String[] imageparts = images.trim().split(",");
						if (imageparts.length > 1) {
							print.infoln("       Images: " + images);
						} else {
							print.infoln("       Image: " + images);
						}
                    }
                }
            }
        }
    }

    private static String getMatches(Workspace workspace,
                                     Print pr,
                                     String matchLookPath,
                                     String serviceAddr) {

        if (workspace == null || matchLookPath == null || pr == null) {
            return null; // *** EARLY RETURN ***
        }

        final Integer id = workspace.getID();
        if (id == null) {
            pr.errln("no workspace id to look for?");
            return null; // *** EARLY RETURN ***
        }

        final File dir = new File(matchLookPath);
        if (!dir.isDirectory()) {
            pr.errln("'" + matchLookPath + "' is not a directory?");
            return null; // *** EARLY RETURN ***
        }

        final String[] subdirs = dir.list(new dirFilter());
        if (subdirs == null) {
            pr.debug("No subdirectories");
            return null; // *** EARLY RETURN ***
        }

        String matches = "";
        for (String subdir : subdirs) {
            if (subdir.startsWith(HistoryUtil.historyClusterDirPrefix)) {
                try {
                    matches += clusterMatch(dir, subdir, id);
                } catch (Exception e) {
                    pr.errln("Problem reading cluster EPR in '" +
                                subdir +"': " + e.getMessage());
                }
            } else if (subdir.startsWith(HistoryUtil.historyDirPrefix)) {
                try {
                    matches += instanceMatch(dir, subdir, id, serviceAddr);
                } catch (Exception e) {
                    pr.errln("Problem reading instance EPR in '" +
                                subdir +"': " + e.getMessage());
                }
            } else {
                pr.debug("(unexpected subdirectory in history " +
                         "dir: '" + subdir + "')");
            }
        }

        return matches;
    }

	// get all images in the metadata files in this history subdir
	private static String getImageNames(String matchLookPath, String matchSubdir, Print pr) {

		final File dir = new File(matchLookPath);
        if (!dir.isDirectory()) {
            pr.errln("      '" + matchLookPath + "' is not a directory?");
            return "none"; // *** EARLY RETURN ***
        }

        final File subdir = new File(dir, matchSubdir);
		pr.debugln("examining '" + subdir.getAbsolutePath() + "' for image strings");
        if (!subdir.exists()) {
            pr.errln("      Not a subdirectory?");
            return "none"; // *** EARLY RETURN ***
        }

		final String[] subfiles = subdir.list(new fileFilter());

		if (subfiles == null || subfiles.length == 0) {
            return "none";
        }

		String ret = "";

        for (String subfile : subfiles) {

			final File f = new File(subdir, subfile);
            if (fileExistsAndReadable(f)) {

                VirtualWorkspace_Type vwType;
                try {
                    vwType = (VirtualWorkspace_Type)
                            ObjectDeserializer.deserialize(
                                new InputSource(new FileInputStream(f)),
                                                VirtualWorkspace_Type.class);
                } catch (Throwable t) {
                    // not an issue, some of these are text files etc.
                    continue;
                }

                if (vwType != null) {
                    try {
                        final Definition def = vwType.getDefinition();
						if (def != null) {
							final DiskCollection_Type dct = def.getDiskCollection();
							if (dct != null) {
								final BoundDisk_Type bdt = dct.getRootVBD();
								if (bdt != null) {
									final URI uri = bdt.getLocation();
									if (uri != null) {
										String path = uri.getPath();
										StringTokenizer st = new StringTokenizer(path, "/");
										String last = "";
										while (st.hasMoreTokens()) {
											last = st.nextToken();
										}
										if (ret.length() > 0) {
											ret += ", " + last;
										} else {
											ret += last;
										}
									}
								}
							}
						}
                    } catch (Throwable t) {
                        continue;
                    }
                }
            }
			


		}

		if (ret.length() == 0) {
			return "none";
		} else {
			return ret;
		}
	}

    private static String clusterMatch(File topdir,
                                       String subdir,
                                       int id) {

        final File thisdir = new File(topdir, subdir);

        final String[] subfiles = thisdir.list(new fileFilter());

        if (subfiles == null || subfiles.length == 0) {
            return "";
        }

        for (String subfile : subfiles) {

            final File f = new File(thisdir, subfile);
            if (fileExistsAndReadable(f)) {

                EndpointReferenceType epr;
                try {
                    epr = (EndpointReferenceType)
                            ObjectDeserializer.deserialize(
                                new InputSource(new FileInputStream(f)),
                                                EndpointReferenceType.class);
                } catch (Throwable t) {
                    // not an issue, some of these are text files etc.
                    continue;
                }

                // see note below about matching against particular clouds
                if (epr != null) {
                    try {
                        if (EPRUtils.isInstanceEPR(epr)) {
                            int eprID = EPRUtils.getIdFromEPR(epr);
                            if (eprID == id) {
                                return subdir + " ";
                            }
                        }
                    } catch (Throwable t) {
                        // library out of our control can throw NPE in certain
                        // places in our brute force approach
                        continue;
                    }
                }
            }
        }

        return "";
    }

    private static String instanceMatch(File topdir,
                                        String subdir,
                                        int id,
                                        String serviceAddress)
            throws DeserializationException, FileNotFoundException {

        final File instanceEPR = new File(new File(topdir, subdir),
                                          HistoryUtil.SINGLE_EPR_FILE_NAME);

        if (!fileExistsAndReadable(instanceEPR)) {
            return "";
        }
        
        EndpointReferenceType epr =
            (EndpointReferenceType) ObjectDeserializer.deserialize(
                    new InputSource(new FileInputStream(instanceEPR)),
                    EndpointReferenceType.class);

        // it's possible to use same cloud client with different clouds:
        //
        // TODO, the following is currently FAILING because of hostname vs.
        // IP address in the EPR:

        /* if (epr != null && serviceAddress != null) {
            final String address = EPRUtils.getServiceURIAsString(epr);
            if (address == null || !address.trim().equals(serviceAddress)) {
                return ""; // *** EARLY RETURN ***
            }
        } */
        

        if (epr != null && EPRUtils.isInstanceEPR(epr)) {
            int eprID = EPRUtils.getIdFromEPR(epr);
            if (eprID == id) {
                return subdir + " ";
            }
        }

        return "";
    }

    public static RepositoryInterface getRepoUtil(
        String                          repoType,
        AllArgs                         args,
        Print                           print)
    {
        RepositoryInterface             repoUtil;

        if(repoType == null)
        {
            repoType = "gridftp";
        }
        if(repoType.equals("cumulus"))
        {
            repoUtil = new CumulusRepositoryUtil(args, print);
        }
        else
        {
            repoUtil = new GridFTPRepositoryUtil(args, print);
        }
        return repoUtil;
    }

    public static String deriveImageURL(String imageName, AllArgs args)
                              throws ExecutionProblem {

        RepositoryInterface repoUtil;

        String repoType = args.getXferType();
        repoUtil = CloudClientUtil.getRepoUtil(repoType, args, new Print());

        if (imageName == null) {
            throw new IllegalArgumentException("imageName may not be null");
        }

        return repoUtil.getDerivedImageURL(imageName);
    }

    public static String expandSshPath(String sshfile) 
        throws ParameterProblem {

        if (sshfile == null) {
            throw new IllegalArgumentException("sshfile may not be null");
        }

        if (sshfile.startsWith("~")) {

            final String homedir = System.getProperty("user.home");

            if (homedir == null || homedir.trim().length() == 0) {
                throw new ParameterProblem("Need to replace tilde in " +
                        "SSH public key file, but cannot determine " +
                        "user home directory.  Please hardcode, see " +
                        "properties file.");
            }
            final String result = sshfile.replaceFirst("~", homedir);

            sshfile = result;
        }
        return sshfile;
    }

    private static class dirFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            final File test = new File(dir, name);
            return test.isDirectory();
        }
    }

    private static class fileFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            final File test = new File(dir, name);
            return test.isFile();
        }
    }

    public static void checkX509Credential(String action, Print print)
        throws ParameterProblem {

        try {
            CloudClientUtil.getActiveX509Credential(print);
        } catch (Exception e) {

            String actionTxt = action;

            if (action == null) {
                actionTxt = "This action";
            }

            String msg = actionTxt + " requires an X509 credential.\n\n";

            msg += "If you point to an unencrypted X509 key with the \"nimbus.cert\"\n" +
                   "and \"nimbus.key\" properties, that will be used. If these properties\n" +
                    "are not absolute, they will be resolved relative to the\n"+
                    "configuration file they are found in.\n\n";
            msg += "If your X509 key file is encrypted on disk, you will need to\n" +
                   "first run \"./bin/grid-proxy-init.sh\" and the program will use\n" +
                   "the created \"/tmp/x509up_$unix_id\" proxy certificate file.\n\n";
            msg += "If that file is not present, if you have an unencrypted X509 key\n" +
                   "\"~/.nimbus/userkey.pem\", that will be used.\n\n";
            msg += "If that file is not present, if you have an unencrypted X509 key\n" +
                   "\"~/.globus/userkey.pem\", that will be used.\n\n";

            msg += e.getMessage();
            throw new ParameterProblem(msg);
        }
    }

    public static GlobusCredential getActiveX509Credential(Print print) throws Exception {

        final StringBuilder accumulatedErrors =
               new StringBuilder("-----------------------------\n\nHere is what happened:\n\n");

        try {
            print.debugln("First, attempting to load any credential specified in properties");

            String certPath = NimbusCredential.getNimbusCertificatePath();
            if (certPath == null) {
                certPath = "Not set.";
            }
            String keyPath = NimbusCredential.getNimbusUnencryptedKeyPath();
            if (keyPath == null) {
                keyPath = "Not set.";
            }
            String files = "  - " + Props.KEY_NIMBUS_CERT + ": " + certPath + '\n';
            files += "  - " + Props.KEY_NIMBUS_KEY + ": " + keyPath + '\n';
            print.debugln(files);

            final GlobusCredential credential = NimbusCredential.getGlobusCredential();
            if (credential != null) {
                print.debugln("Using Nimbus credential from properties:");
                print.debugln("    - " + NimbusCredential.getNimbusCertificatePath());
                print.debugln("    - " + NimbusCredential.getNimbusUnencryptedKeyPath());
                print.debugln();
                return credential;
            }
            String err = "Could not load credential from properties, properties are " +
                    "probably not set.\n";
            print.debugln(err);
            err += files;
            accumulatedErrors.append(err);
        } catch (Exception e) {
            final String err = "Problem loading credential from properties:\n    " +
                    CommonUtil.recurseForSomeString(e);
            accumulatedErrors.append(err).append('\n');
            print.debugln(err);
        }

        try {
            print.debugln("Attempting to load the default X509 proxy file");
            final GlobusCredential credential = GlobusCredential.getDefaultCredential();
            print.debugln("Using default X509 proxy:");
            proxyInformation(credential, print);
            return credential;
        } catch (Exception e) {
            final String err = "Problem loading default proxy credential:" +
                    "\n    " + CommonUtil.recurseForSomeString(e);
            accumulatedErrors.append(err).append('\n');
            print.debugln(err);
        }

        final String dotNimbus = expandTilde("~/.nimbus/");
        try {
            print.debugln("\nLooking for unencrypted credential in ~/.nimbus");
            final File usercert = guessCertFromDirectory(dotNimbus);
            final File userkey = guessKeyFromDirectory(dotNimbus);
            final GlobusCredential credential =
                    getUnencryptedUsercert(usercert, userkey, print);
            NimbusCredential.setUnencryptedCredential(usercert.getAbsolutePath(),
                                                       userkey.getAbsolutePath());
            print.debugln("Using unencrypted ~/.nimbus credential\n");
            return credential;
        } catch (Exception e) {
            final String err = "Problem loading from ~/.nimbus:\n    " +
                    CommonUtil.recurseForSomeString(e);
            accumulatedErrors.append(err).append('\n');
            print.debugln(err);
        }

        final String dotGlobus = expandTilde("~/.globus/");
        try {
            print.debugln("\nLooking for unencrypted credential in ~/.globus");
            final File usercert = guessCertFromDirectory(dotGlobus);
            final File userkey = guessKeyFromDirectory(dotGlobus);
            final GlobusCredential credential =
                    getUnencryptedUsercert(usercert, userkey, print);
            NimbusCredential.setUnencryptedCredential(usercert.getAbsolutePath(),
                                                       userkey.getAbsolutePath());
            print.debugln("Using unencrypted ~/.globus credential\n");
            return credential;
        } catch (Exception e) {
            final String err = "Problem loading from ~/.globus:\n    " +
                    CommonUtil.recurseForSomeString(e);
            accumulatedErrors.append(err).append('\n');
            print.debugln(err);
            throw new Exception(accumulatedErrors.toString());
        }
    }

    private static String expandTilde(String directory) throws Exception {
        if (directory.startsWith("~")) {
            final String homedir = System.getProperty("user.home");
            if (homedir == null || homedir.trim().length() == 0) {
                throw new Exception("Need to replace tilde to look for X509 credential, but " +
                        "cannot determine your user home directory");
            }
            return directory.replaceFirst("~", homedir);
        } else {
            return directory;
        }
    }

    private static File guessCertFromDirectory(String directory) {
        return new File(directory, "usercert.pem");
    }

    private static File guessKeyFromDirectory(String directory) {
        return new File(directory, "userkey.pem");
    }

    private static GlobusCredential getUnencryptedUsercert(File usercert,
                                                           File userkey,
                                                           Print print) throws Exception {

        final String userkeyPath = userkey.getAbsolutePath();
        final String usercertPath = usercert.getAbsolutePath();
        
        if (userkey.exists()) {
            print.debugln("Exists: " + userkeyPath);
        } else {
            final String err = "Does not exist: " + userkeyPath;
            print.debugln(err);
            throw new Exception(err);
        }
        if (usercert.exists()) {
            print.debugln("Exists: " + usercertPath);
        } else {
            final String err = "Does not exist: " + usercertPath;
            print.debugln(err);
            throw new Exception(err);
        }

        final GlobusCredential credential;
        try {
            credential = new GlobusCredential(usercertPath, userkeyPath);
        } catch (Exception e) {
            final String err = "Problem loading " + usercertPath + ":\n    " +
                                    CommonUtil.recurseForSomeString(e);
            print.debugln(err);
            throw new Exception(err);
        }
        return credential;
    }
    
    private static void proxyInformation(GlobusCredential credential, Print print)
            throws GlobusCredentialException {

        // this cert is already verified once
        print.debugln("    Identity: " + credential.getIdentity());
        print.debugln("    Subject: " + credential.getSubject());
        print.debugln("    Time Left: " + Util.formatTimeSec(credential.getTimeLeft()));
        print.debugln("\n");
    }
}
