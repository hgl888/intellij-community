/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DirectoryInfo {
  /**
   * @return {@code true} if the whole directory is located under project content or library roots and not excluded or ignored
   * @deprecated use {@link #isInProject(VirtualFile)} instead, this method doesn't take {@link ContentEntry#getExcludePatterns()} into account
   */
  public abstract boolean isInProject();

  /**
   * @return {@code true} if {@code file} is located under project content or library roots and not excluded or ignored
   */
  public abstract boolean isInProject(@NotNull VirtualFile file);

  /**
   * @return {@code true} if located under ignored directory
   */
  public abstract boolean isIgnored();

  /**
   * @return {@code true} if the whole directory is located in project content, output or library root but excluded from the project
   * @deprecated use {@link #isExcluded(VirtualFile)} instead, this method doesn't take {@link ContentEntry#getExcludePatterns()} into account
   */
  public abstract boolean isExcluded();

  /**
   * @return {@code true} if {@code file} located under this directory is excluded from the project.
   */
  public abstract boolean isExcluded(@NotNull VirtualFile file);

  public abstract boolean isInModuleSource();

  public abstract boolean isInLibrarySource();

  @Nullable
  public abstract VirtualFile getSourceRoot();

  public abstract int getSourceRootTypeId();

  public boolean hasLibraryClassRoot() {
    return getLibraryClassRoot() != null;
  }

  public abstract VirtualFile getLibraryClassRoot();

  @Nullable
  public abstract VirtualFile getContentRoot();

  @Nullable
  public abstract Module getModule();
}
