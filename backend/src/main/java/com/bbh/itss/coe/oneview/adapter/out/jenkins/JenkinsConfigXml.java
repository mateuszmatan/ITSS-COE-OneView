package com.bbh.itss.coe.oneview.adapter.out.jenkins;

import com.bbh.itss.coe.oneview.domain.jenkins.BuildRetention;
import com.bbh.itss.coe.oneview.domain.jenkins.FolderLibrary;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsfileFacts;
import com.bbh.itss.coe.oneview.domain.jenkins.JobKind;
import com.bbh.itss.coe.oneview.domain.jenkins.JobParameter;
import com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger;
import lombok.NoArgsConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.BRANCH_SCAN;
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.OTHER;
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.SCM_POLLING;
import static com.bbh.itss.coe.oneview.domain.jenkins.JobTrigger.Kind.TIMER;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/** The config.xml of a Jenkins job: read into what OneView shows of a job, and written for the job it creates. */
@NoArgsConstructor(access = PRIVATE)
final class JenkinsConfigXml {

    static final String NOT_A_PIPELINE = "This is not a Pipeline job. OneView read what it has in common with one;"
            + " its build steps are not carried over, because the new job takes its steps from the Jenkinsfile.";
    /** An empty folder, for the folders of a new job that Jenkins does not have yet. */
    static final String FOLDER = "<?xml version='1.1' encoding='UTF-8'?>\n"
            + "<com.cloudbees.hudson.plugins.folder.Folder>\n<description>Created by ITSS COE OneView</description>\n"
            + "</com.cloudbees.hudson.plugins.folder.Folder>\n";
    private static final String MULTIBRANCH = "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";
    private static final String OWNER = "<owner class=\"" + MULTIBRANCH + "\" reference=\"../..\"/>";

    /** Reads the settings of a job from its config.xml. Never resolves anything outside the document. */
    static JenkinsJobSnapshot.JenkinsJobSnapshotBuilder read(String configXml) {
        Reader xml = new Reader(configXml);
        String root = xml.document.getDocumentElement().getNodeName();
        JobKind kind = root.equals("flow-definition") ? JobKind.PIPELINE
                : root.equals(MULTIBRANCH) ? JobKind.MULTIBRANCH : JobKind.OTHER;
        List<String> notes = new ArrayList<>();
        if (kind == JobKind.OTHER) {
            notes.add(NOT_A_PIPELINE);
        }
        return JenkinsJobSnapshot.builder().kind(kind)
                .description(xml.text("/*/description"))
                .repositoryUrl(repositoryUrl(xml))
                .credentialsId(xml.text("(//hudson.plugins.git.UserRemoteConfig/credentialsId"
                        + " | //sources//source/credentialsId)[1]"))
                .branch(branch(xml.text("(//hudson.plugins.git.BranchSpec/name)[1]")))
                .scriptPath(xml.text("(//definition/scriptPath | //factory/scriptPath)[1]"))
                .agentLabel(xml.text("/*/assignedNode"))
                .parameters(parameters(xml, notes))
                .retention(retention(xml))
                .concurrentBuildsDisabled(xml.exists(
                        "//org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty")
                        || "false".equals(xml.text("/*/concurrentBuild")))
                .triggers(triggers(xml))
                .libraries(xml.texts("//org.jenkinsci.plugins.workflow.libs.LibraryConfiguration/name"))
                .inlineScript(JenkinsfileFacts.read(xml.text("//definition/script")))
                .notes(notes);
    }

    private static String repositoryUrl(Reader xml) {
        String git = xml.text("(//hudson.plugins.git.UserRemoteConfig/url | //sources//source/remote)[1]");
        String server = xml.text("(//sources//source/serverUrl)[1]");
        String owner = xml.text("(//sources//source/repoOwner)[1]");
        String repository = xml.text("(//sources//source/repository)[1]");
        if (git != null || server == null || owner == null || repository == null) {
            return git;
        }
        return server.replaceFirst("/+$", "") + "/scm/" + owner.toLowerCase(java.util.Locale.ROOT) + "/" + repository
                + ".git";
    }

    private static String branch(String spec) {
        return spec == null ? null : trimToNull(spec.replaceFirst("^(\\*/|refs/heads/|origin/)", ""));
    }

    private static List<JobParameter> parameters(Reader xml, List<String> notes) {
        List<JobParameter> parameters = new ArrayList<>();
        for (Element definition : xml.elements("//parameterDefinitions/*")) {
            String name = xml.text("name", definition);
            JobParameter.Type type = switch (substringAfterLast("." + definition.getNodeName(), ".")) {
                case "StringParameterDefinition" -> JobParameter.Type.STRING;
                case "TextParameterDefinition" -> JobParameter.Type.TEXT;
                case "BooleanParameterDefinition" -> JobParameter.Type.BOOLEAN;
                case "ChoiceParameterDefinition" -> JobParameter.Type.CHOICE;
                case "PasswordParameterDefinition" -> JobParameter.Type.PASSWORD;
                default -> null;
            };
            if (name == null) {
                continue;
            }
            if (type == null) {
                notes.add("Parameter " + name + " is of a kind OneView does not create ("
                        + substringAfterLast("." + definition.getNodeName(), ".") + "); add it to the new job yourself.");
                continue;
            }
            parameters.add(new JobParameter(type, name, xml.text("defaultValue", definition),
                    xml.text("description", definition), xml.texts(".//string", definition)));
        }
        return parameters;
    }

    private static BuildRetention retention(Reader xml) {
        String strategy = "(//jenkins.model.BuildDiscarderProperty/strategy | /*/logRotator)[1]/";
        BuildRetention retention = new BuildRetention(xml.number(strategy + "daysToKeep"),
                xml.number(strategy + "numToKeep"));
        return retention.limited() ? retention : null;
    }

    private static List<JobTrigger> triggers(Reader xml) {
        List<JobTrigger> triggers = new ArrayList<>();
        for (Element trigger : xml.elements("//triggers/*")) {
            String name = substringAfterLast("." + trigger.getNodeName(), ".");
            String spec = xml.text("spec", trigger);
            switch (name) {
                case "SCMTrigger" -> triggers.add(new JobTrigger(SCM_POLLING, "Poll SCM", spec));
                case "TimerTrigger" -> triggers.add(new JobTrigger(TIMER, "Build periodically", spec));
                case "PeriodicFolderTrigger" -> {
                    Integer interval = xml.number("interval", trigger);
                    triggers.add(new JobTrigger(BRANCH_SCAN, "Scan the branches periodically",
                            interval == null ? spec : "every " + Math.max(1, interval / 60_000) + " minutes"));
                }
                default -> triggers.add(new JobTrigger(OTHER, name, spec));
            }
        }
        return triggers;
    }

    /** Writes the config.xml of the job to create. */
    static String write(JenkinsJobSpec job) {
        return job.kind() == JobKind.MULTIBRANCH ? multibranch(job) : pipeline(job);
    }

    private static String pipeline(JenkinsJobSpec job) {
        StringBuilder xml = new StringBuilder("<?xml version='1.1' encoding='UTF-8'?>\n<flow-definition>\n")
                .append(element("description", job.description()))
                .append("<keepDependencies>false</keepDependencies>\n<properties>\n");
        if (job.concurrentBuildsDisabled()) {
            xml.append("<org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty/>\n");
        }
        if (job.retention() != null && job.retention().limited()) {
            xml.append("<jenkins.model.BuildDiscarderProperty>\n<strategy class=\"hudson.tasks.LogRotator\">\n")
                    .append(element("daysToKeep", limit(job.retention().daysToKeep())))
                    .append(element("numToKeep", limit(job.retention().buildsToKeep())))
                    .append("<artifactDaysToKeep>-1</artifactDaysToKeep>\n<artifactNumToKeep>-1</artifactNumToKeep>\n")
                    .append("</strategy>\n</jenkins.model.BuildDiscarderProperty>\n");
        }
        if (!job.parameters().isEmpty()) {
            xml.append("<hudson.model.ParametersDefinitionProperty>\n<parameterDefinitions>\n");
            job.parameters().forEach(parameter -> xml.append(parameter(parameter)));
            xml.append("</parameterDefinitions>\n</hudson.model.ParametersDefinitionProperty>\n");
        }
        xml.append("<org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>\n<triggers>\n")
                .append("<hudson.triggers.SCMTrigger>\n").append(element("spec", job.pollSchedule()))
                .append("<ignorePostCommitHooks>false</ignorePostCommitHooks>\n</hudson.triggers.SCMTrigger>\n");
        if (job.timerSchedule() != null) {
            xml.append("<hudson.triggers.TimerTrigger>\n").append(element("spec", job.timerSchedule()))
                    .append("</hudson.triggers.TimerTrigger>\n");
        }
        return xml.append("</triggers>\n</org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>\n")
                .append("</properties>\n")
                .append("<definition class=\"org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition\">\n")
                .append("<scm class=\"hudson.plugins.git.GitSCM\">\n<configVersion>2</configVersion>\n")
                .append("<userRemoteConfigs>\n<hudson.plugins.git.UserRemoteConfig>\n")
                .append(element("url", job.repositoryUrl())).append(optional("credentialsId", job.credentialsId()))
                .append("</hudson.plugins.git.UserRemoteConfig>\n</userRemoteConfigs>\n")
                .append("<branches>\n<hudson.plugins.git.BranchSpec>\n").append(element("name", "*/" + job.branch()))
                .append("</hudson.plugins.git.BranchSpec>\n</branches>\n")
                .append("<doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>\n")
                .append("<submoduleCfg class=\"empty-list\"/>\n<extensions/>\n</scm>\n")
                .append(element("scriptPath", job.scriptPath())).append("<lightweight>true</lightweight>\n")
                .append("</definition>\n<triggers/>\n<disabled>false</disabled>\n</flow-definition>\n").toString();
    }

    private static String parameter(JobParameter parameter) {
        String type = switch (parameter.type()) {
            case STRING -> "String";
            case TEXT -> "Text";
            case BOOLEAN -> "Boolean";
            case CHOICE -> "Choice";
            case PASSWORD -> "Password";
        };
        StringBuilder xml = new StringBuilder("<hudson.model." + type + "ParameterDefinition>\n")
                .append(element("name", parameter.name())).append(optional("description", parameter.description()));
        if (parameter.type() == JobParameter.Type.CHOICE) {
            xml.append("<choices class=\"java.util.Arrays$ArrayList\">\n<a class=\"string-array\">\n");
            parameter.choices().forEach(choice -> xml.append(element("string", choice)));
            xml.append("</a>\n</choices>\n");
        } else if (parameter.type() == JobParameter.Type.BOOLEAN) {
            xml.append(element("defaultValue", String.valueOf("true".equalsIgnoreCase(parameter.defaultValue()))));
        } else if (parameter.type() != JobParameter.Type.PASSWORD) {
            xml.append(element("defaultValue", parameter.defaultValue()));
        }
        return xml.append("</hudson.model." + type + "ParameterDefinition>\n").toString();
    }

    private static String multibranch(JenkinsJobSpec job) {
        return "<?xml version='1.1' encoding='UTF-8'?>\n<" + MULTIBRANCH + ">\n<actions/>\n"
                + element("description", job.description())
                + "<properties>\n" + library(job.library()) + "</properties>\n"
                + "<folderViews class=\"jenkins.branch.MultiBranchProjectViewHolder\">\n" + OWNER + "\n</folderViews>\n"
                + "<healthMetrics/>\n"
                + "<icon class=\"jenkins.branch.MetadataActionFolderIcon\">\n" + OWNER + "\n</icon>\n"
                + "<orphanedItemStrategy"
                + " class=\"com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy\">\n"
                + "<pruneDeadBranches>true</pruneDeadBranches>\n<daysToKeep>-1</daysToKeep>\n"
                + "<numToKeep>-1</numToKeep>\n</orphanedItemStrategy>\n"
                + "<triggers>\n<com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger>\n"
                + "<spec>* * * * *</spec>\n"
                + element("interval", String.valueOf(job.scanIntervalMinutes() * 60_000L))
                + "</com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger>\n</triggers>\n"
                + "<disabled>false</disabled>\n"
                + "<sources class=\"jenkins.branch.MultiBranchProject$BranchSourceList\">\n<data>\n"
                + "<jenkins.branch.BranchSource>\n<source class=\"jenkins.plugins.git.GitSCMSource\">\n"
                + "<id>oneview</id>\n" + element("remote", job.repositoryUrl())
                + optional("credentialsId", job.credentialsId())
                + "<traits>\n<jenkins.plugins.git.traits.BranchDiscoveryTrait/>\n</traits>\n</source>\n"
                + "<strategy class=\"jenkins.branch.DefaultBranchPropertyStrategy\">\n"
                + "<properties class=\"empty-list\"/>\n</strategy>\n</jenkins.branch.BranchSource>\n</data>\n"
                + OWNER + "\n</sources>\n"
                + "<factory class=\"org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory\">\n"
                + OWNER + "\n" + element("scriptPath", job.scriptPath()) + "</factory>\n</" + MULTIBRANCH + ">\n";
    }

    private static String library(FolderLibrary library) {
        if (library == null) {
            return "";
        }
        return "<org.jenkinsci.plugins.workflow.libs.FolderLibraries>\n<libraries>\n"
                + "<org.jenkinsci.plugins.workflow.libs.LibraryConfiguration>\n" + element("name", library.name())
                + "<retriever class=\"org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever\">\n"
                + "<scm class=\"jenkins.plugins.git.GitSCMSource\">\n<id>oneview-library</id>\n"
                + element("remote", library.repositoryUrl()) + optional("credentialsId", library.credentialsId())
                + "<traits>\n<jenkins.plugins.git.traits.BranchDiscoveryTrait/>\n</traits>\n</scm>\n</retriever>\n"
                + element("defaultVersion", library.version())
                + "<implicit>false</implicit>\n<allowVersionOverride>true</allowVersionOverride>\n"
                + "<includeInChangesets>false</includeInChangesets>\n"
                + "</org.jenkinsci.plugins.workflow.libs.LibraryConfiguration>\n</libraries>\n"
                + "</org.jenkinsci.plugins.workflow.libs.FolderLibraries>\n";
    }

    private static String limit(Integer value) {
        return value == null ? "-1" : value.toString();
    }

    private static String optional(String name, String value) {
        return value == null ? "" : element(name, value);
    }

    private static String element(String name, String value) {
        return "<" + name + ">" + escape(value) + "</" + name + ">\n";
    }

    /** Escapes text for XML 1.1 and drops the control characters XML cannot carry. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        value.codePoints().forEach(point -> {
            switch (point) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (point >= 0x20 || point == '\n' || point == '\t') {
                        escaped.appendCodePoint(point);
                    }
                }
            }
        });
        return escaped.toString();
    }

    /** A config.xml parsed without document types or external entities, queried with XPath. */
    private static final class Reader {

        private final Document document;
        private final XPath xpath = XPathFactory.newInstance().newXPath();

        Reader(String configXml) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(configXml)));
            } catch (Exception e) {
                throw new IllegalStateException("Jenkins answered with a job configuration OneView cannot read: "
                        + e.getMessage(), e);
            }
        }

        String text(String expression) {
            return text(expression, document);
        }

        String text(String expression, Node from) {
            List<Element> found = elements(expression, from);
            return found.isEmpty() ? null : trimToNull(found.get(0).getTextContent());
        }

        List<String> texts(String expression) {
            return texts(expression, document);
        }

        List<String> texts(String expression, Node from) {
            return elements(expression, from).stream().map(element -> trimToNull(element.getTextContent()))
                    .filter(java.util.Objects::nonNull).toList();
        }

        Integer number(String expression) {
            return number(expression, document);
        }

        Integer number(String expression, Node from) {
            String text = text(expression, from);
            return text != null && text.matches("-?\\d{1,9}") ? Integer.valueOf(text) : null;
        }

        boolean exists(String expression) {
            return !elements(expression, document).isEmpty();
        }

        List<Element> elements(String expression) {
            return elements(expression, document);
        }

        List<Element> elements(String expression, Node from) {
            try {
                NodeList nodes = (NodeList) xpath.evaluate(expression, from, XPathConstants.NODESET);
                List<Element> elements = new ArrayList<>();
                for (int index = 0; index < nodes.getLength(); index++) {
                    if (nodes.item(index) instanceof Element element) {
                        elements.add(element);
                    }
                }
                return elements;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read " + expression + " of a Jenkins job configuration", e);
            }
        }
    }
}
