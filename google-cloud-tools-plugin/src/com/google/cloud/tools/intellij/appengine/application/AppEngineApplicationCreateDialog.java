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

package com.google.cloud.tools.intellij.appengine.application;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.appengine.v1.model.Application;
import com.google.api.services.appengine.v1.model.Location;
import com.google.cloud.tools.intellij.appengine.cloud.AppEngineOperationFailedException;
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
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;

public class AppEngineApplicationCreateDialog extends DialogWrapper {

  private final static String STANDARD_ENV_AVAILABLE_KEY = "standardEnvironmentAvailable";
  private final static String FLEXIBLE_ENV_AVAILABLE_KEY = "flexibleEnvironmentAvailable";
  private final static String DOCUMENTATION_URL
      = "https://cloud.google.com/docs/geography-and-regions";

  private JPanel panel;
  private JTextPane instructionsTextPane;
  private JComboBox<Location> regionComboBox;
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

    regionComboBox.setRenderer(new AppEngineRegionComboBoxRenderer());
    // TODO link handler
    instructionsTextPane.setText(GctBundle.message("appengine.application.create.instructions")
        + "<p>"
        + GctBundle.message("appengine.application.create.documentation",
        "<a href=\"" + DOCUMENTATION_URL + "\">", "</a>")
        + "</p>");
  }

  @Override
  protected void doOKAction() {
    Location selectedLocation = (Location) regionComboBox.getSelectedItem();
    final String locationId = getLocationId(selectedLocation);

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
          setStatusMessageAsync(GctBundle.message("appengine.application.create.error"), true);
          setOKActionEnabled(true);
          return;

        } catch (AppEngineOperationFailedException e) {
          setStatusMessageAsync(e.getStatus().getMessage(), true);
          setOKActionEnabled(true);
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

  private void setStatusMessageAsync(final String message, final boolean isError) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        setStatusMessage(message, isError);

      }
    });
  }

  private void setStatusMessage(String message, boolean isError) {
    statusPane.setText(message);
    statusPane.setForeground(isError ? JBColor.red : JBColor.black);
    statusPane.setVisible(true);
  }

  private String getLocationId(Location location) {
    // TODO(alexsloan) see if b/33458530 will be fixed and we can remove this
    return location.getLabels().get("cloud.googleapis.com/region");
  }

  private void refreshLocationsSelector() {
    List<Location> appEngineRegions;
    try {
      appEngineRegions = AppEngineAdminService.getInstance().getAllAppEngineLocations(userCredential);
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

  /**
   * Interface that defines a listener that can be notified when new Applications are created.
   */
  public interface ApplicationCreatedListener {
    void onApplicationCreated(Application application);
  }

  // Renders the list items in the location dropdown menu
  private class AppEngineRegionComboBoxRenderer extends JLabel implements
      ListCellRenderer<Location> {

    @Override
    public Component getListCellRendererComponent(JList<? extends Location> list, Location value,
        int index, boolean isSelected, boolean cellHasFocus) {

      // TODO consider right-padding for tabular format
      String displayText = getLocationId(value);

      boolean isStandardSupported
          = parseMetatadaBoolean(STANDARD_ENV_AVAILABLE_KEY, value.getMetadata());
      boolean isFlexSupported
          = parseMetatadaBoolean(FLEXIBLE_ENV_AVAILABLE_KEY, value.getMetadata());

      if (isStandardSupported && isFlexSupported) {
        displayText += " (supports standard and flexible)";
      } else if (isStandardSupported) {
        displayText += " (supports standard)";
      } else if (isFlexSupported) {
        displayText += " (supports flexible)";
      }

      setText(displayText);
      return this;
    }

    private boolean parseMetatadaBoolean(String key, Map<String, Object> metadata) {
      if (!metadata.containsKey(key)) {
        return false;
      }
      Object val = metadata.get(key);
      if (val == null || !(val instanceof Boolean)) {
        return false;
      }
      return (Boolean) val;
    }
  }

}
