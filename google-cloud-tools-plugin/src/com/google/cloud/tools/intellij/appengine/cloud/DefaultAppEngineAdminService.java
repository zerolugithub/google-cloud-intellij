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

package com.google.cloud.tools.intellij.appengine.cloud;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.appengine.v1.Appengine;
import com.google.api.services.appengine.v1.model.Application;
import com.google.api.services.appengine.v1.model.ListLocationsResponse;
import com.google.api.services.appengine.v1.model.Location;
import com.google.api.services.appengine.v1.model.Operation;
import com.google.cloud.tools.intellij.resources.GoogleApiClientFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultAppEngineAdminService extends AppEngineAdminService {

  private final static String APP_ENGINE_RESOURCE_WILDCARD = "-";

  @Override
  @Nullable
  public Application getApplicationForProjectId(@NotNull String projectId,
      @NotNull Credential credential) throws IOException {
    try {
      return GoogleApiClientFactory.getAppEngineApiClient(credential)
          .apps().get(projectId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      // TODO any other special cases we should handle?
      throw e;
    }
  }

  @Override
  public ListenableFuture<Application> createApplicationForProjectId(@NotNull final Application
      application, @NotNull final String projectId, @NotNull final Credential credential)
      throws IOException {

    // TODO use an executor that's configured elsewhere
    // TODO perhaps can overload this method call to optionally provide another executorService
    ExecutorService executor = Executors.newSingleThreadExecutor();

    return MoreExecutors.listeningDecorator(executor).submit(new Callable<Application>() {
      @Override
      public Application call() throws Exception {
        Operation operation = GoogleApiClientFactory.getAppEngineApiClient(credential)
            .apps().create(application).execute();

        boolean done = false;
        do {
          // TODO handle interrupt? Is there a recommended polling interval?
          Thread.sleep(500);
          operation = getOperation(projectId, operation.getName(), credential);
          if (operation.getDone() != null) {
            done = operation.getDone();
          }
        } while (!done);

        if (operation.getError() != null) {
          // TODO throw exception
          return null;
        } else {
          // TODO parse response and return Application
          return null;
        }
      }

    });
  }

  private Operation getOperation(String projectId, String operationName, Credential credential)
      throws IOException {
    return GoogleApiClientFactory.getAppEngineApiClient(credential).apps()
        .operations().get(projectId, operationName).execute();
  }

  @Override
  public List<Location> getAllAppEngineRegions(Credential credential) throws IOException {
    ListLocationsResponse response = GoogleApiClientFactory.getAppEngineApiClient(credential)
        .apps().locations().list(APP_ENGINE_RESOURCE_WILDCARD).execute();

    return response.getLocations();
  }

}
