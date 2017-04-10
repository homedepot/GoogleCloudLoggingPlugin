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

package org.jenkinsci.plugins.googlecloudlogging.constants;

public class GoogleCloudLoggingConstants {

    /** Tags from the Jenkins environment */
    public static final String BUILD_TAG = "${BUILD_TAG}";
    public static final String JOB_NAME = "${JOB_NAME}";
    public static final String BUILD_NUMBER = "${BUILD_NUMBER}";
    public static final String BUILD_URL = "${BUILD_URL}";
    public static final String BUILD_TS = "${BUILD_TIMESTAMP}";
    public static final String JENKINS_URL = "${JENKINS_URL}";
    public static final String EXECUTOR_NUMBER = "${EXECUTOR_NUMBER}";
    public static final String WORKSPACE = "${WORKSPACE}";
    public static final String GIT_COMMIT = "${GIT_COMMIT}";
    public static final String GIT_URL = "${GIT_URL}";
    public static final String GIT_BRANCH = "${GIT_BRANCH}";

    /** Query text to replace with values from Jenkins environment */
    public static final String QUERY_TEXT = "SELECT '%1$s' as build_tag," +
            " '%2$s' as job_name," +
            " '%3$s' as build_number," +
            " TIMESTAMP('%4$s') as build_start_ts," +
            " CURRENT_TIMESTAMP() as build_end_ts," +
            " TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), TIMESTAMP('%5$s'), SECOND) as build_duration, " +
            " '%6$s' as build_result," +
            " '%7$s' as build_url," +
            " '%8$s' as jenkins_url," +
            " '%9$s' as executor_number," +
            " '%10$s' as WORKSPACE," +
            " '%11$s' as params," +
            " '%12$s' as git_commit," +
            " '%13$s' as git_url," +
            " '%14$s' as git_branch," +
            " '%15$s' as upstream_url," +
            " '%16$s' as upstream_build_number," +
            " '%17$s' as upstream_project," +
            " %18$s as pipeline";

    /** Miscellaneous constants */
    public static final String WRITE_DISPOSITION = "WRITE_APPEND";
    public static final String APPLICATION_NAME = "BigQueryLogging";
}
