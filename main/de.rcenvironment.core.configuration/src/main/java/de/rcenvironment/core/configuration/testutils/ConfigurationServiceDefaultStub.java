/*
 * Copyright (C) 2006-2015 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */
package de.rcenvironment.core.configuration.testutils;

import java.io.File;
import java.util.Map;

import de.rcenvironment.core.configuration.ConfigurationSegment;
import de.rcenvironment.core.configuration.ConfigurationService;

/**
 * Default test stub for {@link ConfigurationService}. Returns the Java default field values for all methods with a return value.
 * 
 * @author Robert Mischke
 */
public class ConfigurationServiceDefaultStub implements ConfigurationService {

    @Override
    public void addSubstitutionProperties(String namespace, Map<String, String> properties) {}

    @Override
    public <T> T getConfiguration(String identifier, Class<T> clazz) {
        return null;
    }

    @Override
    public ConfigurationSegment getConfigurationSegment(String relativePath) {
        return null;
    }

    @Override
    public void reloadConfiguration() {}

    @Override
    public String resolveBundleConfigurationPath(String identifier, String path) {
        return null;
    }

    @Override
    public String getInstanceName() {
        return null;
    }

    @Override
    public boolean getIsWorkflowHost() {
        return false;
    }

    @Override
    public boolean getIsRelay() {
        return false;
    }

    @Override
    public File getProfileDirectory() {
        return null;
    }

    @Override
    public File getProfileConfigurationFile() {
        return null;
    }

    @Override
    public boolean isUsingIntendedProfileDirectory() {
        return false;
    }

    @Override
    public File getOriginalProfileDirectory() {
        return null;
    }

    @Override
    public File getConfigurablePath(ConfigurablePathId pathId) {
        return null;
    }

    @Override
    public File[] getConfigurablePathList(ConfigurablePathListId pathListId) {
        return null;
    }

    @Override
    public File initializeSubDirInConfigurablePath(ConfigurablePathId pathId, String relativePath) {
        return null;
    }

    @Override
    public File getParentTempDirectoryRoot() {
        return null;
    }

    @Override
    public boolean isUsingDefaultConfigurationValues() {
        return false;
    }

    @Override
    public File getInstallationDir() {
        return null;
    }
}
