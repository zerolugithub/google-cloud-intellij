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

import com.google.cloud.tools.intellij.appengine.cloud.AppEngineApplicationInfoPanel;
import com.google.cloud.tools.intellij.appengine.cloud.AppEngineArtifactDeploymentSource;
import com.google.cloud.tools.intellij.appengine.cloud.AppEngineDeployable;
import com.google.cloud.tools.intellij.appengine.cloud.AppEngineDeploymentConfiguration;
import com.google.cloud.tools.intellij.appengine.cloud.AppEngineEnvironment;
import com.google.cloud.tools.intellij.appengine.facet.flexible.AppEngineFlexibleFacet;
import com.google.cloud.tools.intellij.appengine.facet.flexible.AppEngineFlexibleFacetType;
import com.google.cloud.tools.intellij.appengine.project.AppEngineProjectService;
import com.google.cloud.tools.intellij.appengine.project.AppEngineProjectService.FlexibleRuntime;
import com.google.cloud.tools.intellij.appengine.sdk.CloudSdkService;
import com.google.cloud.tools.intellij.appengine.sdk.CloudSdkValidationResult;
import com.google.cloud.tools.intellij.login.CredentialedUser;
import com.google.cloud.tools.intellij.resources.ProjectSelector;
import com.google.cloud.tools.intellij.ui.BrowserOpeningHyperLinkListener;
import com.google.cloud.tools.intellij.util.GctBundle;
import com.google.common.annotations.VisibleForTesting;

import com.intellij.facet.FacetManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.tree.TreeModelAdapter;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelEvent;

/**
 * Flexible deployment run configuration user interface.
 */
public class AppEngineFlexibleDeploymentEditor extends
    SettingsEditor<AppEngineDeploymentConfiguration> {
  private static final String DEFAULT_SERVICE = "default";
  private static final String LABEL_OPEN_TAG = "<html><font face='sans' size='-1'>";
  private static final String LABEL_CLOSE_TAG = "</font></html>";
  private static final String LABEL_HREF_CLOSE_TAG = "</a>";
  private static final String COST_WARNING_HREF_OPEN_TAG =
      "<a href='https://cloud.google.com/appengine/pricing'>";
  private static final String PROMOTE_INFO_HREF_OPEN_TAG =
      "<a href='https://console.cloud.google.com/appengine/versions'>";
  private static final AppEngineProjectService APP_ENGINE_PROJECT_SERVICE =
      AppEngineProjectService.getInstance();

  private JPanel mainPanel;
  private JBTextField version;
  private JCheckBox promoteVersionCheckBox;
  private JCheckBox stopPreviousVersionCheckBox;
  private ProjectSelector gcpProjectSelector;
  private JLabel serviceLabel;
  private TextFieldWithBrowseButton yamlTextField;
  private TextFieldWithBrowseButton dockerfileTextField;
  private TextFieldWithBrowseButton archiveSelector;
  private JTextPane appEngineCostWarningLabel;
  private AppEngineApplicationInfoPanel appInfoPanel;
  private JPanel archiveSelectorPanel;
  private JTextPane promoteInfoLabel;
  private JLabel dockerfileLabel;
  private JTextPane filesWarningLabel;
  private JLabel yamlLabel;
  private JComboBox<Module> modulesWithFlexFacetComboBox;
  private JCheckBox yamlOverrideCheckBox;
  private JCheckBox dockerfileOverrideCheckBox;
  private String dockerfileOverride = "";
  private JButton yamlModuleSettings;
  private JLabel environmentLabel;
  private DeploymentSource deploymentSource;

  public AppEngineFlexibleDeploymentEditor(Project project, AppEngineDeployable deploymentSource) {
    this.deploymentSource = deploymentSource;
    version.getEmptyText().setText(GctBundle.getString("appengine.flex.version.placeholder.text"));
    yamlTextField.addBrowseFolderListener(
        GctBundle.message("appengine.flex.config.browse.app.yaml"),
        null /* description */,
        project,
        FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter(
            virtualFile -> Comparing.equal(virtualFile.getExtension(), "yaml")
                || Comparing.equal(virtualFile.getExtension(), "yml"))
    );

    dockerfileTextField.addBrowseFolderListener(
        GctBundle.message("appengine.flex.config.browse.dockerfile"),
        null /* description */,
        project,
        FileChooserDescriptorFactory.createSingleFileDescriptor()
    );

    archiveSelector.addBrowseFolderListener(
        GctBundle.message("appengine.flex.config.user.specified.artifact.title"),
        null /* description */,
        project,
        FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter(
            virtualFile ->
                Comparing.equal(
                    virtualFile.getExtension(), "jar", SystemInfo.isFileSystemCaseSensitive)
                    || Comparing.equal(
                    virtualFile.getExtension(), "war", SystemInfo.isFileSystemCaseSensitive)
        )
    );

    archiveSelector.getTextField().getDocument().addDocumentListener(
        new DocumentAdapter() {
          @Override
          protected void textChanged(DocumentEvent event) {
            if (deploymentSource instanceof UserSpecifiedPathDeploymentSource) {
              ((UserSpecifiedPathDeploymentSource) deploymentSource).setFilePath(
                  archiveSelector.getText());
            }
          }
        }
    );

    yamlOverrideCheckBox.addChangeListener(
        event -> {
          boolean isYamlOverrideSelected = ((JCheckBox) event.getSource()).isSelected();
          yamlTextField.setVisible(isYamlOverrideSelected);
          toggleDockerfileSection();
          if (isYamlOverrideSelected) {
            checkConfigurationFiles();
          }
        });

    dockerfileOverrideCheckBox.addChangeListener(
        event -> {
          boolean isDockerfileOverrideSelected = ((JCheckBox) event.getSource()).isSelected();
          dockerfileTextField.setEnabled(isDockerfileOverrideSelected);
          if (isDockerfileOverrideSelected) {
            if (dockerfileOverride.isEmpty()) {
              dockerfileOverride = dockerfileTextField.getText();
            }
            dockerfileTextField.setText(dockerfileOverride);
            checkConfigurationFiles();
          } else {
            dockerfileOverride = dockerfileTextField.getText();
            dockerfileTextField.setText(getDockerfilePath());
          }
        }
    );

    yamlTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent event) {
        updateServiceName();
        toggleDockerfileSection();
        checkConfigurationFiles();
      }
    });

    dockerfileTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent event) {
        checkConfigurationFiles();
      }
    });

    appEngineCostWarningLabel.setText(
        GctBundle.message("appengine.flex.deployment.cost.warning",
            LABEL_OPEN_TAG,
            COST_WARNING_HREF_OPEN_TAG,
            LABEL_HREF_CLOSE_TAG,
            LABEL_CLOSE_TAG));
    appEngineCostWarningLabel.addHyperlinkListener(new BrowserOpeningHyperLinkListener());
    appEngineCostWarningLabel.setBackground(mainPanel.getBackground());

    gcpProjectSelector.addProjectSelectionListener(event ->
        appInfoPanel.refresh(event.getSelectedProject().getProjectId(),
            event.getUser().getCredential()));
    gcpProjectSelector.addModelListener(new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent event) {
        // projects have finished loading
        refreshApplicationInfoPanel();
      }
    });

    promoteInfoLabel.setText(
        GctBundle.message("appengine.promote.info.label",
            LABEL_OPEN_TAG,
            PROMOTE_INFO_HREF_OPEN_TAG,
            LABEL_HREF_CLOSE_TAG,
            LABEL_CLOSE_TAG));
    promoteInfoLabel.addHyperlinkListener(new BrowserOpeningHyperLinkListener());

    promoteVersionCheckBox.addItemListener(event -> {
      boolean isPromoteSelected = ((JCheckBox) event.getItem()).isSelected();

      stopPreviousVersionCheckBox.setEnabled(isPromoteSelected);

      if (!isPromoteSelected) {
        stopPreviousVersionCheckBox.setSelected(false);
      }
    });
    stopPreviousVersionCheckBox.setEnabled(false);

    modulesWithFlexFacetComboBox.setModel(new DefaultComboBoxModel<>(
        Arrays.stream(ModuleManager.getInstance(project).getModules())
            .filter(module ->
                FacetManager.getInstance(module)
                    .getFacetByType(AppEngineFlexibleFacetType.ID) != null)
            .toArray(Module[]::new)
    ));
    modulesWithFlexFacetComboBox.addItemListener(event -> {
      checkConfigurationFiles();
      toggleDockerfileSection();
    });
    modulesWithFlexFacetComboBox.setRenderer(new ListCellRendererWrapper<Module>() {
      @Override
      public void customize(JList list, Module value, int index, boolean selected,
          boolean hasFocus) {
        if (value != null) {
          setText(value.getName());
        }
      }
    });
    if (modulesWithFlexFacetComboBox.getItemCount() == 0) {
      yamlModuleSettings.setEnabled(false);
    }

    filesWarningLabel.setForeground(Color.RED);

    yamlModuleSettings.addActionListener(event -> {
      AppEngineFlexibleFacet flexFacet =
          FacetManager.getInstance(((Module) modulesWithFlexFacetComboBox.getSelectedItem()))
              .getFacetByType(AppEngineFlexibleFacetType.ID);
      ModulesConfigurator.showFacetSettingsDialog(flexFacet, null /* tabNameToSelect */);
      checkConfigurationFiles();
      toggleDockerfileSection();
    });

    updateSelectors();
    checkConfigurationFiles();
    toggleDockerfileSection();
  }

  private void refreshApplicationInfoPanel() {
    if (gcpProjectSelector.getProject() != null && gcpProjectSelector.getSelectedUser() != null) {
      appInfoPanel.refresh(gcpProjectSelector.getProject().getProjectId(),
          gcpProjectSelector.getSelectedUser().getCredential());
    }
  }

  @Override
  protected void resetEditorFrom(@NotNull AppEngineDeploymentConfiguration configuration) {
    version.setText(configuration.getVersion());
    promoteVersionCheckBox.setSelected(configuration.isPromote());
    stopPreviousVersionCheckBox.setSelected(configuration.isStopPreviousVersion());
    yamlTextField.setText(configuration.getYamlPath());
    dockerfileTextField.setText(configuration.getDockerFilePath());
    gcpProjectSelector.setText(configuration.getCloudProjectName());
    yamlTextField.setVisible(configuration.isOverrideYaml());
    archiveSelector.setText(configuration.getUserSpecifiedArtifactPath());
    yamlOverrideCheckBox.setSelected(configuration.isOverrideYaml());
    dockerfileOverrideCheckBox.setSelected(configuration.isOverrideDockerfile());
    modulesWithFlexFacetComboBox.setEnabled(!configuration.isOverrideYaml());

    toggleDockerfileSection();
    updateServiceName();
    refreshApplicationInfoPanel();
    checkConfigurationFiles();
  }

  @Override
  protected void applyEditorTo(@NotNull AppEngineDeploymentConfiguration configuration)
      throws ConfigurationException {
    validateConfiguration();

    configuration.setVersion(version.getText());
    configuration.setPromote(promoteVersionCheckBox.isSelected());
    configuration.setStopPreviousVersion(stopPreviousVersionCheckBox.isSelected());
    configuration.setYamlPath(getYamlPath());
    configuration.setDockerFilePath(getDockerfilePath());
    configuration.setCloudProjectName(gcpProjectSelector.getText());
    CredentialedUser user = gcpProjectSelector.getSelectedUser();
    if (user != null) {
      configuration.setGoogleUsername(user.getEmail());
    }
    String environment = "";
    if (deploymentSource instanceof UserSpecifiedPathDeploymentSource) {
      environment = AppEngineEnvironment.APP_ENGINE_FLEX.name();
    } else if (deploymentSource instanceof AppEngineArtifactDeploymentSource) {
      environment = ((AppEngineArtifactDeploymentSource) deploymentSource).getEnvironment().name();
    }
    configuration.setEnvironment(environment);
    configuration.setUserSpecifiedArtifact(
        deploymentSource instanceof UserSpecifiedPathDeploymentSource);
    configuration.setUserSpecifiedArtifactPath(archiveSelector.getText());
    configuration.setOverrideYaml(yamlOverrideCheckBox.isSelected());
    configuration.setOverrideDockerfile(dockerfileOverrideCheckBox.isSelected());
    updateSelectors();
    checkConfigurationFiles();
  }

  private void validateConfiguration() throws ConfigurationException {
    if (deploymentSource instanceof UserSpecifiedPathDeploymentSource
        && (StringUtil.isEmpty(archiveSelector.getText())
        || !isJarOrWar(archiveSelector.getText()))) {
      throw new ConfigurationException(
          GctBundle.message("appengine.flex.config.user.specified.artifact.error"));
    }
    if (!(deploymentSource instanceof UserSpecifiedPathDeploymentSource)
        && !deploymentSource.isValid()) {
      throw new ConfigurationException(
          GctBundle.message("appengine.config.deployment.source.error"));
    }
    if (StringUtils.isBlank(gcpProjectSelector.getText())) {
      throw new ConfigurationException(
          GctBundle.message("appengine.flex.config.project.missing.message"));
    }
    Set<CloudSdkValidationResult> validationResults =
        CloudSdkService.getInstance().validateCloudSdk();
    if (validationResults.contains(CloudSdkValidationResult.CLOUD_SDK_NOT_FOUND)) {
      throw new ConfigurationException(GctBundle.message(
          "appengine.cloudsdk.deploymentconfiguration.location.invalid.message"));
    }
    if (StringUtils.isBlank(getYamlPath())) {
      throw new ConfigurationException(
          GctBundle.message("appengine.flex.config.browse.app.yaml"));
    }
    try {
      if (!Files.exists(Paths.get(getYamlPath()))) {
        throw new ConfigurationException(
            GctBundle.getString("appengine.deployment.error.staging.yaml"));
      }
    } catch (InvalidPathException ipe) {
      throw new ConfigurationException(
          GctBundle.message("appengine.flex.config.badchars", "YAML"));
    }
    if (APP_ENGINE_PROJECT_SERVICE.getFlexibleRuntimeFromAppYaml(
        getYamlPath()).equals(FlexibleRuntime.CUSTOM)) {
      if (StringUtils.isBlank(getDockerfilePath())) {
        throw new ConfigurationException(
            GctBundle.message("appengine.flex.config.browse.dockerfile"));
      }
      try {
        if (!Files.exists(Paths.get(getDockerfilePath()))) {
          throw new ConfigurationException(
              GctBundle.getString("appengine.deployment.error.staging.dockerfile"));
        }
      } catch (InvalidPathException ipe) {
        throw new ConfigurationException(
            GctBundle.message("appengine.flex.config.badchars", "Dockerfile"));
      }
    }
    if (!appInfoPanel.isApplicationValid()) {
      throw new ConfigurationException(
          GctBundle.message("appengine.application.required.deployment"));
    }
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return mainPanel;
  }

  private void updateServiceName() {
    Optional<String> service =
        APP_ENGINE_PROJECT_SERVICE.getServiceNameFromAppYaml(getYamlPath());
    serviceLabel.setText(service.orElse(DEFAULT_SERVICE));
  }

  private void updateSelectors() {
    archiveSelectorPanel.setVisible(deploymentSource instanceof UserSpecifiedPathDeploymentSource);
  }

  private boolean isJarOrWar(String stringPath) {
    try {
      Path path = Paths.get(stringPath);
      return !Files.isDirectory(path) && (StringUtil.endsWithIgnoreCase(stringPath, ".jar")
          || StringUtil.endsWithIgnoreCase(stringPath, ".war"));
    } catch (InvalidPathException ipe) {
      return false;
    }
  }

  /**
   * Enables the Dockerfile section of the UI if the Yaml file contains "runtime: custom". Disables
   * it otherwise.
   */
  private void toggleDockerfileSection() {
    boolean isCustomRuntime = APP_ENGINE_PROJECT_SERVICE.getFlexibleRuntimeFromAppYaml(
        getYamlPath()).equals(FlexibleRuntime.CUSTOM);
    dockerfileOverrideCheckBox.setVisible(isCustomRuntime);
    dockerfileTextField.setVisible(isCustomRuntime);
    dockerfileTextField.setEnabled(dockerfileOverrideCheckBox.isSelected());
    dockerfileLabel.setVisible(isCustomRuntime);
    if (isCustomRuntime) {
      dockerfileTextField.setText(getDockerfilePath());
    }
  }

  private void checkConfigurationFiles() {
    checkConfigurationFile(getYamlPath(), yamlTextField.getTextField(), yamlLabel);
    if (APP_ENGINE_PROJECT_SERVICE.getFlexibleRuntimeFromAppYaml(getYamlPath())
        .equals(FlexibleRuntime.CUSTOM)) {
      checkConfigurationFile(dockerfileTextField.getText(), dockerfileTextField.getTextField(),
          dockerfileLabel);
    }

    filesWarningLabel.setVisible(yamlTextField.getTextField().getForeground().equals(Color.RED)
        || (APP_ENGINE_PROJECT_SERVICE.getFlexibleRuntimeFromAppYaml(getYamlPath())
        .equals(FlexibleRuntime.CUSTOM)
        && dockerfileTextField.getTextField().getForeground().equals(Color.RED)));
  }

  /**
   * Checks if a specified configuration file is valid or not and triggers UI warnings
   * accordingly.
   */
  private void checkConfigurationFile(String path, JTextField textField, JLabel label) {
    try {
      if (!Files.exists(Paths.get(path)) || !Files.isRegularFile(Paths.get(path))) {
        textField.setForeground(Color.RED);
        label.setForeground(Color.RED);
      } else {
        textField.setForeground(Color.BLACK);
        label.setForeground(Color.BLACK);
      }
    } catch (InvalidPathException ipe) {
      textField.setForeground(Color.RED);
      label.setForeground(Color.RED);
    }
  }

  /**
   * Returns the final Yaml file path from the combobox or text field, depending on if it's
   * overridden.
   */
  private String getYamlPath() {
    if (yamlOverrideCheckBox.isSelected()) {
      return yamlTextField.getText();
    }

    if (modulesWithFlexFacetComboBox.getSelectedItem() == null) {
      return "";
    }

    return Optional.ofNullable(
        FacetManager.getInstance(((Module) modulesWithFlexFacetComboBox.getSelectedItem()))
        .getFacetByType(AppEngineFlexibleFacetType.ID))
        .map(flexFacet -> flexFacet.getConfiguration().getYamlPath())
        .orElse("");
  }

  private String getDockerfilePath() {
    if (dockerfileOverrideCheckBox.isSelected()) {
      return dockerfileTextField.getText();
    }

    if (modulesWithFlexFacetComboBox.getSelectedItem() == null) {
      return "";
    }

    return Optional.ofNullable(
        FacetManager.getInstance(((Module) modulesWithFlexFacetComboBox.getSelectedItem()))
            .getFacetByType(AppEngineFlexibleFacetType.ID))
        .map(flexFacet -> flexFacet.getConfiguration().getDockerfilePath())
        .orElse("");
  }

  @VisibleForTesting
  JCheckBox getPromoteVersionCheckBox() {
    return promoteVersionCheckBox;
  }

  @VisibleForTesting
  JCheckBox getStopPreviousVersionCheckBox() {
    return stopPreviousVersionCheckBox;
  }

  @VisibleForTesting
  TextFieldWithBrowseButton getYamlTextField() {
    return yamlTextField;
  }

  @VisibleForTesting
  TextFieldWithBrowseButton getDockerfileTextField() {
    return dockerfileTextField;
  }

  @VisibleForTesting
  JLabel getDockerfileLabel() {
    return dockerfileLabel;
  }

  @VisibleForTesting
  JCheckBox getDockerfileOverrideCheckBox() {
    return dockerfileOverrideCheckBox;
  }

  @VisibleForTesting
  JLabel getYamlLabel() {
    return yamlLabel;
  }

  @VisibleForTesting
  JCheckBox getYamlOverrideCheckBox() {
    return yamlOverrideCheckBox;
  }

  @VisibleForTesting
  JLabel getServiceLabel() {
    return serviceLabel;
  }

  @VisibleForTesting
  JTextPane getFilesWarningLabel() {
    return filesWarningLabel;
  }

  @VisibleForTesting
  JComboBox getModulesWithFlexFacetComboBox() {
    return modulesWithFlexFacetComboBox;
  }

  @VisibleForTesting
  TextFieldWithBrowseButton getArchiveSelector() {
    return archiveSelector;
  }

  @VisibleForTesting
  ProjectSelector getGcpProjectSelector() {
    return gcpProjectSelector;
  }

  @VisibleForTesting
  void setAppInfoPanel(AppEngineApplicationInfoPanel appInfoPanel) {
    this.appInfoPanel = appInfoPanel;
  }

  @VisibleForTesting
  void setDeploymentSource(DeploymentSource deploymentSource) {
    this.deploymentSource = deploymentSource;
  }
}
