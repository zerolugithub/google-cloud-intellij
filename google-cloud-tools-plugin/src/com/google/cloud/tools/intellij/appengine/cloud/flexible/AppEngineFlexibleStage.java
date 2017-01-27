/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.intellij.appengine.cloud.flexible;

import com.google.cloud.tools.intellij.appengine.cloud.AppEngineDeploymentConfiguration;
import com.google.cloud.tools.intellij.appengine.cloud.CloudSdkAppEngineHelper;
import com.google.cloud.tools.intellij.appengine.project.AppEngineProjectService;
import com.google.cloud.tools.intellij.appengine.project.AppEngineProjectService.FlexibleRuntime;
import com.google.cloud.tools.intellij.util.GctBundle;

import com.intellij.remoteServer.runtime.log.LoggingHandler;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stages an application in preparation for deployment to the App Engine flexible environment.
 */
public class AppEngineFlexibleStage {
  private CloudSdkAppEngineHelper helper;
  private LoggingHandler loggingHandler;
  private Path deploymentArtifactPath;
  private AppEngineDeploymentConfiguration deploymentConfiguration;

  /**
   * Initialize the staging dependencies.
   */
  public AppEngineFlexibleStage(
      @NotNull CloudSdkAppEngineHelper helper,
      @NotNull LoggingHandler loggingHandler,
      @NotNull Path deploymentArtifactPath,
      @NotNull AppEngineDeploymentConfiguration deploymentConfiguration) {
    this.helper = helper;
    this.loggingHandler = loggingHandler;
    this.deploymentArtifactPath = deploymentArtifactPath;
    this.deploymentConfiguration = deploymentConfiguration;
  }

  /**
   * Given a local staging directory, stage the application in preparation for deployment to the
   * App Engine flexible environment.
   */
  public void stage(@NotNull Path stagingDirectory) {
    try {
      // Checks if the Yaml or Dockerfile exist before staging.
      // This should only happen in special circumstances, since the deployment UI prevents the
      // run config from being ran is the specified configuration files don't exist.
      FlexibleRuntime runtime =
          AppEngineProjectService.getInstance().getFlexibleRuntimeFromAppYaml(
              deploymentConfiguration.getAppYamlPath());
      if (deploymentConfiguration.isCustom()) {
        if (!Files.exists(Paths.get(deploymentConfiguration.getAppYamlPath()))) {
          throw new RuntimeException(
              GctBundle.getString("appengine.deployment.error.staging.yaml"));
        }
        if (runtime == FlexibleRuntime.CUSTOM
            && !Files.exists(Paths.get(deploymentConfiguration.getDockerFilePath()))) {
          throw new RuntimeException(
              GctBundle.getString("appengine.deployment.error.staging.dockerfile"));
        }
      }

      Path stagedArtifactPath = stagingDirectory.resolve(
          "target" + AppEngineFlexibleDeploymentArtifactType.typeForPath(deploymentArtifactPath));
      Files.copy(deploymentArtifactPath, stagedArtifactPath);

      Path appYamlPath = deploymentConfiguration.isAuto()
          ? helper.defaultAppYaml().get()
          : Paths.get(deploymentConfiguration.getAppYamlPath());
      Files.copy(appYamlPath, stagingDirectory.resolve(appYamlPath.getFileName()));

      if (runtime == FlexibleRuntime.CUSTOM) {
        Path dockerFilePath =
            deploymentConfiguration.isAuto()
                ? helper
                    .defaultDockerfile(
                        AppEngineFlexibleDeploymentArtifactType.typeForPath(deploymentArtifactPath))
                    .orElseThrow(
                        () ->
                            new RuntimeException(
                                GctBundle.getString(
                                    "appengine.deployment.error.deployable.notjarorwar")))
                : Paths.get(deploymentConfiguration.getDockerFilePath());
        Files.copy(dockerFilePath, stagingDirectory.resolve(dockerFilePath.getFileName()));
      }
    } catch (IOException | InvalidPathException ex) {
      loggingHandler.print(ex.getMessage() + "\n");
      throw new RuntimeException(ex);
    }
  }
}