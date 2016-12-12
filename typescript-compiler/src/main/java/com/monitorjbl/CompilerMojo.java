package com.monitorjbl;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

@Mojo(name = "compile")
public class CompilerMojo extends AbstractMojo {
  private static final String v8_version = "4.6.0";
  private static final String base_dir = "compiler";

  @Parameter(required = true)
  private List<File> sourceDirs;

  @Parameter
  private File outputDir;

  @Parameter
  private File outputFile;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    String buildDir = project.getBuild().getDirectory();
    List<String> args = new ArrayList<>();

    try {
      extractTypescriptCompiler(buildDir);

      //add source files
      sourceDirs.stream()
          .flatMap(CompilerMojo::listTypescriptFiles)
          .map(File::getAbsolutePath)
          .forEach(args::add);

      if(outputFile != null) {
        args.addAll(asList("--outFile", outputFile.getAbsolutePath()));
      } else if(outputDir != null) {
        args.addAll(asList("--outDir", outputDir.getAbsolutePath()));
      } else {
        throw new MojoFailureException("Must specify outputFile or outputDir");
      }

      runJava(buildDir, args);
    } catch(Exception e) {
      throw new MojoExecutionException("Could not run NodeJS code", e);
    }
  }

  private static int runJava(String target, List<String> args) throws IOException, InterruptedException, MojoFailureException, MojoExecutionException {
    String javaHome = System.getProperty("java.home");

    List<String> command = asList(javaHome + "/bin/java",
        "-cp", target + "/" + base_dir + "/classes:" + whichV8(target),
        "-Dtsc.command='" + transformArgs(args) + "'",
        "-Dtsc.buildDir=" + target,
        Main.class.getCanonicalName());
    System.out.println(command.stream().collect(joining(" ")));
    ProcessBuilder pb = new ProcessBuilder()
        .command(command)
        .inheritIO();
    Process proc = pb.start();
    proc.waitFor();
    return proc.exitValue();
  }

  private static Stream<File> listTypescriptFiles(File file) {
    File[] ts = file.listFiles((dir, name) -> name.endsWith(".ts"));
    return Stream.of(ts == null ? new File[0] : ts);
  }

  private static void extractTypescriptCompiler(String target) {
    new File(target + "/" + base_dir + "/typescript").mkdirs();
    new File(target + "/" + base_dir + "/jars").mkdirs();
    new File(target + "/" + base_dir + "/classes/com/monitorjbl").mkdirs();

    new Reflections("typescript", new ResourcesScanner()).getResources(Pattern.compile(".*"))
        .forEach(file -> copyResource(file, target + "/" + base_dir + "/" + file));
    new Reflections("jars", new ResourcesScanner()).getResources(Pattern.compile(".*"))
        .forEach(file -> copyResource(file, target + "/" + base_dir + "/" + file));
    new Reflections("", new ResourcesScanner()).getResources(Pattern.compile("Main\\.class")).stream()
        .filter(file -> file.equals("com/monitorjbl/Main.class"))
        .forEach(file -> copyResource(file, target + "/" + base_dir + "/classes/" + file));
  }

  private static void copyResource(String resource, String target) {
    try(InputStream is = CompilerMojo.class.getClassLoader().getResourceAsStream(resource);
        OutputStream os = new FileOutputStream(new File(target))) {
      IOUtils.copy(is, os);
    } catch(IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String whichV8(String target) throws MojoExecutionException, MojoFailureException {
    String jar = target + "/" + base_dir + "/jars/";
    if(SystemUtils.IS_OS_LINUX) {
      jar += "j2v8_linux_x86_64-" + v8_version + ".jar";
    } else if(SystemUtils.IS_OS_MAC) {
      jar += "j2v8_macosx_x86_64-" + v8_version + ".jar";
    } else if(SystemUtils.IS_OS_WINDOWS) {
      jar += "j2v8_win32_x86_64-" + v8_version + ".jar";
    } else {
      throw new MojoExecutionException("N0 J2V8 implementation available for operating system '" + System.getProperty("os.name") + "'");
    }
    return jar;
  }

  private static String transformArgs(List<String> args) {
    return args.stream()
        .map(s -> "\"" + s + "\"")
        .collect(joining(","));
  }

}
