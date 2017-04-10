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

package org.jenkinsci.plugins.googlecloudlogging.plugin;

import hudson.Extension;
import hudson.Plugin;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class GoogleCloudLoggingPlugin extends Plugin {
    private final static Logger LOGGER = Logger.getLogger(GoogleCloudLoggingPlugin.class.getName());
    private boolean enableBigQuery;
    private String bqProject;
    private String bqDataset;
    private String bqTable;
    private boolean enableDatastore;


    @Override
    public void configure(StaplerRequest req, JSONObject formData)
    {
        enableBigQuery = formData.optBoolean("enableBigQuery", false);

        bqProject = formData.optString("bqProject", "yourProject");
        bqDataset = formData.optString("bqDataset", "yourDataset");
        bqTable = formData.optString("bqTable", "yourTable");

        enableDatastore = formData.optBoolean("enableDatastore", false); //data part, ain't chipolte

        try {
            save();
        } catch (IOException e)
        {
            LOGGER.log(Level.SEVERE, "Error Reading Google Cloud Logging Settings :: " + e.getMessage());
        }
    }

    @Override
    public void start()
    {
        try {
            load();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting Google Cloud Logging :: " + e.getMessage());
        }
    }

    public boolean isEnableBigQuery() {
        return enableBigQuery;
    }

    public void setEnableBigQuery(boolean enableBigQuery) {
        this.enableBigQuery = enableBigQuery;
    }

    public String getBqProject() {
        return bqProject;
    }

    public void setBqProject(String bqProject) {
        this.bqProject = bqProject;
    }

    public String getBqDataset() {
        return bqDataset;
    }

    public void setBqDataset(String bqDataset) {
        this.bqDataset = bqDataset;
    }

    public String getBqTable() {
        return bqTable;
    }

    public void setBqTable(String bqTable) {
        this.bqTable = bqTable;
    }

    public boolean isEnableDatastore() {
        return enableDatastore;
    }

    public void setEnableDatastore(boolean enableDatastore) {
        this.enableDatastore = enableDatastore;
    }
}
