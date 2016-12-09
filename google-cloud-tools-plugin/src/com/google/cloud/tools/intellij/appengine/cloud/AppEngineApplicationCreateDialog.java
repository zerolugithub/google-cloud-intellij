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
import com.google.api.services.appengine.v1.model.Application;
import com.google.api.services.appengine.v1.model.Location;
import com.google.cloud.tools.intellij.util.GctBundle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;

public class AppEngineApplicationCreateDialog extends DialogWrapper {

  private JPanel panel;
  private JTextPane instructionsTextPane;
  private JComboBox<Location> regionComboBox;
  private JLabel regionText;
  private JTextPane statusPane;

  private Component parent;
  private Credential userCredential;
  private String gcpProjectId;
  private List<ApplicationCreatedListener> applicationCreatedListeners;

  public AppEngineApplicationCreateDialog(@NotNull Component parent, @NotNull String gcpProjectId,
      @NotNull Credential userCredential) {
    super(parent, false);

    this.parent = parent;
    this.gcpProjectId = gcpProjectId;
    this.userCredential = userCredential;
    this.applicationCreatedListeners = new ArrayList<>();

    init();
    setTitle(GctBundle.message("appengine.application.region.select"));
    refreshLocationsSelector();
  }

  @Override
  protected void doOKAction() {
    Location selectedLocation = (Location) regionComboBox.getSelectedItem();

    // TODO see if bug will be fixed and can use selectedLocation.getLocationId() instead
    final String locationId = selectedLocation.getLabels().get("cloud.googleapis.com/region");

    // show loading state
    setOKActionEnabled(false);
    setStatusMessage(GctBundle.message(
        "appengine.application.create.loading", selectedLocation.getName()), false);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        Application result;
        try {
          result = AppEngineAdminService.getInstance().createApplication(
              locationId, gcpProjectId, userCredential);
        } catch (IOException e) {
          setStatusMessage(GctBundle.message("appengine.application.create.error"), true);
          return;
        } catch (InterruptedException e) {
          // TODO different error message saying that the operation is probably in progress?
          return;
        }

        for (ApplicationCreatedListener listener : applicationCreatedListeners) {
          listener.onApplicationCreated(result);
        }

        // Defer to the dispatch thread to invoke the action of closing this dialog
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            AppEngineApplicationCreateDialog.this.close(OK_EXIT_CODE);
          }
        });
      }
    });
  }

  public void addApplicationCreatedListener(ApplicationCreatedListener listener) {
    applicationCreatedListeners.add(listener);
  }

  public void removeApplicationCreatedListener(ApplicationCreatedListener listener) {
    applicationCreatedListeners.remove(listener);
  }

  private void setStatusMessage(String message, boolean isError) {
    statusPane.setText(message);
    statusPane.setForeground(isError ? JBColor.red : JBColor.black);
    statusPane.setVisible(true);
  }

  private void refreshLocationsSelector() {
    List<Location> appEngineRegions;
    try {
      appEngineRegions = AppEngineAdminService.getInstance().getAllAppEngineRegions(userCredential);
    } catch (IOException e) {
      setStatusMessage(GctBundle.message("appengine.application.region.list.fetch.error"), true);
      return;
    }
    regionComboBox.removeAllItems();
    for (Location location : appEngineRegions) {
      regionComboBox.addItem(location);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  interface ApplicationCreatedListener {
    void onApplicationCreated(Application application);
  }

}
