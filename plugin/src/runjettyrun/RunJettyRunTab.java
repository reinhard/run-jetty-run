/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package runjettyrun;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * Launch tab for the RunJettyRun plugin.
 * 
 * @author hillenius, James Synge
 */
public class RunJettyRunTab extends JavaLaunchTab {

    private Text fProjText;

    private Button fProjButton;
    private Text fPortText;

    private Text fSSLPortText;

    private Text fKeystoreText;
    private Button fKeystoreButton;
    private Text fKeyPasswordText;

    private Text fPasswordText;
    private Text fContextText;

    private Text fWebAppDirText;

    private Button fWebappDirButton;

    /**
     * Construct.
     */
    public RunJettyRunTab() {
    }

    public void createControl(Composite parent) {
        Composite comp = new Composite(parent, SWT.NONE);
        comp.setFont(parent.getFont());

        GridData gd = new GridData(1);
        gd.horizontalSpan = GridData.FILL_BOTH;
        comp.setLayoutData(gd);

        GridLayout layout = new GridLayout(1, false);
        layout.verticalSpacing = 0;
        comp.setLayout(layout);

        this.createProjectEditor(comp);
        this.createVerticalSpacer(comp, 1);
        this.createPortEditor(comp);
        this.createVerticalSpacer(comp, 1);
        this.createJettyOptionsEditor(comp);
        this.createVerticalSpacer(comp, 1);
        this.setControl(comp);
        // PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(),
        // IJavaDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG_MAIN_TAB);

        return;
    }

    @Override
    public Image getImage() {
        return Plugin.getJettyIcon();
    }

    @Override
    public String getMessage() {
        return "Create a configuration to launch a web application with Jetty.";
    }

    public String getName() {
        return "Jetty";
    }

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        super.initializeFrom(configuration);
        try {
            this.fProjText.setText(configuration.getAttribute(ATTR_PROJECT_NAME, ""));

            this.fPortText.setText(configuration.getAttribute(Plugin.ATTR_PORT, ""));

            this.fSSLPortText.setText(configuration.getAttribute(Plugin.ATTR_SSL_PORT, ""));
            this.fKeystoreText.setText(configuration.getAttribute(Plugin.ATTR_KEYSTORE, ""));
            this.fPasswordText.setText(configuration.getAttribute(Plugin.ATTR_PWD, ""));
            this.fKeyPasswordText.setText(configuration.getAttribute(Plugin.ATTR_KEY_PWD, ""));

            this.fContextText.setText(configuration.getAttribute(Plugin.ATTR_CONTEXT, ""));
            this.fWebAppDirText.setText(configuration.getAttribute(Plugin.ATTR_WEBAPPDIR, ""));
        } catch (CoreException e) {
            Plugin.logError(e);
        }
    }

    @Override
    public boolean isValid(ILaunchConfiguration config) {
        this.setErrorMessage(null);
        this.setMessage(null);

        String projectName = this.fProjText.getText().trim();
        IProject project = null;
        if (projectName.length() > 0) {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IStatus status = workspace.validateName(projectName, IResource.PROJECT);
            if (status.isOK()) {
                project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
                if (!project.exists()) {
                    this.setErrorMessage(MessageFormat.format("Project {0} does not exist", projectName));
                    this.fWebappDirButton.setEnabled(false);
                    return false;
                }
                if (!project.isOpen()) {
                    this.setErrorMessage(MessageFormat.format("Project {0} is closed", projectName));
                    this.fWebappDirButton.setEnabled(false);
                    return false;
                }
            } else {
                this.setErrorMessage(MessageFormat.format("Illegal project name: {0}", status.getMessage()));
                this.fWebappDirButton.setEnabled(false);
                return false;
            }
            this.fWebappDirButton.setEnabled(true);
        } else {
            this.setErrorMessage("No project selected");
            return false;
        }
        String directory = this.fWebAppDirText.getText().trim();
        if (!"".equals(directory.trim())) {
            IFolder folder = project.getFolder(directory);
            if (!folder.exists()) {
                this.setErrorMessage(MessageFormat.format("Folder {0} does not exist in project {1}", directory, project.getName()));
                return false;
            }
            IFile file = project.getFile(new Path(directory + "/WEB-INF/web.xml"));
            if (!file.exists()) {
                this.setErrorMessage(MessageFormat.format(
                        "Directory {0} does not contain WEB-INF/web.xml; it is not a valid web application directory", directory));
                return false;
            }
        } else {
            this.setErrorMessage("Web application directory is not set");
            return false;
        }

        String port = this.fPortText.getText().trim();
        String sslPort = this.fSSLPortText.getText().trim();
        if (port.length() == 0 && sslPort.length() == 0) {
            this.setErrorMessage("Must specify at least one port");
            return false;
        }
        if (this.isInvalidPort(port)) {
            return false;
        }
        if (this.isInvalidPort(sslPort)) {
            return false;
        }

        if (sslPort.length() > 0) {
            // Validate that we have the necessary key store info.
            String keystore = this.fKeystoreText.getText().trim();
            String keyPwd = this.fKeyPasswordText.getText().trim();
            String password = this.fPasswordText.getText().trim();
            if (keystore.length() == 0) {
                this.setErrorMessage("Keystore location is not set");
                return false;
            } else if (!new File(keystore).isFile()) {
                this.setErrorMessage(MessageFormat.format("Keystore file {0} does not exist", keystore));
                return false;
            }
            if (keyPwd.length() == 0) {
                this.setErrorMessage("Key Password is not set");
                return false;
            }
            if (password.length() == 0) {
                this.setErrorMessage("Password is not set");
                return false;
            }
        }

        return true;
    }

    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(ATTR_PROJECT_NAME, this.fProjText.getText());

        configuration.setAttribute(Plugin.ATTR_PORT, this.fPortText.getText());

        configuration.setAttribute(Plugin.ATTR_SSL_PORT, this.fSSLPortText.getText());
        configuration.setAttribute(Plugin.ATTR_KEYSTORE, this.fKeystoreText.getText());
        configuration.setAttribute(Plugin.ATTR_PWD, this.fPasswordText.getText());
        configuration.setAttribute(Plugin.ATTR_KEY_PWD, this.fKeyPasswordText.getText());

        configuration.setAttribute(Plugin.ATTR_CONTEXT, this.fContextText.getText());
        configuration.setAttribute(Plugin.ATTR_WEBAPPDIR, this.fWebAppDirText.getText());

        return;
    }

    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {

        IJavaElement javaElement = this.getContext();
        if (javaElement != null) {
            this.initializeJavaProject(javaElement, configuration);
        } else {
            configuration.setAttribute(ATTR_PROJECT_NAME, "");
        }

        configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, Plugin.BOOTSTRAP_CLASS_NAME);

        // set the class path provider so that Jetty and the bootstrap jar are
        // added to the run time class path. Value has to be the same as the one
        // defined for the extension point
        configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, "RunJettyRunWebAppClassPathProvider");

        // get the name for this launch configuration
        String launchConfigName = "";
        try {
            // try to base the launch config name on the current project
            launchConfigName = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
        } catch (CoreException e) {
            // ignore
        }
        if (launchConfigName == null || launchConfigName.length() == 0) {
            // if no project name was found, base on a default name
            launchConfigName = "Jetty Webapp";
        }
        // generate an unique name (e.g. myproject(2))
        launchConfigName = this.getLaunchConfigurationDialog().generateName(launchConfigName);
        configuration.rename(launchConfigName); // and rename the config

        configuration.setAttribute(Plugin.ATTR_PORT, "8080");
        configuration.setAttribute(Plugin.ATTR_SSL_PORT, "8443");

        File userHomeDir = new File(System.getProperty("user.home"));
        File keystoreFile = new File(userHomeDir, ".keystore");
        String keystore = keystoreFile.getAbsolutePath();

        configuration.setAttribute(Plugin.ATTR_KEYSTORE, keystore);
        configuration.setAttribute(Plugin.ATTR_PWD, "changeit");
        configuration.setAttribute(Plugin.ATTR_KEY_PWD, "changeit");

        configuration.setAttribute(Plugin.ATTR_CONTEXT, "/");
        configuration.setAttribute(Plugin.ATTR_WEBAPPDIR, "");

        return;
    }

    protected void handleBrowseFileSystem() {
        String current = this.fKeystoreText.getText();
        if (current == null || current.trim().equals("")) {
            String userHome = System.getProperty("user.home");
            String fileSeparator = System.getProperty("file.separator");
            current = userHome + fileSeparator + ".keystore";
        }
        FileDialog dialog = new FileDialog(this.getControl().getShell());
        dialog.setFilterExtensions(new String[] {"*.keystore", "*"}); //$NON-NLS-1$
        dialog.setFilterPath(this.fKeystoreText.getText());
        dialog.setText("Choose a keystore file");
        String res = dialog.open();
        if (res != null) {
            this.fKeystoreText.setText(res);
        }
    }

    protected void setKeystoreEnabled(boolean b) {
        this.fKeystoreText.setEnabled(b);
        this.fKeystoreButton.setEnabled(b);
        this.fPasswordText.setEnabled(b);
        this.fKeyPasswordText.setEnabled(b);

        return;
    }

    private IJavaProject chooseJavaProject() {
        ILabelProvider labelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(this.getShell(), labelProvider);
        dialog.setTitle("Project Selection");
        dialog.setMessage("Select a project to constrain your search.");
        try {
            dialog.setElements(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects());
        } catch (JavaModelException jme) {
            Plugin.logError(jme);
        }

        IJavaProject javaProject = null;
        String projectName = this.fProjText.getText().trim();
        if (projectName.length() > 0) {
            javaProject = JavaCore.create(this.getWorkspaceRoot()).getJavaProject(projectName);
        }
        if (javaProject != null) {
            dialog.setInitialSelections(new Object[] {javaProject});
        }
        if (dialog.open() == Window.OK) {
            return (IJavaProject) dialog.getFirstResult();
        }
        return null;
    }

    private void chooseWebappDir() {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(this.fProjText.getText());
        ContainerSelectionDialog dialog = new ContainerSelectionDialog(this.getShell(), project, false,
        "Select Web Application Directory");
        dialog.setTitle("Folder Selection");
        if (project != null) {
            IPath path = project.getFullPath();
            dialog.setInitialSelections(new Object[] {path});
        }
        dialog.showClosedProjects(false);
        dialog.open();
        Object[] results = dialog.getResult();
        if ((results != null) && (results.length > 0) && (results[0] instanceof IPath)) {
            IPath path = (IPath) results[0];
            path = path.removeFirstSegments(1);
            String containerName = path.makeRelative().toString();
            this.fWebAppDirText.setText(containerName);
        }
    }

    private GridData createHFillGridData() {
        GridData gd = new GridData();
        gd.horizontalAlignment = SWT.FILL;
        gd.grabExcessHorizontalSpace = true;
        return gd;
    }

    /**
     * Creates the widgets for specifying the directory, context and port for the web application.
     * 
     * @param parent the parent composite
     */
    private void createJettyOptionsEditor(Composite parent) {
        Font font = parent.getFont();
        Group group = new Group(parent, SWT.NONE);
        group.setText("Web Application");
        GridData gd = this.createHFillGridData();
        group.setLayoutData(gd);
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        group.setLayout(layout);
        group.setFont(font);

        // Row 1: "Context", Text field (2 columns)
        new Label(group, SWT.LEFT).setText("Context");

        this.fContextText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fContextText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        gd.horizontalSpan = 2;
        this.fContextText.setLayoutData(gd);
        this.fContextText.setFont(font);

        // Row 2: "WebApp dir", Text field, "Browse..." Button
        new Label(group, SWT.LEFT).setText("WebApp dir");
        this.fWebAppDirText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fWebAppDirText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        this.fWebAppDirText.setLayoutData(gd);
        this.fWebAppDirText.setFont(font);

        this.fWebappDirButton = this.createPushButton(group, "&Browse...", null);
        this.fWebappDirButton.addSelectionListener(new ButtonListener() {

            public void widgetSelected(SelectionEvent e) {
                RunJettyRunTab.this.chooseWebappDir();
            }
        });
        this.fWebappDirButton.setEnabled(false);
        gd = new GridData();
        this.fWebappDirButton.setLayoutData(gd);

        return;
    }

    /**
     * Creates the widgets for specifying the ports:
     * 
     * HTTP Port: Text....... HTTPS Port: Text....... Keystore: Text.................. Browse Button Store Password:
     * Text.. Key Password: Text.....
     * 
     * @param parent the parent composite
     */
    private void createPortEditor(Composite parent) {
        // Create group, container for widgets
        Font font = parent.getFont();
        Group group = new Group(parent, SWT.NONE);
        group.setText("Ports");
        GridData gd = this.createHFillGridData();
        group.setLayoutData(gd);
        GridLayout layout = new GridLayout();
        layout.numColumns = 4;
        group.setLayout(layout);
        group.setFont(font);

        // HTTP and HTTPS ports

        new Label(group, SWT.LEFT).setText("HTTP");

        this.fPortText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fPortText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        this.fPortText.setLayoutData(gd);
        this.fPortText.setFont(font);
        this.fPortText.setTextLimit(5);
        this.setWidthForSampleText(this.fPortText, " 65535 ");

        Label lbl = new Label(group, SWT.LEFT);
        lbl.setText("HTTPS");
        gd = new GridData();
        gd.horizontalAlignment = SWT.RIGHT;
        lbl.setLayoutData(gd);

        this.fSSLPortText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fSSLPortText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                if (RunJettyRunTab.this.fSSLPortText.getText().trim().length() == 0) {
                    RunJettyRunTab.this.setKeystoreEnabled(false);
                } else {
                    RunJettyRunTab.this.setKeystoreEnabled(true);
                }
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        this.fSSLPortText.setLayoutData(gd);
        this.fSSLPortText.setFont(font);

        // keystore

        new Label(group, SWT.LEFT).setText("Keystore");
        this.fKeystoreText = new Text(group, SWT.SINGLE | SWT.BORDER);

        this.fKeystoreText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        gd.horizontalSpan = 2;
        this.fKeystoreText.setLayoutData(gd);
        this.fKeystoreText.setFont(font);
        this.fKeystoreText.setEnabled(false);

        this.fKeystoreButton = this.createPushButton(group, "&Browse...", null);
        this.fKeystoreButton.addSelectionListener(new ButtonListener() {

            public void widgetSelected(SelectionEvent e) {
                RunJettyRunTab.this.handleBrowseFileSystem();
            }
        });
        this.fKeystoreButton.setEnabled(false);
        gd = new GridData();
        this.fKeystoreButton.setLayoutData(gd);

        // Password and Key Password (not sure exactly how used by keystore)

        new Label(group, SWT.LEFT).setText("Password");
        this.fPasswordText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fPasswordText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        this.fPasswordText.setLayoutData(gd);
        this.fPasswordText.setFont(font);
        this.fPasswordText.setEnabled(false);

        new Label(group, SWT.LEFT).setText("Key Password");
        this.fKeyPasswordText = new Text(group, SWT.SINGLE | SWT.BORDER);
        this.fKeyPasswordText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        gd = this.createHFillGridData();
        this.fKeyPasswordText.setLayoutData(gd);
        this.fKeyPasswordText.setFont(font);
        this.fKeyPasswordText.setEnabled(false);

        return;
    }

    /**
     * Creates the widgets for specifying a main type.
     * 
     * @param parent the parent composite
     */
    private void createProjectEditor(Composite parent) {
        Font font = parent.getFont();
        Group group = new Group(parent, SWT.NONE);
        group.setText("Project");
        GridData gd = this.createHFillGridData();
        group.setLayoutData(gd);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        group.setLayout(layout);
        group.setFont(font);
        this.fProjText = new Text(group, SWT.SINGLE | SWT.BORDER);
        gd = this.createHFillGridData();
        this.fProjText.setLayoutData(gd);
        this.fProjText.setFont(font);
        this.fProjText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                RunJettyRunTab.this.updateLaunchConfigurationDialog();
            }
        });
        this.fProjButton = this.createPushButton(group, "&Browse...", null);
        this.fProjButton.addSelectionListener(new ButtonListener() {

            public void widgetSelected(SelectionEvent e) {
                RunJettyRunTab.this.handleProjectButtonSelected();
            }
        });
    }

    private IWorkspaceRoot getWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    private void handleProjectButtonSelected() {
        IJavaProject project = this.chooseJavaProject();
        if (project == null) {
            return;
        }
        String projectName = project.getElementName();
        this.fProjText.setText(projectName);
    }

    private boolean isInvalidPort(String s) {
        if (s.length() == 0) {
            return false;
        }
        try {
            int p = Integer.parseInt(s);
            if (1 <= p && p <= 65535) {
                return false;
            }
        } catch (NumberFormatException e) {
        }
        this.setErrorMessage(MessageFormat.format("Not a valid TCP port number: {0}", s));
        return true;
    }

    private void setWidthForSampleText(Text control, String sampleText) {
        GC gc = new GC(control);
        try {
            Point sampleSize = gc.textExtent(sampleText);
            Point currentSize = control.getSize();
            sampleSize.y = currentSize.y;
            control.setSize(sampleSize);
            return;
        } finally {
            gc.dispose();
        }
    }

    private static abstract class ButtonListener implements SelectionListener {

        public void widgetDefaultSelected(SelectionEvent e) {
        }
    }
}
