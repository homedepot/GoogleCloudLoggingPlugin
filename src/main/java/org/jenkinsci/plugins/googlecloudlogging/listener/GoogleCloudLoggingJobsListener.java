/*
Copyright 2017 The Home Depot

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.jenkinsci.plugins.googlecloudlogging.listener;

import com.jmethods.catatumbo.EntityManager;
import com.jmethods.catatumbo.EntityManagerFactory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.PluginManager;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.googlecloudlogging.constants.GoogleCloudLoggingConstants;
import org.jenkinsci.plugins.googlecloudlogging.entities.JenkinsBuild;
import org.jenkinsci.plugins.googlecloudlogging.manager.BigQueryManager;
import org.jenkinsci.plugins.googlecloudlogging.plugin.GoogleCloudLoggingPlugin;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class GoogleCloudLoggingJobsListener extends RunListener<Run> {

    private static GoogleCloudLoggingPlugin plugin;
    private static EntityManager em;
    private final static Logger LOGGER = Logger.getLogger(GoogleCloudLoggingJobsListener.class.getName());
    private static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    public GoogleCloudLoggingJobsListener() {
        super(Run.class);
        LOGGER.log(Level.SEVERE, "Starting to load Google Cloud Logging Listener");
        Jenkins jenkins = Jenkins.getInstance();
        PluginManager pm = jenkins.getPluginManager();
        plugin = (GoogleCloudLoggingPlugin) pm.getPlugin(GoogleCloudLoggingPlugin.class).getPlugin();

        EntityManagerFactory emf = EntityManagerFactory.getInstance();
        em = emf.createDefaultEntityManager();
    }

    /**
     * If the parameter is empty, then the value will be the same as the key so it should match the ${ pattern
     **/
    private static String checkForEmpty(String input) {
        return (input.contains("${")) ? "" : input;
    }

    /**
     * After the execution of any build in jenkins, the onCompleted is call the build to log to Google.
     *
     * @param build current build executed in Jenkins from the environment.
     * @param listener Task listener to print out with
     *
     * @throws RuntimeException
     */
    @Override
    public void onCompleted(Run build, TaskListener listener) throws RuntimeException {
        super.onCompleted(build, listener);

        try {
            EnvVars env = build.getEnvironment(listener);

            String upstreamUrl = "";
            String upstreamBuildNum = "";
            String upstreamProject = "";
            boolean isPipeline = false;

            // Loop through build actions and get the upstream url, build number, and project if available
            for (final CauseAction action : build.getActions(CauseAction.class)) {
                for (final Cause cause : action.getCauses()) {
                    if (!(cause instanceof Cause.UpstreamCause)) {
                        continue;
                    }

                    final Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;

                    upstreamUrl = upstreamCause.getUpstreamUrl();
                    upstreamBuildNum = String.valueOf(upstreamCause.getUpstreamBuild());
                    upstreamProject = upstreamCause.getUpstreamProject();
                }
            }

            // Build params list
            String params = "";
            Map<String, String> varMap = build.getEnvironment(listener);

            // Check for sensitive build vars and mask them
            Set<String> sensVarMap = getSensitiveBuildVariables(build);
            for (Map.Entry<String, String> variable : varMap.entrySet()) {
                if (!params.equals("")) {
                    params += "&";
                }

                if (sensVarMap.contains(variable.getKey())) {
                    params += "Key:" + variable.getKey() + ", Value: ********";
                } else {
                    params += "Key:" + variable.getKey() + ", Value: " + variable.getValue().replace("'", "\\'");
                }
            }

            Result buildResult = build.getResult();

            // Expand Env Strings
            String buildTagExpanded = env.expand(GoogleCloudLoggingConstants.BUILD_TAG);
            String jobNameExpanded = env.expand(GoogleCloudLoggingConstants.JOB_NAME);
            String buildNumberExpanded = env.expand(GoogleCloudLoggingConstants.BUILD_NUMBER);
            String buildURLExpanded = env.expand(GoogleCloudLoggingConstants.BUILD_URL);
            String buildTSExpanded = env.expand(GoogleCloudLoggingConstants.BUILD_TS);
            String jenkinsURLExpanded = env.expand(GoogleCloudLoggingConstants.JENKINS_URL);
            String executorNumberExpanded = env.expand(GoogleCloudLoggingConstants.EXECUTOR_NUMBER);
            String workspaceExpanded = env.expand(GoogleCloudLoggingConstants.WORKSPACE);
            String gitCommitExpanded = env.expand(GoogleCloudLoggingConstants.GIT_COMMIT);
            String gitURLExpanded = env.expand(GoogleCloudLoggingConstants.GIT_URL);
            String gitBranchExpanded = env.expand(GoogleCloudLoggingConstants.GIT_BRANCH);

            // Check for empty build tags and set to empty strings
            if (buildTagExpanded.contains("${")) {
                throw new Exception("Build Tag from Jenkins came empty.");
            }

            buildTagExpanded = checkForEmpty(buildTagExpanded);
            jobNameExpanded = checkForEmpty(jobNameExpanded);
            buildNumberExpanded = checkForEmpty(buildNumberExpanded);
            buildURLExpanded = checkForEmpty(buildURLExpanded);
            buildTSExpanded = checkForEmpty(buildTSExpanded);
            jenkinsURLExpanded = checkForEmpty(jenkinsURLExpanded);
            executorNumberExpanded = checkForEmpty(executorNumberExpanded);
            workspaceExpanded = checkForEmpty(workspaceExpanded);
            gitCommitExpanded = checkForEmpty(gitCommitExpanded);
            gitURLExpanded = checkForEmpty(gitURLExpanded);
            gitBranchExpanded = checkForEmpty(gitBranchExpanded);

            // Check if build is a pipeline
            if (build instanceof WorkflowRun) {
                isPipeline = true;
            }

            // Check if Datstore logging is enabled and log out to Datastore if so
            if (plugin.isEnableDatastore()){
                logToConsole(listener, "Logging Job Details to Datastore");

                JenkinsBuild jenkinsBuild = new JenkinsBuild(buildTagExpanded, jobNameExpanded, buildNumberExpanded, DF.parse(buildTSExpanded), Calendar.getInstance().getTime(), buildResult.toString(),
                        buildURLExpanded, jenkinsURLExpanded, executorNumberExpanded, workspaceExpanded, params, gitCommitExpanded, gitURLExpanded, gitBranchExpanded,
                        upstreamUrl, upstreamBuildNum, upstreamProject, isPipeline);

                em.upsert(jenkinsBuild);

                logToConsole(listener, "Datastore Logging Successful");

            }

            // Check if BigQuery logging is enabled and log out to BigQuery if so
            if (plugin.isEnableBigQuery()){
                            // Build Query SQL
            String querySql = String.format(GoogleCloudLoggingConstants.QUERY_TEXT,
                    buildTagExpanded, jobNameExpanded, buildNumberExpanded, buildTSExpanded, buildTSExpanded, buildResult.toString(),
                    buildURLExpanded, jenkinsURLExpanded, executorNumberExpanded, workspaceExpanded, params, gitCommitExpanded, gitURLExpanded, gitBranchExpanded,
                    upstreamUrl, upstreamBuildNum, upstreamProject, String.valueOf(isPipeline));

            logToConsole(listener, "Logging Job Details to BigQuery");
            logToConsole(listener, "Query Used in Logging Job Details to BigQuery :: " + querySql);

            new BigQueryManager(plugin.getBqProject(),
                    plugin.getBqDataset(),
                    plugin.getBqTable(), querySql);

                logToConsole(listener, "BigQuery Logging Successful");

            }

            // If both logging types are disabled, alert in console that on logging was performed.
            if (!plugin.isEnableBigQuery() && !plugin.isEnableDatastore()){
                logToConsole(listener, "Both BigQuery and Datastore Logging Disabled, No Logging to Perform");
            }


        } catch (Exception e) {
            logToConsole(listener, "Google Cloud Logging Failed :: " + e.getMessage());
        }
    }

    /**
     * Prints a message out to the console of the Jenkins job.
     *
     * @param listener Task listener to print out with
     * @param message Message to print to console with
     */
    private static void logToConsole(TaskListener listener, String message){
        listener.getLogger().println("========\n" + message + "\n========");
    }

    /**
     * Gets a list of any build variables listed as sensitive within a run.
     *
     * @param build The run
     *
     * @return Set of all sensitive build variables for a build.
     */
    private static Set<String> getSensitiveBuildVariables(Run build) {
        HashSet s = new HashSet();
        ParametersAction parameters = build.getAction(ParametersAction.class);
        Iterator i$;
        if(parameters != null) {
            i$ = parameters.iterator();

            while(i$.hasNext()) {
                ParameterValue bw = (ParameterValue)i$.next();
                if(bw.isSensitive()) {
                    s.add(bw.getName());
                }
            }
        }

        return s;
    }
}
