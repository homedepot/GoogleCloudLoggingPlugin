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

package org.jenkinsci.plugins.googlecloudlogging.manager;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.*;
import com.google.api.services.bigquery.Bigquery.Jobs.Insert;
import org.jenkinsci.plugins.googlecloudlogging.constants.GoogleCloudLoggingConstants;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BigQueryManager {

  private static final Logger LOGGER = Logger.getLogger(BigQueryManager.class.getName());

  /**
   * Creates an authorized BigQuery builder using the Application Default Credentials.
   *
   * @return an authorized BigQuery builder
   *
   * @throws IOException
   */
  private static Bigquery createAuthorizedClient() throws IOException {
    HttpTransport transport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    GoogleCredential credential = GoogleCredential.getApplicationDefault(transport, jsonFactory);

    if (credential.createScopedRequired()) {
      credential = credential.createScoped(BigqueryScopes.all());
    }

    return new Bigquery.Builder(transport, jsonFactory, credential)
        .setApplicationName(GoogleCloudLoggingConstants.APPLICATION_NAME)
        .build();
  }

  /**
   * Checks the results of a BigQuery submitted SQL and allows for retry logic
   *
   * @param bigquery authorized BigQuery client
   * @param projectId BigQuery Project ID to Load to
   * @param jobId Job ID returned when a query is submitted to BigQuery
   *
   * @return a pollJob if there are no errors otherwise null to continue with retry logic
   *
   * @throws IOException
   * @throws InterruptedException
   */
  private static boolean checkQueryResults(Bigquery bigquery, String projectId, JobReference jobId)
          throws IOException, InterruptedException {

    // Variables to keep track of total query time
    long startTime = System.currentTimeMillis();
    long elapsedTime;
    int attempts = 0;

    while (attempts < 10) {
      Job pollJob = bigquery.jobs().get(projectId, jobId.getJobId()).execute();
      elapsedTime = System.currentTimeMillis() - startTime;
      LOGGER.log(Level.INFO, String.format("Job status (%dms) %s: %s\n", elapsedTime,
             jobId.getJobId(), pollJob.getStatus().getState()));
      if (pollJob.getStatus().getState().equals("DONE") && pollJob.getStatus().getErrorResult() == null) {
           return false;
      } else if (pollJob.getStatus().getState().equals("DONE") && pollJob.getStatus().getErrorResult() != null) {
        return true;
      }
      attempts++;
      // Pause execution for one second before polling job status again, to
      // reduce unnecessary calls to the BigQuery API and lower overall
      // application bandwidth.
      Thread.sleep(1000);
      
    }
      return false;
  }

  /**
   * Runs a query and loads the result set into a table.
   *
   * @param bigquery authorized BigQuery client
   * @param projectId BigQuery Project ID to Load to
   * @param datasetId BigQuery Dataset to Load to
   * @param tableId BigQuery table to Load to
   * @param querySql BigQuery SQL to run
   *
   * @return Job ID returned when a query is started
   *
   * @throws IOException
   */

  private static JobReference startQuery(Bigquery bigquery, String projectId, String datasetId, String tableId,
                                         String querySql) throws IOException {
    LOGGER.log(Level.INFO, String.format("\nInserting Query Job: %s\n", querySql));

    Job job = new Job();
    JobConfiguration config = new JobConfiguration();
    JobConfigurationQuery queryConfig = new JobConfigurationQuery();
    TableReference tr = new TableReference();
    tr.setProjectId(projectId);
    tr.setDatasetId(datasetId);
    tr.setTableId(tableId);

    queryConfig.setQuery(querySql);
    queryConfig.setDestinationTable(tr);
    queryConfig.setUseLegacySql(false);
    queryConfig.setWriteDisposition(GoogleCloudLoggingConstants.WRITE_DISPOSITION);
    config.setQuery(queryConfig);
    job.setConfiguration(config);

    Insert insert = bigquery.jobs().insert(projectId, job);
    insert.setProjectId(projectId);
    JobReference jobId = insert.execute().getJobReference();

    LOGGER.log(Level.INFO, String.format("\nJob ID of Query Job is: %s\n", jobId.getJobId()));

    return jobId;
  }

    /**
     * Method used to orchestrate running a query and retrying it a limited number of times.
     *
     * @param projectId BigQuery Project ID to Load to
     * @param datasetId BigQuery Dataset to Load to
     * @param tableId BigQuery table to Load to
     * @param querySql BigQuery SQL to run
     *
     * @throws IOException
     */
    public BigQueryManager(String projectId, String datasetId, String tableId, String querySql) throws IOException {
    boolean retryJob = true;
    int tries = 0;

    // Create a new BigQuery client authorized via Application Default Credentials.
    Bigquery bigquery = createAuthorizedClient();

    while (retryJob && tries <= 4){
        tries++;
      try{
        JobReference jobId = startQuery(bigquery, projectId, datasetId, tableId, querySql);
        retryJob = checkQueryResults(bigquery, projectId, jobId);
      } catch (IOException e) {
          LOGGER.log(Level.WARNING, "IOException caught while writing to BigQuery : " + e.getMessage());
      } catch (InterruptedException e) {
          LOGGER.log(Level.WARNING, "InterruptedException caught while writing to BigQuery : " + e.getMessage());
        // Jenkins throws an interrupted exception when the stop button is clicked.
        Thread.currentThread().interrupt();
      }
    }

  }
}
