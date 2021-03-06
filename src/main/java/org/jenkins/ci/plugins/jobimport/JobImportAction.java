/*
 * The MIT License
 * 
 * Copyright (c) 2011, Jesse Farinacci
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jenkins.ci.plugins.jobimport;

import hudson.model.Action;
import hudson.model.TopLevelItem;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.collect.Sets;

/**
 * @author <a href="mailto:jieryn@gmail.com">Jesse Farinacci</a>
 * @since 1.0
 */
public class JobImportAction implements Action {

  private static final Logger                               LOG                    = Logger
                                                                                       .getLogger(JobImportAction.class
                                                                                           .getName());
  final private JobImportContainer container;

  private String                                            remoteUrl;
  private String username, password;

  private final SortedSet<RemoteJob>                        remoteJobs             = new TreeSet<RemoteJob>();

  private final SortedMap<RemoteJob, RemoteJobImportStatus> remoteJobsImportStatus = new TreeMap<RemoteJob, RemoteJobImportStatus>();
  
  private String remoteJobsQueryStatus;
  
  public JobImportAction(JobImportContainer container) {
	  this.container = container;
  }
  
  public void doClear(final StaplerRequest request, final StaplerResponse response) throws ServletException,
      IOException {
    reset();
    response.sendRedirect(container.getUrl());
  }
  
  public void doReset(final StaplerRequest request, final StaplerResponse response) throws ServletException,
	  IOException {
	reset();
	response.sendRedirect(getAbsoluteUrl());
	}

  public void doImport(final StaplerRequest request, final StaplerResponse response) throws ServletException,
      IOException {
    remoteJobsImportStatus.clear();

    if (isRemoteJobsAvailable()) {
      if (request.hasParameter("jobUrl")) {
        for (final String jobUrl : Arrays.asList(request.getParameterValues("jobUrl"))) {
        	RemoteJob remoteJob = null;
        	if(StringUtils.isNotEmpty(jobUrl)){
        		remoteJob = findRemoteJob(jobUrl, remoteJobs);
        	}
          if (remoteJob != null) {
            if (!remoteJobsImportStatus.containsKey(remoteJob)) {
              remoteJobsImportStatus.put(remoteJob, new RemoteJobImportStatus(remoteJob));
            }

            // ---
            
            if (container.hasJob(remoteJob.getName())) {
              remoteJobsImportStatus.get(remoteJob).setStatus(MessagesUtils.formatFailedDuplicateJobName());
            }

            else {
              InputStream inputStream = null;

              try {
                  inputStream = URLUtils.fetchUrl(remoteJob.getUrl() + "/config.xml", username, password);
                  container.createProjectFromXML(remoteJob.getName(), inputStream);
                  remoteJobsImportStatus.get(remoteJob).setStatus(MessagesUtils.formatSuccess());
              }

              catch (final Exception e) {
                LOG.warning("Job Import Failed: " + e.getMessage());
                if (LOG.isLoggable(Level.INFO)) {
                  LOG.log(Level.INFO, e.getMessage(), e);
                }

                // ---

                remoteJobsImportStatus.get(remoteJob).setStatus(MessagesUtils.formatFailedException(e));

                try {
                    TopLevelItem created = container.getJob(remoteJob.getName());
                    if (created != null) {
                        created.delete();
                    }
                }
                catch (final InterruptedException e2) {
                  // do nothing
                }
              }

              finally {
                IOUtils.closeQuietly(inputStream);
              }
            }
          }
        }
      }
    }

    response.forwardToPreviousPage(request);
  }

  public void doQuery(final StaplerRequest request, final StaplerResponse response) throws ServletException,
      IOException {
    remoteJobs.clear();
    remoteJobsImportStatus.clear();
    remoteJobsQueryStatus = null;
    remoteUrl = request.getParameter("remoteUrl");
    username = request.getParameter("username");
    password = request.getParameter("password");
    remoteJobs.addAll(getAllJobs(remoteUrl));
    response.forwardToPreviousPage(request);
  }
  private static String text(Element e, String name) {
      NodeList nl = e.getElementsByTagName(name);
      if (nl.getLength() == 1) {
          Element e2 = (Element) nl.item(0);
          return e2.getTextContent();
      } else {
          return null;
      }
  }

  public FormValidation doTestConnection(@QueryParameter("remoteUrl") final String remoteUrl) {
    return FormValidation.ok();
  }

  public String getDisplayName() {
    return Messages.DisplayName();
  }

  public String getIconFileName() {
    return "/images/32x32/setting.png";
  }

  public SortedSet<RemoteJob> getRemoteJobs() {
    return remoteJobs;
  }
  
  private RemoteJob findRemoteJob(final String jobUrl, final SortedSet<RemoteJob> jobs) {
	  for(RemoteJob childJob : jobs) {
		  if(jobUrl.equals(childJob.getUrl())) {
			  return childJob;
		  }
		  RemoteJob result = findRemoteJob(jobUrl, childJob.getJobs());
		  if(result!=null) return result;
	  }
	  return null;
  }

  public SortedMap<RemoteJob, RemoteJobImportStatus> getRemoteJobsImportStatus() {
    return remoteJobsImportStatus;
  }
  
  public String getRemoteJobsQueryStatus() {
	  return remoteJobsQueryStatus;
  }

  public String getRemoteUrl() {
    return remoteUrl;
  }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }

  public String getUrlName() {
    return "job-import";
  }
  
  protected String getAbsoluteUrl() {
	  return container.getUrl() + getUrlName();
  }

  public boolean isRemoteJobsAvailable() {
    return remoteJobs.size() > 0;
  }

  public boolean isRemoteJobsImportStatusAvailable() {
    return remoteJobsImportStatus.size() > 0;
  }
  
  public boolean isRemoteJobsQueryStatusAvailable() {
	  return remoteJobsQueryStatus != null;
  }

  public void setRemoteUrl(final String remoteUrl) {
    this.remoteUrl = remoteUrl;
  }
  
  protected void reset() {
	remoteUrl = null;
    username = password = null;
    remoteJobs.clear();
    remoteJobsImportStatus.clear();
    remoteJobsQueryStatus = null;
  }
  
  
  	/**
  	 * Method that will retrieve all remote jobs recursively.
  	 * @param remoteURL
  	 * @param remoteJobs
  	 */
  	public SortedSet<RemoteJob> getAllJobs(String remoteURL) {
  		try{
			return getAllJobsImpl(remoteURL, null);
		} catch(Exception e){
			e.printStackTrace();
			remoteJobsQueryStatus = MessagesUtils.formatQueryFailedException(e);
		}
  		return Sets.newTreeSet();
  	}
  	
  	/**
  	 * @param remoteURL
  	 * @param parent
  	 * @return
  	 * @throws Exception
  	 */
  	private SortedSet<RemoteJob> getAllJobsImpl(String remoteURL, RemoteJob parent) throws Exception {
  		SortedSet<RemoteJob> remoteJobs = new TreeSet<RemoteJob>();
  		if(!remoteURL.endsWith("/")) remoteURL = remoteURL + "/";
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(URLUtils.fetchUrl(remoteURL + "api/xml?tree=jobs[name,url,description]",
						username, password));
		List<RemoteJob> result = fromNodeListToList(doc.getElementsByTagName("job"));
		boolean parentIsFolder = doc.getElementsByTagName("folder").getLength() > 0;
		boolean allJobsHidden = true;
		for(RemoteJob remoteJob : result){
			remoteJobs.add(remoteJob);
			String newRemoteURL = remoteJob.getUrl();
			remoteJob.setJobs(getAllJobsImpl(newRemoteURL, remoteJob));
			allJobsHidden = allJobsHidden && remoteJob.isHidden();
		}
		if (parent != null)
			parent.setHidden(parentIsFolder && allJobsHidden);
		
		return remoteJobs;
  	}
	
	/**
	 * transform from {@link NodeList} to a {@link List} of {@link RemoteJob}s
	 */
	private static List<RemoteJob> fromNodeListToList(NodeList nl){
		List<RemoteJob> result = new ArrayList<RemoteJob>();
		if(nl == null) return result;
		for (int i = 0; i < nl.getLength(); i++) {
			Element job = (Element) nl.item(i);
			String description = text(job, "description");
			RemoteJob entry = new RemoteJob(text(job, "name"),
					text(job, "url"), description == null ? "" : description);
			result.add(entry);
		}
		return result;
	}
  
}
