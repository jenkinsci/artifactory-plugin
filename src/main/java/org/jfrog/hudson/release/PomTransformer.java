/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.release;

import hudson.AbortException;
import hudson.FilePath;
import hudson.maven.MavenModule;
import hudson.maven.ModuleName;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Rewrites the project versions in the pom.
 *
 * @author Yossi Shaul
 */
public class PomTransformer implements FilePath.FileCallable<Object> {

    private final String newVersion;
    private final String scmUrl;
    private final Map<ModuleName, MavenModule> modulesByName;
    private boolean modified;

    /**
     * Transforms single pom file.
     *
     * @param modulesByName Map of module names to module info
     * @param newVersion    The new version to use for current module and dependencies included in the modules list
     * @param scmUrl        Scm url to use if scm element exists in the pom file
     */
    public PomTransformer(Map<ModuleName, MavenModule> modulesByName, String newVersion, String scmUrl) {
        this.modulesByName = modulesByName;
        this.newVersion = newVersion;
        this.scmUrl = scmUrl;
    }

    public Object invoke(File pomFile, VirtualChannel channel) throws IOException, InterruptedException {
        if (!pomFile.exists()) {
            throw new AbortException("Couldn't find pom file: " + pomFile);
        }

        SAXBuilder saxBuilder = createSaxBuilder();
        Document document;
        try {
            document = saxBuilder.build(pomFile);
        } catch (JDOMException e) {
            throw new IOException("Failed to parse pom: " + pomFile.getAbsolutePath());
        }

        Element rootElement = document.getRootElement();
        Namespace ns = rootElement.getNamespace();

        changeParentVersion(rootElement, ns);

        changeVersion(rootElement, ns);

        //changePropertiesVersion(rootElement, ns);

        changeDependencyManagementVersions(rootElement, ns);

        changeDependencyVersions(rootElement, ns);

        changeScm(rootElement, ns);

        if (modified) {
            FileWriter fileWriter = new FileWriter(pomFile);
            try {
                new XMLOutputter().output(document, fileWriter);
            } finally {
                IOUtils.closeQuietly(fileWriter);
            }
        }

        return null;
    }

    private void changeParentVersion(Element root, Namespace ns) {
        Element parentElement = root.getChild("parent", ns);
        if (parentElement == null) {
            return;
        }

        ModuleName parentName = extractModuleName(parentElement, ns);
        if (!modulesByName.containsKey(parentName)) {
            // parent is not part of the currently built project
            return;
        }

        setVersion(parentElement, ns);
    }

    private void changeVersion(Element rootElement, Namespace ns) {
        setVersion(rootElement, ns);
    }

    private void changeDependencyManagementVersions(Element rootElement, Namespace ns) {
        Element dependencyManagement = rootElement.getChild("dependencyManagement", ns);
        if (dependencyManagement == null) {
            return;
        }

        Element dependenciesElement = dependencyManagement.getChild("dependencies", ns);
        if (dependenciesElement == null) {
            return;
        }

        List<Element> dependencies = dependenciesElement.getChildren("dependency", ns);
        for (Element dependency : dependencies) {
            changeDependencyVersion(ns, dependency);
        }
    }

    private void changeDependencyVersions(Element rootElement, Namespace ns) {
        Element dependenciesElement = rootElement.getChild("dependencies", ns);
        if (dependenciesElement == null) {
            return;
        }

        List<Element> dependencies = dependenciesElement.getChildren("dependency", ns);
        for (Element dependency : dependencies) {
            changeDependencyVersion(ns, dependency);
        }
    }

    private void changeDependencyVersion(Namespace ns, Element dependency) {
        ModuleName moduleName = extractModuleName(dependency, ns);
        if (modulesByName.containsKey(moduleName)) {
            setVersion(dependency, ns);
        }
    }

    private void changeScm(Element rootElement, Namespace ns) {
        Element scm = rootElement.getChild("scm", ns);
        if (scm == null) {
            return;
        }
        Element connection = scm.getChild("connection", ns);
        if (connection != null) {
            connection.setText("scm:svn:" + scmUrl);
        }
        Element developerConnection = scm.getChild("developerConnection", ns);
        if (developerConnection != null) {
            developerConnection.setText("scm:svn:" + scmUrl);
        }
        Element url = scm.getChild("url", ns);
        if (url != null) {
            url.setText(scmUrl);
        }
    }

    private void setVersion(Element element, Namespace ns) {
        Element version = element.getChild("version", ns);
        if (version != null) {
            String currentVersion = version.getText();
            if (!newVersion.equals(currentVersion)) {
                version.setText(newVersion);
                modified = true;
            }
        }
    }

    private ModuleName extractModuleName(Element element, Namespace ns) {
        String groupId = element.getChildText("groupId", ns);
        String artifactId = element.getChildText("artifactId", ns);
        if (StringUtils.isBlank(groupId) || StringUtils.isBlank(artifactId)) {
            throw new IllegalArgumentException("Couldn't extract module key from: " + element);
        }
        return new ModuleName(groupId, artifactId);
    }


    static SAXBuilder createSaxBuilder() {
        SAXBuilder sb = new SAXBuilder();
        // don't validate and don't load dtd
        sb.setValidation(false);
        sb.setFeature("http://xml.org/sax/features/validation", false);
        sb.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        sb.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return sb;
    }

}
