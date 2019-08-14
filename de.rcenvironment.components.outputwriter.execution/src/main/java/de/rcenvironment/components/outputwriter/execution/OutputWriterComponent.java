/*
 * Copyright (C) 2006-2016 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.components.outputwriter.execution;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.rcenvironment.components.outputwriter.common.OutputLocation;
import de.rcenvironment.components.outputwriter.common.OutputLocationList;
import de.rcenvironment.components.outputwriter.common.OutputWriterComponentConstants;
import de.rcenvironment.core.component.api.ComponentException;
import de.rcenvironment.core.component.datamanagement.api.ComponentDataManagementService;
import de.rcenvironment.core.component.execution.api.ComponentContext;
import de.rcenvironment.core.component.execution.api.ComponentLog;
import de.rcenvironment.core.component.model.spi.DefaultComponent;
import de.rcenvironment.core.datamodel.api.DataType;
import de.rcenvironment.core.datamodel.api.TypedDatum;
import de.rcenvironment.core.datamodel.types.api.DirectoryReferenceTD;
import de.rcenvironment.core.datamodel.types.api.FileReferenceTD;
import de.rcenvironment.core.utils.common.JsonUtils;
import de.rcenvironment.core.utils.common.StringUtils;
import de.rcenvironment.core.utils.common.TempFileService;
import de.rcenvironment.core.utils.common.TempFileServiceAccess;

/**
 * The default Outputwriter backend.
 * 
 * @author Hendrik Abbenhaus
 * @author Sascha Zur
 * @author Brigitte Boden
 * @author Oliver Seebach
 * 
 */
public class OutputWriterComponent extends DefaultComponent {

    private static final String FRONTSLASH = "/";

    private static final String BACKSLASHES = "\\";

    private static final String DOT = ".";

    private static final String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss-S";

    private ComponentContext componentContext;

    private ComponentLog componentLog;

    private ComponentDataManagementService dataManagementService;

    private TempFileService tempFileService = TempFileServiceAccess.getInstance();

    private String root = "";

    private String wfStartTimeStamp;

    private boolean writesToRelativePath = false;

    private Map<String, OutputLocationWriter> inputNameToOutputLocationWriter = new HashMap<>();

    @Override
    public void setComponentContext(ComponentContext componentContext) {
        this.componentContext = componentContext;
        componentLog = componentContext.getLog();
    }

    private void checkRelativePathForValidProject(String relativePath) throws ComponentException {
        if (relativePath.split(FRONTSLASH).length < 2) {
            throw new ComponentException(StringUtils.format("Cannot resolve root location '%s' "
                + "because it contains no project.", relativePath));
        } else {
            String projectName = relativePath.split(FRONTSLASH)[1];
            if (!ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).exists()) {
                throw new ComponentException(StringUtils.format("Failed to resolve root location '%s' "
                    + "because the given project '%s' could not be found.", relativePath, projectName));
            }
        }
    }

    private String adaptRootToAbsoluteRootIfProjectRelative(String rootToBeAdapted) throws ComponentException {

        String absoluteRoot = rootToBeAdapted;

        // if front and backslashes are mixed -> exception
        if (rootToBeAdapted.contains(FRONTSLASH) && rootToBeAdapted.contains(BACKSLASHES)) {
            throw new ComponentException(StringUtils.format(
                "Given path to file or directory could not be resolved, as it contains front and backslash as well: %s", rootToBeAdapted));
        }

        File file = new File(rootToBeAdapted);
        if (!file.isAbsolute()) {

            if (rootToBeAdapted.startsWith(OutputWriterComponentConstants.PH_WORKSPACE + FRONTSLASH)) {

                if (ResourcesPlugin.getWorkspace().getRoot().exists()) {

                    checkRelativePathForValidProject(rootToBeAdapted);

                    String workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
                    absoluteRoot = rootToBeAdapted.replace(OutputWriterComponentConstants.PH_WORKSPACE, workspacePath);
                } else {
                    throw new ComponentException(StringUtils.format("Failed to resolve root location '%s' "
                        + "because the workspace could not be determined. "
                        + "Note that in headless mode relative paths are not supported.", rootToBeAdapted));
                }
            } else {
                // // TODO 9.0.0: Remove this warning and make this fail,
                // // as relative paths without the explicit workspace placeholder are no longer supported
                componentLog.componentWarn(StringUtils.format("Note that from version 9.0 on relative paths have to start explicitly "
                    + "with the prefix '%s'. Relative paths without this prefix are resolved relative to the "
                    + "current working directory.", OutputWriterComponentConstants.PH_WORKSPACE));
            }
        }

        return absoluteRoot;
    }

    @Override
    public void start() throws ComponentException {
        dataManagementService = componentContext.getService(ComponentDataManagementService.class);

        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
        wfStartTimeStamp = df.format(dt);

        String rootonworkflowstart = componentContext.getConfigurationValue(OutputWriterComponentConstants.CONFIG_KEY_ONWFSTART);
        boolean onwfstart = Boolean.parseBoolean(rootonworkflowstart);
        if (onwfstart) {
            this.root = componentContext.getConfigurationValue(OutputWriterComponentConstants.CONFIG_KEY_ONWFSTART_ROOT);
        } else {
            this.root = adaptRootToAbsoluteRootIfProjectRelative(
                componentContext.getConfigurationValue(OutputWriterComponentConstants.CONFIG_KEY_ROOT));
            File tempRootFile = new File(componentContext.getConfigurationValue(OutputWriterComponentConstants.CONFIG_KEY_ROOT));
            writesToRelativePath = !tempRootFile.isAbsolute();
        }

        // Parse list of outputLocations and initialize corresponding objects
        String jsonString = componentContext.getConfigurationValue(OutputWriterComponentConstants.CONFIG_KEY_OUTPUTLOCATIONS);
        // For "old" outputWriters that only have file/directory inputs, the jsonString may not be set
        if (jsonString != null && !jsonString.isEmpty()) {
            ObjectMapper jsonMapper = JsonUtils.getDefaultObjectMapper();
            jsonMapper.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
            try {
                OutputLocationList outputList = jsonMapper.readValue(jsonString, OutputLocationList.class);
                for (OutputLocation out : outputList.getOutputLocations()) {
                    OutputLocationWriter writer =
                        new OutputLocationWriter(out.getInputs(), out.getHeader(), out.getFormatString(),
                            out.getHandleExistingFile(), componentLog);
                    for (String input : out.getInputs()) {
                        inputNameToOutputLocationWriter.put(input, writer);
                    }
                    // Initialize the file for the outputLocation. Checks if the file already exists at component start. If yes, the
                    // filename is changed.
                    // NOTE: The handling of a file existing at workflow start (always AUTORENAME) is done here (as it is the same as for
                    // files and directories), whereas the handling of files in later iterations is done in the OutputLocationWriter.
                    String path = out.getFolderForSaving() + File.separator + out.getFilename();
                    path = path.substring(OutputWriterComponentConstants.ROOT_DISPLAY_NAME.length() + 1);
                    path = replacePlaceholder(path, "", null);

                    File fileToWrite = new File(root + File.separator + path);
                    writer.initializeFile(fileToWrite);
                }
            } catch (IOException e) {
                throw new ComponentException("Failed to parse (internal) configuration (JSON string)", e);
            }
        }
    }

    @Override
    public void processInputs() throws ComponentException {

        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);

        String inputName = componentContext.getInputsWithDatum().iterator().next();
        TypedDatum input = componentContext.readInput(inputName);

        if (input.getDataType().equals(DataType.DirectoryReference) || input.getDataType().equals(DataType.FileReference)) {
            processFileOrDirectory(inputName, input);
        } else if (inputNameToOutputLocationWriter.get(inputName) != null) {
            Map<String, TypedDatum> inputMap = new HashMap<>();
            for (String name : componentContext.getInputsWithDatum()) {
                inputMap.put(name, componentContext.readInput(name));
            }
            inputNameToOutputLocationWriter.get(inputName).writeOutput(inputMap, df.format(dt), componentContext.getExecutionCount());
        } else {
            componentLog.componentWarn(StringUtils.format("Received value for input '%s' that is"
                + " not associated with any target for simple data types", inputName));
        }
        if (writesToRelativePath) {
            try {
                ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (CoreException e) {
                componentLog.componentInfo("Failed to refresh the workspace automatically. "
                    + "Files or directories written to the workspace might not be visible at the moment. "
                    + "Please try to refresh the respective folder(s) manually via its context menu.");
            }
        }

    }

    private void processFileOrDirectory(String inputName, TypedDatum input) throws ComponentException {
        String path = componentContext.getInputMetaDataValue(inputName, OutputWriterComponentConstants.CONFIG_KEY_FOLDERFORSAVING)
            + File.separator
            + componentContext.getInputMetaDataValue(inputName, OutputWriterComponentConstants.CONFIG_KEY_FILENAME);

        path = path.substring(OutputWriterComponentConstants.ROOT_DISPLAY_NAME.length() + 1);
        String origFilename = null;
        if (input.getDataType().equals(DataType.DirectoryReference)) {
            origFilename = ((DirectoryReferenceTD) input).getDirectoryName();
        } else if (input.getDataType().equals(DataType.FileReference)) {
            origFilename = ((FileReferenceTD) input).getFileName();
        }

        path = replacePlaceholder(path, inputName, origFilename);

        File fileToWrite = new File(root + File.separator + path);

        if (!fileToWrite.exists()) {
            writeFile(input, fileToWrite.getAbsolutePath(), inputName);
        } else {
            File possibleFile = autoRename(fileToWrite);
            writeFile(input, possibleFile.getAbsolutePath(), inputName);
        }

        if (input.getDataType().equals(DataType.DirectoryReference)) {
            componentLog.componentInfo(StringUtils.format("Wrote directory '%s' of input '%s' to: %s",
                ((DirectoryReferenceTD) input).getDirectoryName(), inputName, fileToWrite.getAbsolutePath()));
        } else if (input.getDataType().equals(DataType.FileReference)) {
            componentLog.componentInfo(StringUtils.format("Wrote file '%s' of input '%s' to: %s",
                ((FileReferenceTD) input).getFileName(), inputName, fileToWrite.getAbsolutePath()));
        }
    }

    protected File autoRename(File fileToWrite) {
        String folderpath = fileToWrite.getParent();
        String fileName = fileToWrite.getName();
        String extension = "";

        if (fileName.contains(DOT)) {
            extension = fileName.substring(fileName.lastIndexOf(DOT));
            fileName = fileName.substring(0, fileName.lastIndexOf(DOT));
        }
        int i = 1;
        File possibleFile = new File(folderpath, fileName + " (" + i + ")" + extension);

        while (possibleFile.exists()) {
            possibleFile = new File(folderpath, fileName + " (" + ++i + ")" + extension);
        }
        componentLog.componentInfo(StringUtils.format("File '%s' already exists, "
            + "renamed to: %s", fileToWrite.getAbsolutePath(), possibleFile.getAbsolutePath()));
        return possibleFile;
    }

    /**
     * Replaces the placeholder.
     * 
     * @param pathinput contains a input
     * @param currentInputName contains a current input name
     * @param filename the filename
     * @return the input without placeholder
     */
    public String replacePlaceholder(String pathinput, String currentInputName, String filename) {
        String output = pathinput;
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_WORKFLOWNAME), componentContext.getWorkflowInstanceName()
                .replaceAll(":", "-"));
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_INPUTNAME), currentInputName);
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_WF_START_TS), wfStartTimeStamp);
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_COMP_NAME), componentContext.getInstanceName());
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_COMP_TYPE), componentContext.getComponentName());
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_EXECUTION_COUNT),
                Integer.toString(componentContext.getExecutionCount()));
        // In the case of simple data inputs, there is no initial filename, so this placeholder is only replaced for files/directories.
        if (filename != null) {
            output =
                output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_FILE_NAME), filename);
        }
        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
        output =
            output.replaceAll(escapePlaceholder(OutputWriterComponentConstants.PH_TIMESTAMP), df.format(dt));
        return output;
    }

    private String escapePlaceholder(String placeholder) {
        return placeholder
            .replace(OutputWriterComponentConstants.PH_PREFIX, BACKSLASHES + OutputWriterComponentConstants.PH_PREFIX)
            .replace(OutputWriterComponentConstants.PH_SUFFIX, BACKSLASHES + OutputWriterComponentConstants.PH_SUFFIX);
    }

    private void writeFile(TypedDatum input, String path, String inputName) throws ComponentException {
        File file = new File(path);
        String filename = file.getName();
        file = new File(file.getAbsolutePath().replace(File.separator + filename, ""));
        if (!file.isDirectory()) {
            file.mkdirs();
        }
        final File incFileOrDir;
        switch (input.getDataType()) {
        case FileReference:
            // Check for invalid filename
            List<String> forbiddenFilenames = Arrays.asList(OutputWriterComponentConstants.PROBLEMATICFILENAMES_WIN);
            if (forbiddenFilenames.contains(filename) || filename.contains("/") || filename.contains("\\")) {
                throw new ComponentException(StringUtils.format("Failed to write file of input '%s' because '%s' "
                    + "is a forbidden filename", inputName, filename));
            }
            incFileOrDir = new File(path);
            try {
                dataManagementService.copyReferenceToLocalFile(((FileReferenceTD) input).getFileReference(),
                    incFileOrDir, componentContext.getDefaultStorageNodeId());
            } catch (IOException e) {
                throw new ComponentException(StringUtils.format("Failed to write file of input '%s' to %s",
                    inputName, incFileOrDir.getAbsolutePath()), e);
            }
            break;
        case DirectoryReference:
            incFileOrDir = new File(path);
            File tempDir;
            try {
                tempDir = tempFileService.createManagedTempDir();
            } catch (IOException e) {
                throw new ComponentException("Failed to create temporary directory that is required by Output Writer", e);
            }
            try {
                dataManagementService.copyDirectoryReferenceTDToLocalDirectory(componentContext, ((DirectoryReferenceTD) input),
                    tempDir);
                FileUtils.moveDirectory(new File(tempDir, ((DirectoryReferenceTD) input).getDirectoryName()), incFileOrDir);
            } catch (IOException e) {
                throw new ComponentException(StringUtils.format("Failed to write directory of input '%s' to %s",
                    inputName, incFileOrDir.getAbsolutePath()), e);
            } finally {
                try {
                    tempFileService.disposeManagedTempDirOrFile(tempDir);
                } catch (IOException e) {
                    LogFactory.getLog(getClass()).error("Failed to delete temporary directory", e);
                }
            }
            break;
        default:
            break;
        }

    }

    @Override
    public void tearDown(FinalComponentState state) {
        super.tearDown(state);
        // Close all the output streams
        for (OutputLocationWriter out : inputNameToOutputLocationWriter.values()) {
            out.close();
        }
    }

}
