/*
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

package com.google.devtools.j2objc.javac;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.ast.CompilationUnit;
import com.google.devtools.j2objc.file.InputFile;
import com.google.devtools.j2objc.file.RegularInputFile;
import com.google.devtools.j2objc.pipeline.ProcessingContext;
import com.google.devtools.j2objc.util.ErrorUtil;
import com.google.devtools.j2objc.util.FileUtil;
import com.google.devtools.j2objc.util.Parser;
import com.google.devtools.j2objc.util.PathClassLoader;
import com.google.devtools.j2objc.util.SourceVersion;
import com.google.devtools.j2objc.util.TypeUtil;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.MetadataParser;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;

/**
 * Parser front-end that uses javac.
 *
 * @author Tom Ball
 */
public class JavacParser extends Parser {

  private StandardJavaFileManager fileManager;

  public JavacParser(Options options){
    super(options);
  }

  @Override
  public String version() {
    // Avoid using private API (Java 9+) to get version string.
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ToolProvider.getSystemJavaCompiler().run(null, null, out, "-version");
      return out.toString("UTF-8").trim();
    } catch (UnsupportedEncodingException e) {
      return "javac version not available";
    }
  }

  @Override
  public void setEnableDocComments(boolean enable) {
    // Ignore, since JavacTaskImpl always enables them.
  }

  @Override
  public CompilationUnit parse(InputFile file) {
    try {
      if (file.getUnitName().endsWith(".java")) {
        if (file instanceof RegularInputFile) {
          // Avoid creating an in-memory file.
          CompilationUnit[] result = new CompilationUnit[1];
          Parser.Handler handler = (String path, CompilationUnit unit) -> result[0] = unit;
          parseFiles(Collections.singletonList(file.getAbsolutePath()), handler,
              options.getSourceVersion());
          return result[0];
        }
        String source = options.fileUtil().readFile(file);
        return parse(null, file.getUnitName(), source);
      } else {
        assert options.translateClassfiles();
        JavacEnvironment parserEnv =
            createEnvironment(Collections.emptyList(), Collections.emptyList(), false);
        if (true) {
        	return parseDecompiledClass(parserEnv, file);
        }
        else {
        	return ClassFileConverter.convertClassFile(options, parserEnv, file);
        }
      }
    } catch (IOException e) {
      ErrorUtil.error(e.getMessage());
      return null;
    }
  }
  
  
  private static TypeReference lookupType(String path, ITypeLoader loader) {
	  MetadataSystem metadataSystem = new MetadataSystem(loader);
	  /* Hack to get around classes whose descriptors clash with primitive types. */
	  if (path.length() == 1) {
		  MetadataParser parser = new MetadataParser(IMetadataResolver.EMPTY);
		  return metadataSystem.resolve(parser.parseTypeDescriptor(path));
	  }
	  return metadataSystem.lookupType(path);
  }
  
  private CompilationUnit parseDecompiledClass(JavacEnvironment parserEnv, InputFile file) throws IOException { /*ARGC ++*/
    String fullPath = file.getAbsolutePath();
    int pos = fullPath.lastIndexOf(file.getUnitName());
    if (pos < 0) pos = fullPath.length();
    String rootPath = fullPath.substring(0, pos);

    String path = file.getUnitName().substring(0, file.getUnitName().length() - 6);
    ITypeLoader loader;
    loader = new JarTypeLoader(new JarFile(rootPath));
    //loader = new InputTypeLoader();
    TypeReference typeRef = lookupType(path, loader);

    TypeDefinition resolvedType = null;
    if (typeRef == null || ((resolvedType = typeRef.resolve()) == null)) {
      throw new RuntimeException("Unable to resolve type.");
    }
    DecompilerSettings settings = DecompilerSettings.javaDefaults();
    settings.setShowSyntheticMembers(true);
    StringWriter stringwriter = new StringWriter();
    DecompilationOptions decompilationOptions;
    decompilationOptions = new DecompilationOptions();
    decompilationOptions.setSettings(settings);
    decompilationOptions.setFullDecompilation(true);
    PlainTextOutput plainTextOutput = new PlainTextOutput(stringwriter);
    plainTextOutput.setUnicodeOutputEnabled(
        decompilationOptions.getSettings().isUnicodeOutputEnabled());
    settings.getLanguage().decompileType(resolvedType, plainTextOutput,
        decompilationOptions);
    String decompiledSource = stringwriter.toString();
    System.out.println(decompiledSource);
    return parse(null, file.getUnitName(), decompiledSource);
  }

  @Override
  public CompilationUnit parse(String mainType, String path, String source) {
    try {
      JavacEnvironment parserEnv = createEnvironment(path, source);
      JavacTask task = parserEnv.task();
      CompilationUnitTree unit = task.parse().iterator().next();
      task.analyze();
      processDiagnostics(null, parserEnv.diagnostics());
      return TreeConverter.convertCompilationUnit(options, parserEnv, unit);
    } catch (IOException e) {
      ErrorUtil.fatalError(e, path);
    }
    return null;
  }

  private StandardJavaFileManager getFileManager(JavaCompiler compiler,
      DiagnosticCollector<JavaFileObject> diagnostics) throws IOException {
	  fileManager = new FileManagerProxy(compiler.getStandardFileManager(diagnostics, null, options.fileUtil().getCharset()));
    addPaths(StandardLocation.CLASS_PATH, classpathEntries, fileManager);
    addPaths(StandardLocation.SOURCE_PATH, sourcepathEntries, fileManager);
    addPaths(StandardLocation.PLATFORM_CLASS_PATH, options.getBootClasspath(), fileManager);
    List<String> processorPathEntries = options.getProcessorPathEntries();
    if (!processorPathEntries.isEmpty()) {
      addPaths(StandardLocation.ANNOTATION_PROCESSOR_PATH, processorPathEntries, fileManager);
    }
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
        Lists.newArrayList(options.fileUtil().getOutputDirectory()));
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT,
        Lists.newArrayList(FileUtil.createTempDir("annotations")));
    return fileManager;
  }

  private void addPaths(Location location, List<String> paths, StandardJavaFileManager fileManager)
      throws IOException {
    List<File> filePaths = new ArrayList<>();
    for (String path : paths) {
      filePaths.add(new File(path));
    }
    fileManager.setLocation(location, filePaths);
  }

  private List<String> getJavacOptions(boolean processAnnotations) {
    List<String> javacOptions = new ArrayList<>();
    String encoding = options.fileUtil().getFileEncoding();
    if (encoding != null) {
      javacOptions.add("-encoding");
      javacOptions.add(encoding);
    }
    SourceVersion javaLevel = options.getSourceVersion();
    if (javaLevel != null) {
      javacOptions.add("-source");
      javacOptions.add(javaLevel.flag());
      javacOptions.add("-target");
      javacOptions.add(javaLevel.flag());
    }
    String lintArgument = options.lintArgument();
    if (lintArgument != null) {
      javacOptions.add(lintArgument);
    }
    String explicitProcessors = options.getProcessors();
    if (explicitProcessors != null) {
      javacOptions.add("-processor");
      javacOptions.add(explicitProcessors);
    }
    if (processAnnotations) {
      javacOptions.add("-proc:only");
    } else {
      javacOptions.add("-proc:none");
    }
    javacOptions.add("-implicit:none");
    javacOptions.addAll(options.getPlatformModuleSystemOptions());
    return javacOptions;
  }

  @Override
  public void parseFiles(Collection<String> paths, Handler handler, SourceVersion sourceVersion) {
    List<File> files = new ArrayList<>();
    for (String path : paths) {
      files.add(new File(path));
    }
    try {
      JavacEnvironment env = createEnvironment(files, null, false);
      List<CompilationUnitTree> units = new ArrayList<>();
      for (CompilationUnitTree unit : env.task().parse()) {
        units.add(unit);
      }
      env.task().analyze();

      ArrayList<com.google.devtools.j2objc.ast.CompilationUnit> compileUnits = new ArrayList<>();
      if (ErrorUtil.errorCount() == 0) {
        for (CompilationUnitTree ast : units) {
          com.google.devtools.j2objc.ast.CompilationUnit unit = TreeConverter
              .convertCompilationUnit(options, env, ast);

          if (unit != null) {
            if (options.hasCustomImportRule()) {
              ImportManager.registerCompilationUnit(unit);
            }
            compileUnits.add(unit);
          }
        }
        
        if (options.hasCustomImportRule()) {
          for (com.google.devtools.j2objc.ast.CompilationUnit unit : compileUnits) {
            ImportManager.resolveImportability(unit);
          }
        }
        
        for (com.google.devtools.j2objc.ast.CompilationUnit unit : compileUnits) {
          TypeUtil.setUnreachableClasses(unit);
          TypeUtil.setIgnoreAllUnreachableTypeError(false);
          handler.handleParsedUnit(unit.getSourceFilePath(), unit);
          if (!unit.getUnreachableImportedClasses().isEmpty()) {
            ErrorUtil.addSkip(unit.getSourceFilePath());
          }
        }
      }
      processDiagnostics(paths, env.diagnostics());

    } catch (Exception e) {
      ErrorUtil.warning(e, "javac file manager error");
    }
  }

  /**
   * To allow Java 9 libraries like GSON to be transpiled using -source 1.8, stub out
   * the module-info source. This creates an empty .o file, like package-info.java
   * files without annotations. Skipping the file isn't feasible because of build tool
   * output file expectations.
   */
  private static JavaFileObject filterJavaFileObject(JavaFileObject fobj) {
    String path = fobj.getName();
    if (path.endsWith("module-info.java")) {
      String pkgName = null;
      try {
        pkgName = moduleName(fobj.getCharContent(true).toString());
        String source = "package " + pkgName + ";";
        return new MemoryFileObject(path, JavaFileObject.Kind.SOURCE, source);
      } catch (IOException e) {
        // Fall-through
      }
    }
    return fobj;
  }

  private static String moduleName(String moduleSource) {
    Pattern p = Pattern.compile("module\\s+([a-zA_Z_][\\.\\w]*)");
    Matcher matcher = p.matcher(moduleSource);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  // Creates a javac environment from a memory source.
  private JavacEnvironment createEnvironment(String path, String source) throws IOException {
    List<JavaFileObject> inputFiles = new ArrayList<>();
    inputFiles.add(filterJavaFileObject(MemoryFileObject.createJavaFile(path, source)));
    return createEnvironment(Collections.emptyList(), inputFiles, false);
  }

  // Creates a javac environment from a collection of files and/or file objects.
  private JavacEnvironment createEnvironment(List<File> files, List<JavaFileObject> fileObjects,
      boolean processAnnotations) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StandardJavaFileManager fileManager = getFileManager(compiler, diagnostics);
    List<String> javacOptions = getJavacOptions(processAnnotations);
    if (fileObjects == null) {
      fileObjects = new ArrayList<>();
    }
    for (JavaFileObject jfo : fileManager.getJavaFileObjectsFromFiles(files)) {
      fileObjects.add(filterJavaFileObject(jfo));
    }
    JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
        javacOptions, null, fileObjects);
    return new JavacEnvironment(task, fileManager, diagnostics);
  }

  private void processDiagnostics(Collection<String> compileUnits, DiagnosticCollector<JavaFileObject> diagnostics) {
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      ErrorUtil.parserDiagnostic(compileUnits, diagnostic);
    }
  }

  @Override
  public Parser.ParseResult parseWithoutBindings(InputFile file, String source) {
    String path = file.getUnitName();
    try {
      JavacEnvironment parserEnv = createEnvironment(path, source);
      JavacTask task = parserEnv.task();
      CompilationUnitTree unit = task.parse().iterator().next();
      processDiagnostics(null, parserEnv.diagnostics());
      return new JavacParseResult(
          file, source, unit, parserEnv.treeUtilities().getSourcePositions());
    } catch (IOException e) {
      ErrorUtil.fatalError(e, path);
    }
    return null;
  }


  @Override
  public ProcessingResult processAnnotations(List<ProcessingContext> inputs) {
    final List<ProcessingContext> generatedInputs = Lists.newArrayList();
    PathClassLoader loader = new PathClassLoader(options.fileUtil().getClassPathEntries());
    loader.addPaths(options.getProcessorPathEntries());
    Iterator<Processor> serviceIterator = ServiceLoader.load(Processor.class, loader).iterator();
    if (serviceIterator.hasNext() || options.getProcessors() != null) {
      List<File> inputFiles = new ArrayList<>();
      for (ProcessingContext input : inputs) {
        inputFiles.add(new File(input.getFile().getAbsolutePath()));
      }
      try {
        JavacEnvironment env = createEnvironment(inputFiles, null, true);
        env.task().parse();
        env.task().analyze();
        processDiagnostics(null, env.diagnostics());
        // The source output directory is created and set in createEnvironment().
        File sourceOutputDirectory =
            env.fileManager().getLocation(StandardLocation.SOURCE_OUTPUT).iterator().next();
        collectGeneratedInputs(sourceOutputDirectory, "", generatedInputs);
        return new JavacProcessingResult(generatedInputs, sourceOutputDirectory);
      } catch (IOException e) {
        ErrorUtil.fatalError(e, "javac file manager error");
      }
    }
    // No annotation processors on classpath, or processing errors reported.
    return new JavacProcessingResult(generatedInputs, null);
  }

  @Override
  public void close() throws IOException {
    if (fileManager != null) {
      try {
        fileManager.close();
      } finally {
        fileManager = null;
      }
    }
  }

  private void collectGeneratedInputs(
      File dir, String currentRelativePath, List<ProcessingContext> inputs) {
    assert dir.exists() && dir.isDirectory();
    for (File f : dir.listFiles()) {
      String relativeName = currentRelativePath + File.separatorChar + f.getName();
      if (f.isDirectory()) {
        collectGeneratedInputs(f, relativeName, inputs);
      } else {
        if (f.getName().endsWith(".java")) {
          inputs.add(ProcessingContext.fromFile(
              new RegularInputFile(f.getPath(), relativeName), options));
        }
      }
    }
  }

  /**
   * Extract the name of a Java source's package, or null if not found. This method is only used
   * before javac parsing to determine the main type name.
   */
  @VisibleForTesting
  static String packageName(String source) {
    try (StringReader r = new StringReader(source)) {
      StreamTokenizer tokenizer = new StreamTokenizer(r);
      tokenizer.slashSlashComments(true);
      tokenizer.slashStarComments(true);
      StringBuilder sb = new StringBuilder();
      boolean inName = false;
      while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
        if (inName) {
          switch (tokenizer.ttype) {
            case ';':
              return sb.length() > 0 ? sb.toString() : null;
            case '.':
              sb.append('.');
              break;
            case StreamTokenizer.TT_WORD:
              sb.append(tokenizer.sval);
              break;
            default:
              inName = false; // Invalid package statement pattern.
              break;
          }
        } else if (tokenizer.ttype == StreamTokenizer.TT_WORD && tokenizer.sval.equals("package")) {
          inName = true;
        }
      }
      return null; // Package statement not found.
    } catch (IOException e) {
      throw new AssertionError("Exception reading string: " + e);
    }
  }

  private static class JavacParseResult implements Parser.ParseResult {
    private final InputFile file;
    private String source;
    private final CompilationUnitTree unit;
    private final SourcePositions sourcePositions;

    private JavacParseResult(
        InputFile file,
        String source,
        CompilationUnitTree unit,
        SourcePositions sourcePositions) {
      this.file = file;
      this.source = source;
      this.unit = unit;
      this.sourcePositions = sourcePositions;
    }

    @Override
    public void stripIncompatibleSource() {
      source = JavacJ2ObjCIncompatibleStripper.strip(source, unit, sourcePositions);
    }

    @Override
    public String getSource() {
      return source;
    }

    @Override
    public String mainTypeName() {
      String qualifiedName = FileUtil.getMainTypeName(file);
      // The API for accessing a compilation unit's package changed between
      // Java 8 and Java 10, so instead this gets the package from the source.
      String packageName = JavacParser.packageName(source);
      if (packageName != null) {
        qualifiedName = packageName + "." + qualifiedName;
      }
      return qualifiedName;
    }

    @Override
    public String toString() {
      return unit.toString();
    }
  }

  private static class JavacProcessingResult implements Parser.ProcessingResult {
    private final List<ProcessingContext> generatedSources;
    private final File sourceOutputDirectory;

    public JavacProcessingResult(List<ProcessingContext> generatedSources,
        File sourceOutputDirectory) {
      this.generatedSources = generatedSources;
      this.sourceOutputDirectory = sourceOutputDirectory;
    }

    @Override
    public List<ProcessingContext> getGeneratedSources() {
      return generatedSources;
    }

    @Override
    public File getSourceOutputDirectory() {
      return sourceOutputDirectory;
    }
  }
}
