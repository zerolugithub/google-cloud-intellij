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
import com.google.cloud.tools.intellij.resources.GoogleApiClientFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class DefaultAppEngineAdminService extends AppEngineAdminService {

  @Override
  @Nullable
  public Application getApplicationForProjectId(@NotNull String projectId,
      @NotNull Credential credential) throws IOException {
    try {
      Appengine appEngineApiClient
          = GoogleApiClientFactory.getAppEngineApiClient(credential);
      return appEngineApiClient.apps().get(projectId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      // TODO any other special cases we should handle?
      throw e;
    }
  }
}
