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

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathProvider;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.StandardClasspathProvider;
import org.eclipse.jdt.launching.VMRunnerConfiguration;

/**
 * Launch configuration type for Jetty. Based on org.eclipse.jdt.launching.JavaLaunchDelegate.
 * 
 * @author hillenius
 */
public class JettyLaunchConfigurationType extends AbstractJavaLaunchConfigurationDelegate {

    public JettyLaunchConfigurationType() {
    }

    @SuppressWarnings("unchecked")
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        monitor.beginTask(MessageFormat.format("{0}...", configuration.getName()), 3); //$NON-NLS-1$
        // check for cancellation
        if (monitor.isCanceled()) {
            return;
        }
        try {
            monitor.subTask("verifying installation");

            String mainTypeName = Plugin.BOOTSTRAP_CLASS_NAME;
            IVMRunner runner = this.getVMRunner(configuration, mode);

            File workingDir = this.verifyWorkingDirectory(configuration);
            String workingDirName = null;
            if (workingDir != null) {
                workingDirName = workingDir.getAbsolutePath();
            }

            // Environment variables
            String[] envp = this.getEnvironment(configuration);

            // Program & VM arguments
            String pgmArgs = this.getProgramArguments(configuration);
            String vmArgs = this.getVMArguments(configuration);
            ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

            // VM-specific attributes
            Map vmAttributesMap = this.getVMSpecificAttributesMap(configuration);

            // Class paths
            String[] classpath = this.getClasspath(configuration);
            String[] webAppClasspathArray = this.getProjectClasspath(configuration);
            String webAppClasspath = null;
            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < webAppClasspathArray.length; i++) {
                    String path = webAppClasspathArray[i];
                    if (sb.length() > 0) {
                        sb.append(File.pathSeparator);
                    }
                    sb.append(path);
                }
                webAppClasspath = sb.toString();
            }

            // Create VM configuration
            VMRunnerConfiguration runConfig = new VMRunnerConfiguration(mainTypeName, classpath);
            runConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
            runConfig.setEnvironment(envp);

            List<String> runtimeVmArgs = this.getJettyArgs(configuration);
            runtimeVmArgs.add("-Drjrclasspath=" + webAppClasspath);
            runtimeVmArgs.addAll(Arrays.asList(execArgs.getVMArgumentsArray()));

            runConfig.setVMArguments(runtimeVmArgs.toArray(new String[runtimeVmArgs.size()]));
            runConfig.setWorkingDirectory(workingDirName);
            runConfig.setVMSpecificAttributesMap(vmAttributesMap);

            // Boot path
            runConfig.setBootClassPath(this.getBootpath(configuration));

            // check for cancellation
            if (monitor.isCanceled()) {
                return;
            }

            // stop in main
            this.prepareStopInMain(configuration);

            // done the verification phase
            monitor.worked(1);

            monitor.subTask("Creating source locator");
            // set the default source locator if required
            this.setDefaultSourceLocator(launch, configuration);
            monitor.worked(1);

            // Launch the configuration - 1 unit of work
            runner.run(runConfig, launch, monitor);

            // check for cancellation
            if (monitor.isCanceled()) {
                return;
            }
        } finally {
            monitor.done();
        }
    }

    private void addOptionalAttr(ILaunchConfiguration configuration, List<String> runtimeVmArgs, String cfgAttr, String argName)
    throws CoreException {
        String value = configuration.getAttribute(cfgAttr, "");
        if (value.length() == 0) {
            return;
        }
        String arg = "-Drjr" + argName + "=" + value;
        runtimeVmArgs.add(arg);
        return;
    }

    private List<String> getJettyArgs(ILaunchConfiguration configuration) throws CoreException {

        List<String> runtimeVmArgs = new ArrayList<String>();

        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_CONTEXT, "context");
        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_WEBAPPDIR, "webapp");

        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_PORT, "port");

        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_SSL_PORT, "sslport");
        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_KEYSTORE, "keystore");
        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_KEY_PWD, "keypassword");
        this.addOptionalAttr(configuration, runtimeVmArgs, Plugin.ATTR_PWD, "password");

        return runtimeVmArgs;
    }

    /**
     * Returns the class path to be used by the web app context (not by Jetty, or as JRE Bootstrap).
     * <p>
     * Added by James Synge.
     * 
     * Copied from {@link AbstractJavaLaunchConfigurationDelegate} so that I can eliminate everything that should be in
     * WEB-INF/lib, but is not supposed to be in the project's classpath.
     * 
     * (non-Javadoc)
     * 
     * @see org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate#getClasspath(org.eclipse.debug.core.ILaunchConfiguration)
     */
    private String[] getProjectClasspath(ILaunchConfiguration configuration) throws CoreException {

        IJavaProject proj = JavaRuntime.getJavaProject(configuration);
        if (proj == null) {
            Plugin.logError("No project!");
            return new String[0];
        }

        IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(proj);

        // Remove JRE entry/entries.

        IRuntimeClasspathEntry stdJreEntry = JavaRuntime.computeJREEntry(configuration);
        IRuntimeClasspathEntry projJreEntry = JavaRuntime.computeJREEntry(proj);
        List<IRuntimeClasspathEntry> entryList = new ArrayList<IRuntimeClasspathEntry>(entries.length);

        for (int i = 0; i < entries.length; i++) {
            IRuntimeClasspathEntry entry = entries[i];
            if (entry.equals(stdJreEntry)) {
                continue;
            }
            if (entry.equals(projJreEntry)) {
                continue;
            }
            entryList.add(entry);
        }

        // Resolve the entries to actual file/folder locations.

        entries = entryList.toArray(new IRuntimeClasspathEntry[0]);

        IRuntimeClasspathProvider provider = new StandardClasspathProvider();
        entries = provider.resolveClasspath(entries, configuration);

        // entries = JavaRuntime.resolveRuntimeClasspath(entries, configuration);

        Set<String> locations = new LinkedHashSet<String>();
        for (int i = 0; i < entries.length; i++) {
            IRuntimeClasspathEntry entry = entries[i];
            if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
                String location = entry.getLocation();
                if (location != null) {
                    locations.add(location);
                }
            }
        }

        return locations.toArray(new String[locations.size()]);
    }
}
