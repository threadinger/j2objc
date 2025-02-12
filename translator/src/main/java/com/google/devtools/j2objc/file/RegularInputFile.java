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
package com.google.devtools.j2objc.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import com.google.devtools.j2objc.J2ObjC;
import com.google.devtools.j2objc.javac.ImportManager;

/**
 * A single file in the filesystem.
 *
 * @author Mike Thvedt
 */
public class RegularInputFile extends InputFile {
  private final String absolutePath;

  public RegularInputFile(String unitPath) {
    this(unitPath, unitPath);
  }

  public RegularInputFile(String fsPath, String unitPath) {
    super(unitPath);
    this.absolutePath = fsPath;
    
    if (!ImportManager.canImportClass(unitPath)) {
      throw new RuntimeException("exclude " + unitPath);
    }
  }

  @Override
  public boolean exists() {
    return new File(absolutePath).exists();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new FileInputStream(new File(absolutePath));
  }

  @Override
  public Reader openReader(Charset charset) throws IOException {
    return new InputStreamReader(getInputStream(),  charset);
  }

  @Override
  public String getAbsolutePath() {
    return absolutePath;
  }

  @Override
  public String getOriginalLocation() {
    return absolutePath;
  }

  @Override
  public long lastModified() {
    return new File(absolutePath).lastModified();
  }

  @Override
  public String toString() {
    return getOriginalLocation();
  }
}
