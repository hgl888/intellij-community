/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public abstract class DirectoryIndexTestCase extends IdeaTestCase {
  protected DirectoryIndexImpl myIndex;
  protected ProjectFileIndex myFileIndex;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndex = (DirectoryIndexImpl)DirectoryIndex.getInstance(myProject);
    myFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
  }

  @Override
  protected void tearDown() throws Exception {
    myFileIndex = null;
    myIndex = null;
    super.tearDown();
  }

  protected void assertNotInProject(VirtualFile file) {
    DirectoryInfo info = myIndex.getInfoForFile(file);
    assertFalse(info.toString(), info.isInProject(file));
    assertFalse(info.toString(), info.isExcluded(file));
  }

  protected void assertExcluded(VirtualFile file, Module module) {
    DirectoryInfo info = myIndex.getInfoForFile(file);
    assertTrue(info.toString(), info.isExcluded(file));
    assertEquals(module, info.getModule());
  }

  protected DirectoryInfo assertInProject(VirtualFile file) {
    DirectoryInfo info = myIndex.getInfoForFile(file);
    assertTrue(file.toString(), info.isInProject(file));
    myIndex.assertConsistency(info);
    return info;
  }

  protected void assertNotExcluded(VirtualFile file) {
    assertFalse(myIndex.getInfoForFile(file).isExcluded(file));
  }

  protected void assertExcludedFromProject(VirtualFile file) {
    assertExcluded(file, null);
  }

  protected void assertIteratedContent(Module module, @Nullable List<VirtualFile> contains, @Nullable List<VirtualFile> doesntContain) {
    assertIteratedContent(ModuleRootManager.getInstance(module).getFileIndex(), contains, doesntContain);
    assertIteratedContent(myFileIndex, contains, doesntContain);
  }

  protected static void assertIteratedContent(FileIndex fileIndex,
                                              @Nullable List<VirtualFile> contains,
                                              @Nullable List<VirtualFile> doesntContain) {
    final Set<VirtualFile> collected = new THashSet<>();
    fileIndex.iterateContent(fileOrDir -> {
      if (!collected.add(fileOrDir)) {
        fail(fileOrDir + " visited twice");
      }
      return true;
    });
    if (contains != null) assertContainsElements(collected, contains);
    if (doesntContain != null) assertDoesntContain(collected, doesntContain);
  }
}
