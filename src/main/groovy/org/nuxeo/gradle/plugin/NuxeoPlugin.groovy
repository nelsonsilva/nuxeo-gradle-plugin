package org.nuxeo.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

import org.gradle.api.logging.Logger

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.nuxeo.seam.AnnotationProcessor.SEAM_BEANS;

class NuxeoPlugin implements Plugin<Project> {

    private static final String SEAM_ANNOTATION_CLASS_NAME = "org.jboss.seam.annotations.Name";

    private static final String DEV_BUNDLES_FILENAME = "dev.bundles";

    private static final String NXSERVER_FOLDER_NAME = "nxserver";

    private static final String POJO_BIN_DIRECTORY_NAME = "pojo-bin";

    private static final String SEAM_BIN_DIRECTORY_NAME = "seam-bin";

    Project project

    Logger log

    void apply(Project project) {

        project.apply plugin: 'java'

        this.project = project
        this.log = project.logger

        // Add the 'nuxeo' extension object
        project.extensions.create("nuxeo", NuxeoPluginExtension)

        def moduleDirectory = project.file(".")
        def moduleDirectoryPath = moduleDirectory.getCanonicalPath();

        def annotationProcessorOutDir = "${project.buildDir}/generated-src/builders"
        def seamBeansFilePath = "$annotationProcessorOutDir/$SEAM_BEANS"

        def devBundlesContent = [];

        project.repositories {
            //flatDir { dirs "${nuxeo.sdk}/nxserver/lib", "${nuxeo.sdk}/nxserver/bundles" }
            //flatDir { dirs "${nuxeo.sdk}/sdk/sources" }
            maven { url "http://maven.nuxeo.org/nexus/content/groups/public" }
            maven { url "http://maven.nuxeo.org/nexus/content/groups/public-snapshot" }
            mavenCentral()
            mavenLocal()
        }

        // Quick hack to fix dep on joda-time
        project.configurations {
            compile.exclude module: 'joda-time'
        }
        project.dependencies {
            compile 'joda-time:joda-time:1.6.0:osgi@jar'
        }

        // Move compiled classes to pojo-bin
        project.task('prepareCompiledClassesAndResources', dependsOn:['compileJava']) << {

            log.info "Nuxeo HotReload"
            log.info "Nuxeo SDK = ${project.nuxeo.sdk}"

            def pojoBinDirectory = createDirectory("$moduleDirectoryPath/$POJO_BIN_DIRECTORY_NAME")

            copyDirContent(project.file("${project.buildDir}/classes"), pojoBinDirectory)

            devBundlesContent << "bundle:$moduleDirectoryPath/$POJO_BIN_DIRECTORY_NAME/main"
        }
        //project.tasks.prepareCompiledClassesAndResources.outputs.dir project.file("$moduleDirectoryPath/$POJO_BIN_DIRECTORY_NAME")


        // Move Seam classes to seam-bin
        project.task('prepareSeamClasses', dependsOn:['prepareCompiledClassesAndResources']) << {
            def seamBinDirectory = project.file("$moduleDirectoryPath/$SEAM_BIN_DIRECTORY_NAME")
            project.file("$seamBeansFilePath").eachLine { seamClass ->
                def classFilename = seamClass.replace(".", "/") + ".class"
                def classFile = project.file("$moduleDirectoryPath/$POJO_BIN_DIRECTORY_NAME/main/$classFilename")
                def targetDirPath = project.file("$seamBinDirectory/main/$classFilename").canonicalPath
                def targetDir = project.file(targetDirPath)
                targetDir.mkdirs();
                log.info "Moving $classFile to $targetDir"
                classFile.renameTo(new File(targetDir, classFilename));
            }
            if (seamBinDirectory.exists()) {
                devBundlesContent << "seam:$moduleDirectoryPath/$SEAM_BIN_DIRECTORY_NAME/main"
            }
        }
        //project.tasks.prepareSeamClasses.outputs.dir project.file("$moduleDirectoryPath/$SEAM_BIN_DIRECTORY_NAME")

        // Change compileJava to run our annotation processor
        project.tasks.withType(JavaCompile) { JavaCompile compile ->
            // Defer actually creating generated-src/builders until task execution
            compile.doFirst {
                project.mkdir(annotationProcessorOutDir);
            }

            if (compile.name == "compileJava") {
                // register the annotation processor result as task output
                compile.outputs.file(project.file("$seamBeansFilePath"))

                compile.options.compilerArgs += ["-s", "$annotationProcessorOutDir"]
                compile.options.compilerArgs += ["-processor", "org.nuxeo.seam.AnnotationProcessor"]
                compile.options.compilerArgs += ["-processorpath", "buildSrc/build/classes/main/"]
            }
        }

        project.task('prepareResourceBundles', dependsOn:['prepareCompiledClassesAndResources']) << {
            project.file("src/main/resources/OSGI-INF/l10n")?.eachFile { resource ->
                devBundlesContent << "resourceBundleFragment:$resource"
            }
        }

        project.task('nuxeo-reload', dependsOn:['prepareResourceBundles','prepareSeamClasses']) << {

            def devBundlesFile = new File("${project.nuxeo.sdk}/$NXSERVER_FOLDER_NAME/$DEV_BUNDLES_FILENAME")

            if (devBundlesFile.exists()) {

                // Add the previous content to ours and get the unique lines
                devBundlesContent = (devBundlesFile.readLines() + devBundlesContent).unique()

                // delete the old file
                devBundlesFile.delete()
            }

            devBundlesFile << devBundlesContent.join("\n")
        }

        project.task('nuxeo-configure', description: 'Configure the nuxeo SDK') << {
            nuxeoConf = new File("${project.nuxeo.sdk}/bin/nuxeo.conf")
            if (!nuxeoConf.exists()) {
                log.error "$nuxeoConf file does not exist"
                return false
            }

            def pattern = Pattern.compile('^#?nuxeo\\.templates=(.*)$');

            def lines = nuxeoConf.readLines().collect { String line ->
                Matcher matcher = pattern.matcher(line)
                if (matcher.matches()) {
                    String value = matcher.group(1);
                    return "nuxeo.templates=$value${value.size() > 0 ? "," : ""}sdk";
                }
                return line
            }

            print ("NEW NUXEO.CONF = \n" + lines.join("\n"))
            //nuxeoConf << lines.join("\n")
        }

        // Expose nuxeoctl commands as tasks
        ["start", "stop"].each { cmd ->
            project.task("nuxeo-$cmd") << { nuxeoctl cmd }
        }

        //project.task('nuxeo-shell') << {
        //    run "java -cp ${project.nuxeo.sdk}/client/nuxeo-shell-${project.nuxeo.version}.jar org.nuxeo.shell.Main"
        //}

        project.task('nuxeo-shell-ui') << {
            run("java -jar ${project.nuxeo.sdk}/client/nuxeo-shell-${project.nuxeo.version}.jar", false)
        }
    }


    private File createDirectory(String Path) {
        def dir = new File(Path);
        dir.deleteDir();
        dir.mkdir();
        return dir;
    }

    private void copyDirContent(File src, File dst) {
        project.ant.copy(todir: "$dst") {
            fileset(dir : src )
        }
    }

    private void run(cmd, wait = true) {
        def proc = cmd.execute()
        if (wait) {
          proc.in.eachLine {line -> print line}
          proc.err.eachLine {line -> log.error line}
          proc.waitFor()
        }
    }

    private void nuxeoctl(cmd) {
        run "${project.nuxeo.sdk}/bin/nuxeoctl $cmd"
    }

    private boolean isConfigured(List<String> nuxeoConfLines) {
        Pattern pattern = Pattern.compile('^(nuxeo\\.templates=(.*))$');
        for (String line : nuxeoConfLines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String value = matcher.group(2);
                if (value.equals("sdk") || value.contains("sdk,")
                        || value.contains(",sdk")) {
                    return true;
                }
            }
        }
        return false;
    }

}

class NuxeoPluginExtension {
    def String sdk = ''
    def String version = '5.7-SNAPSHOT'
    def List<String> userLibraries = []
}