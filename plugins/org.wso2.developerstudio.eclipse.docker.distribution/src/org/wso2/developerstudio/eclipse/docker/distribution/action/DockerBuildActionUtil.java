/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.developerstudio.eclipse.docker.distribution.action;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.wso2.developerstudio.eclipse.docker.distribution.Activator;
import org.wso2.developerstudio.eclipse.docker.distribution.model.DockerHubAuth;
import org.wso2.developerstudio.eclipse.docker.distribution.utils.DockerProjectConstants;
import org.wso2.developerstudio.eclipse.logging.core.IDeveloperStudioLog;
import org.wso2.developerstudio.eclipse.logging.core.Logger;
import org.wso2.developerstudio.eclipse.maven.util.MavenUtils;
import org.wso2.developerstudio.eclipse.platform.core.project.export.util.ExportUtil;
import org.xml.sax.SAXException;

/**
 * Util class for handle export CApps generate docker image and push to the registry.
 */
public class DockerBuildActionUtil {

    private static IDeveloperStudioLog log = Logger.getLog(Activator.PLUGIN_ID);

    private static final String POM_XML = "pom.xml";
    private static final String CAPP_NATURE = "org.wso2.developerstudio.eclipse.distribution.project.nature";
    private static final String MAVEN_DOCKER_BUILD = "Run Docker Build Internal Configuration";
    public static final String MAVEN_CONFIGURATION_TYPE = "org.eclipse.m2e.Maven2LaunchConfigurationType";
    public static final String MAVEN_GOAL_KEY = "M2_GOALS";
    public static final String LAUNCHER_RUN = "run";
    public static final String MAVEN_WORKING_DIR_KEY = "org.eclipse.jdt.launching.WORKING_DIRECTORY";
    public static final String MAVEN_ENVIRONMENT_KEY = "org.eclipse.debug.core.environmentVariables";
    public static final String JAVA_HOME_KEY = "JAVA_HOME";
    public static final String MAVEN_BUILD_GOAL = "install";
    public static final String JDK_PATH = "jdk-home";
    public static final String TOOLING_PATH_MAC = "/Applications/IntegrationStudio.app/Contents/Eclipse";
    public static final String JDK_PATH_MAC = "jdk-home/Contents/Home";

    /**
     * Static method for get selected composite dependencies from the POM.
     * 
     * @param pomFile pom file of the project
     * @return list of composite projects as string list
     */
    public static List<String> getCompositeAppDependencyList(File pomFile) {
        List<String> dependencyProjectNames = new ArrayList<>();
        MavenProject parentPrj;
        try {
            parentPrj = MavenUtils.getMavenProject(pomFile);
            for (Dependency dependency : (List<Dependency>) parentPrj.getDependencies()) {
                dependencyProjectNames.add(dependency.getArtifactId());
            }
        } catch (IOException e) {
            log.error("IOException while reading the pom file in container project", e);
        } catch (XmlPullParserException e) {
            log.error("XmlPullParserException while reading the pom file in container project", e);
        }

        return dependencyProjectNames;
    }

    /**
     * Static method for export CApp to the given destination location.
     * Create CApp using the dependent artifacts
     * 
     * @param carbonAppsFolderLocation export destination location
     * @param dependencyProjectNames pom defined composite projects
     * @return boolean value of isCarbonAppsExportSuccessfully
     */
    public static boolean exportCarbonAppsToTargetFolder(String carbonAppsFolderLocation,
            List<String> dependencyProjectNames) {
        boolean isCarbonAppsExportSuccessfully = false;
        // Fetch all carbon applications in the workspace.
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        Map<String, IProject> carbonApplicationList = new HashMap<String, IProject>();

        try {
            for (IProject project : projects) {
                IFile pomFileRes = project.getFile(POM_XML);
                if (project.isOpen() && pomFileRes.exists() && project.hasNature(CAPP_NATURE)) {
                    String cappName = pomFileRes.getProject().getName();
                    carbonApplicationList.put(cappName, project);
                }
            }

            // Filter dependency carbon applications.
            List<IProject> dependentProjects = new ArrayList<>();
            for (Map.Entry<String, IProject> entry : carbonApplicationList.entrySet()) {
                if (dependencyProjectNames.contains(entry.getKey())) {
                    dependentProjects.add(entry.getValue());
                }
            }

            // Export all the carbon applications to the target folder
            for (IProject project : dependentProjects) {
                exportCarToM2Folder(project, carbonAppsFolderLocation);
            }

            isCarbonAppsExportSuccessfully = true;
        } catch (CoreException e) {
            log.error("Error occurred while checking the nature of the project", e);
        } catch (IOException | XmlPullParserException e) {
            log.error("Error occurred while exporting the CarbonApp to the target folder", e);
        }

        return isCarbonAppsExportSuccessfully;
    }

    /**
     * Export the associated carbon application to a given folder location.
     * 
     * @param project Carbon application project.
     * @param location Destination folder.
     * @throws IOException Error occurred while copying file.
     * @throws XmlPullParserException Error occurred while parsing the pom file.
     */
    private static void exportCarToM2Folder(IProject project, String location)
            throws IOException, XmlPullParserException {
        IFile pomFileRes = project.getFile(POM_XML);
        File pomFile = pomFileRes.getLocation().toFile();
        MavenProject parentPrj = MavenUtils.getMavenProject(pomFile);
        String finalFileName = String.format("%s-%s.car", parentPrj.getModel().getArtifactId(),
                parentPrj.getModel().getVersion());
        try {
            IResource CarbonArchive = ExportUtil.buildCAppProject(pomFileRes.getProject());
            String groupID = parentPrj.getModel().getGroupId();
            groupID = groupID.replace(".", File.separator);
            location = location + File.separator + groupID + File.separator + parentPrj.getModel().getArtifactId()
                    + File.separator + parentPrj.getModel().getVersion();
            File destFileName = new File(location.trim(), finalFileName);
            if (destFileName.exists()) {
                org.apache.commons.io.FileUtils.deleteQuietly(destFileName);
            }
            FileUtils.copyFile(CarbonArchive.getLocation().toFile(), destFileName);
        } catch (Exception e) {
            log.error("An error occurred while creating the carbon archive file", e);
        }
    }

    /**
     * Method of creating maven launcher profile in ILaunchManager.
     * 
     * @param launchManager launcher
     * @param project selected IProject
     * @throws CoreException Error occurred while running the maven job
     */
    public static void runDockerBuildWithMavenProfile(IProject project, String mavenGoal, DockerHubAuth configuration)
            throws CoreException {
        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();

        // remove existing maven launcher for docker build
        if (findLaunchConfigurationByName(launchManager, MAVEN_DOCKER_BUILD) != null) {
            findLaunchConfigurationByName(launchManager, MAVEN_DOCKER_BUILD).delete();
        }

        // creating a new Launcher for docker build
        ILaunchConfigurationType mavenTestLaunchType = launchManager
                .getLaunchConfigurationType(MAVEN_CONFIGURATION_TYPE);
        ILaunchConfigurationWorkingCopy mavenTestLaunchConfig = mavenTestLaunchType.newInstance(null,
                DebugPlugin.getDefault().getLaunchManager().generateLaunchConfigurationName(MAVEN_DOCKER_BUILD));

        // set maven properties for the created launcher
        mavenTestLaunchConfig.setAttribute(MAVEN_GOAL_KEY, mavenGoal);
        mavenTestLaunchConfig.setAttribute(MAVEN_WORKING_DIR_KEY, "${workspace_loc:/" + project.getName() + "}");
        String javaHomePath = getJavaHomePath();
        Map<String, String> environmentVariableMap = new HashMap<>();
        environmentVariableMap.put(JAVA_HOME_KEY, javaHomePath);
        mavenTestLaunchConfig.setAttribute(MAVEN_ENVIRONMENT_KEY, environmentVariableMap);

        // save the launcher with configuration data
        mavenTestLaunchConfig.doSave();

        // Select the maven launcher and run it
        for (ILaunchConfiguration launchConfig : launchManager.getLaunchConfigurations()) {
            if (launchConfig.getName().equals(MAVEN_DOCKER_BUILD)) {
                ILaunch launch = launchConfig.launch(LAUNCHER_RUN, null);
                boolean isBuildOver = false;

                // execute build and push maven jobs using a another java thread
                MavenJobThread mvnTread = new MavenJobThread(isBuildOver, launch, project, configuration);
                Thread newThread = new Thread(mvnTread);
                newThread.start();

                break;
            }
        }
    }

    /**
     * Method of finding a ILauncher from existing ILaunchers by its name.
     * 
     * @param launchManager
     * @param configName name of the searching ILauncher
     * @return ILaunchConfiguration searched
     */
    public static ILaunchConfiguration findLaunchConfigurationByName(ILaunchManager launchManager, String configName)
            throws CoreException {
        ILaunchConfiguration[] availableLauchConfigs = launchManager.getLaunchConfigurations();
        for (ILaunchConfiguration iLaunchConfig : availableLauchConfigs) {
            if (configName.equals(iLaunchConfig.getName())) {
                return iLaunchConfig;
            }
        }
        return null;
    }

    /**
     * Method of getting JAVA_HOME path based on the OS type.
     * 
     * @return JAVA_HOME path
     */
    public static String getJavaHomePath() {
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        String microInteratorPath;

        if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
            // check if EI Tooling is in Application folder for MAC
            File macOSEIToolingAppFile = new File(TOOLING_PATH_MAC);
            if (macOSEIToolingAppFile.exists()) {
                microInteratorPath = TOOLING_PATH_MAC + File.separator + JDK_PATH_MAC;
            } else {
                java.nio.file.Path path = Paths.get("");
                microInteratorPath = (path).toAbsolutePath().toString() + File.separator + JDK_PATH_MAC;
            }
        } else {
            java.nio.file.Path path = Paths.get("");
            microInteratorPath = (path).toAbsolutePath().toString() + File.separator + JDK_PATH;
        }

        return microInteratorPath;
    }
    
    /**
     * Save docker image details to the POM file.
     * 
     * @param pomFile pom file
     */
    public static void changeDockerImageDataInPOMPlugin(File pomFile, String targetRepository, String targetTag) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            XPath xPathRepo = XPathFactory.newInstance().newXPath();
            Node repositoryNode = (Node) xPathRepo.compile(DockerProjectConstants.TARGET_REPOSITORY_XPATH).evaluate(doc, XPathConstants.NODE);
            repositoryNode.setTextContent(targetRepository);

            XPath xPathTag = XPathFactory.newInstance().newXPath();
            Node tagNode = (Node) xPathTag.compile(DockerProjectConstants.TARGET_TAG_XPATH).evaluate(doc, XPathConstants.NODE);
            tagNode.setTextContent(targetTag);

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource domSource = new DOMSource(doc);
            StreamResult sr = new StreamResult(pomFile);
            tf.transform(domSource, sr);
        } catch (TransformerException e) {
            log.error("TransformerException while reading pomfile", e);
        } catch (TransformerFactoryConfigurationError e) {
            log.error("TransformerFactoryConfigurationError while reading pomfile", e);
        } catch (XPathExpressionException e) {
            log.error("XPathExpressionException while reading pomfile", e);
        } catch (ParserConfigurationException e) {
            log.error("ParserConfigurationException while reading pomfile", e);
        } catch (SAXException e) {
            log.error("SAXException while reading pomfile", e);
        } catch (IOException e) {
            log.error("IOException while reading pomfile", e);
        }
    }
    
    /**
     * Change the docker image repository URL in the POM file.
     * 
     * @param pomFile pom file
     * @param targetRepository updated repository URL
     */
    public static void changeDockerImageDataInPOMPlugin(File pomFile, String targetRepository) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            XPath xPathRepo = XPathFactory.newInstance().newXPath();
            Node repositoryNode = (Node) xPathRepo.compile(DockerProjectConstants.TARGET_REPOSITORY_XPATH).evaluate(doc, XPathConstants.NODE);
            repositoryNode.setTextContent(targetRepository);

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource domSource = new DOMSource(doc);
            StreamResult sr = new StreamResult(pomFile);
            tf.transform(domSource, sr);
        } catch (TransformerException e) {
            log.error("TransformerException while reading pomfile", e);
        } catch (TransformerFactoryConfigurationError e) {
            log.error("TransformerFactoryConfigurationError while reading pomfile", e);
        } catch (XPathExpressionException e) {
            log.error("XPathExpressionException while reading pomfile", e);
        } catch (ParserConfigurationException e) {
            log.error("ParserConfigurationException while reading pomfile", e);
        } catch (SAXException e) {
            log.error("SAXException while reading pomfile", e);
        } catch (IOException e) {
            log.error("IOException while reading pomfile", e);
        }
    }
    
    /**
     * Read docker image details from the POM file.
     * 
     * @param pomFile pom file
     * @return value of the target repository
     */
    public static String readDockerImageDetailsFromPomPlugin(File pomFile) {
        String repository = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            XPathExpression xpRepo = XPathFactory.newInstance().newXPath().compile(DockerProjectConstants.TARGET_REPOSITORY_XPATH);
            repository = xpRepo.evaluate(doc);
        } catch (XPathExpressionException e) {
            log.error("XPathExpressionException while reading pomfile", e);
        } catch (ParserConfigurationException e) {
            log.error("ParserConfigurationException while reading pomfile", e);
        } catch (SAXException e) {
            log.error("SAXException while reading pomfile", e);
        } catch (IOException e) {
            log.error("IOException while reading pomfile", e);
        }
        
        return repository;
    }

    /**
     * Method for generating UI pop up box with cutom messages.
     * 
     * @param title message box title
     * @param message message box context
     * @param style icon style
     */
    public static void showMessageBox(final String title, final String message, final int style) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                Display display = PlatformUI.getWorkbench().getDisplay();
                Shell shell = display.getActiveShell();

                MessageBox exportMsg = new MessageBox(shell, style);
                exportMsg.setText(title);
                exportMsg.setMessage(message);

                exportMsg.open();
            }
        });
    }
}