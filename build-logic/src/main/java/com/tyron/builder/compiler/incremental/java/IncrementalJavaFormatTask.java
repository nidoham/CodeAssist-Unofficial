package com.tyron.builder.compiler.incremental.java;

import com.google.common.base.Throwables;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

public class IncrementalJavaFormatTask extends Task<JavaModule> {

  private static final String TAG = "formatJava";
  private List<File> mJavaFiles;

  public IncrementalJavaFormatTask(Project project, JavaModule module, ILogger logger) {
    super(project, module, logger);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void prepare(BuildType type) throws IOException {
    mJavaFiles = new ArrayList<>();
    mJavaFiles.addAll(getJavaFiles(new File(getModule().getRootFile() + "/src/main/java")));
  }

  @Override
  public void run() throws IOException, CompilationFailedException {
    if (mJavaFiles.isEmpty()) {
      return;
    }

    try {
      File buildSettings =
          new File(
              getModule().getProjectDir(),
              ".idea/" + getModule().getRootFile().getName() + "_compiler_settings.json");
      String content = new String(Files.readAllBytes(Paths.get(buildSettings.getAbsolutePath())));

      JSONObject buildSettingsJson = new JSONObject(content);

      String applyJavaFormat =
          buildSettingsJson.optJSONObject("java").optString("applyJavaFormat", "false");

      if (Boolean.parseBoolean(applyJavaFormat)) {

        for (File mJava : mJavaFiles) {
       /*
          StringWriter out = new StringWriter();
          StringWriter err = new StringWriter();
          String text = new String(Files.readAllBytes(mJava.toPath()));

          com.google.googlejavaformat.java.Main main =
              new com.google.googlejavaformat.java.Main(
                  new PrintWriter(out, true),
                  new PrintWriter(err, true),
                  new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
          int exitCode = main.format("-");

          if (exitCode != 0) {
            getLogger().debug("Error: " + mJava.getAbsolutePath() + " " + err.toString());
            throw new CompilationFailedException(TAG + " error");
          }
        */
          String text = new String(Files.readAllBytes(mJava.toPath()));
          String formatted = null;
          try{
      // formatted =  new Formatter().formatSource(text.toString());
        formatted = com.tyron.eclipse.formatter.Formatter.format(text,0,text.length());   
     }catch(Exception e){
         getLogger().debug("Error: " + mJava.getAbsolutePath() + " " + Throwables.getStackTraceAsString(e));
            throw new CompilationFailedException(TAG + " error");
          }
         // String formatted = out.toString();
          if (formatted != null && !formatted.isEmpty()) {
            FileUtils.writeStringToFile(mJava, formatted, Charset.defaultCharset());
          }
        }
      }

    } catch (Exception e) {
      throw new CompilationFailedException(Throwables.getStackTraceAsString(e));
    }
  }

  public static Set<File> getJavaFiles(File dir) {
    Set<File> javaFiles = new HashSet<>();

    File[] files = dir.listFiles();
    if (files == null) {
      return Collections.emptySet();
    }

    for (File file : files) {
      if (file.isDirectory()) {
        javaFiles.addAll(getJavaFiles(file));
      } else {
        if (file.getName().endsWith(".java")) {
          javaFiles.add(file);
        }
      }
    }

    return javaFiles;
  }
}
