/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

public class ChangeListsIndexes {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListsIndexes");
  private final TreeMap<FilePath, Data> myMap;

  ChangeListsIndexes() {
    myMap = new TreeMap<>(HierarchicalFilePathComparator.SYSTEM_CASE_SENSITIVE);
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myMap = new TreeMap<>(idx.myMap);
  }

  void add(final FilePath file, final FileStatus status, final VcsKey key, VcsRevisionNumber number) {
    myMap.put(file, new Data(status, key, number));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Set status " + status + " for " + file);
    }
  }

  void remove(final FilePath file) {
    myMap.remove(file);
  }

  public FileStatus getStatus(final VirtualFile file) {
    return getStatus(VcsUtil.getFilePath(file));
  }

  public FileStatus getStatus(@NotNull FilePath file) {
    Data data = myMap.get(file);
    return data != null ? data.status : null;
  }

  public void changeAdded(final Change change, final VcsKey key) {
    addChangeToIdx(change, key);
  }

  public void changeRemoved(final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();

    if (afterRevision != null) {
      remove(afterRevision.getFile());
    }
    if (beforeRevision != null) {
      remove(beforeRevision.getFile());
    }
  }

  @Nullable
  public VcsKey getVcsFor(@NotNull Change change) {
    VcsKey key = getVcsForRevision(change.getAfterRevision());
    if (key != null) return key;
    return getVcsForRevision(change.getBeforeRevision());
  }

  @Nullable
  private VcsKey getVcsForRevision(@Nullable ContentRevision revision) {
    if (revision != null) {
      Data data = myMap.get(revision.getFile());
      return data != null ? data.vcsKey : null;
    }
    return null;
  }

  private void addChangeToIdx(final Change change, final VcsKey key) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus(), key, beforeRevision == null ? VcsRevisionNumber.NULL : beforeRevision.getRevisionNumber());
    }
    if (beforeRevision != null) {
      if (afterRevision != null) {
        if (!Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
          add(beforeRevision.getFile(), FileStatus.DELETED, key, beforeRevision.getRevisionNumber());
        }
      } else {
        add(beforeRevision.getFile(), change.getFileStatus(), key, beforeRevision.getRevisionNumber());
      }
    }
  }

  /**
   * this method is called after each local changes refresh and collects all:
   * - paths that are new in local changes
   * - paths that are no more changed locally
   * - paths that were and are changed, but base revision has changed (ex. external update)
   * (for RemoteRevisionsCache and annotation listener)
   */
  public void getDelta(final ChangeListsIndexes newIndexes,
                       Set<BaseRevision> toRemove,
                       Set<BaseRevision> toAdd,
                       Set<BeforeAfter<BaseRevision>> toModify) {
    TreeMap<FilePath, Data> oldMap = myMap;
    TreeMap<FilePath, Data> newMap = newIndexes.myMap;
    Set<FilePath> oldFiles = oldMap.keySet();
    Set<FilePath> newFiles = newMap.keySet();

    final Set<FilePath> toRemoveSet = newHashSet(oldFiles);
    toRemoveSet.removeAll(newFiles);

    final Set<FilePath> toAddSet = newHashSet(newFiles);
    toAddSet.removeAll(oldFiles);

    final Set<FilePath> toModifySet = newHashSet(oldFiles);
    toModifySet.removeAll(toRemoveSet);

    for (FilePath s : toRemoveSet) {
      final Data data = oldMap.get(s);
      toRemove.add(createBaseRevision(s, data));
    }
    for (FilePath s : toAddSet) {
      final Data data = newMap.get(s);
      toAdd.add(createBaseRevision(s, data));
    }
    for (FilePath s : toModifySet) {
      final Data oldData = oldMap.get(s);
      final Data newData = newMap.get(s);
      assert oldData != null && newData != null;
      if (!oldData.sameRevisions(newData)) {
        toModify.add(new BeforeAfter<>(createBaseRevision(s, oldData), createBaseRevision(s, newData)));
      }
    }
  }

  private static BaseRevision createBaseRevision(FilePath path, Data data) {
    return new BaseRevision(data.vcsKey, data.revision, path);
  }

  public List<BaseRevision> getAffectedFilesUnderVcs() {
    final List<BaseRevision> result = new ArrayList<>();
    for (Map.Entry<FilePath, Data> entry : myMap.entrySet()) {
      final Data value = entry.getValue();
      result.add(createBaseRevision(entry.getKey(), value));
    }
    return result;
  }

  @NotNull
  public NavigableSet<FilePath> getAffectedPaths() {
    return Sets.unmodifiableNavigableSet(myMap.navigableKeySet());
  }

  private static class Data {
    public final FileStatus status;
    public final VcsKey vcsKey;
    public final VcsRevisionNumber revision;

    public Data(FileStatus status, VcsKey vcsKey, VcsRevisionNumber revision) {
      this.status = status;
      this.vcsKey = vcsKey;
      this.revision = revision;
    }

    public boolean sameRevisions(@NotNull Data data) {
      return Comparing.equal(vcsKey, data.vcsKey) && Comparing.equal(revision, data.revision);
    }
  }
}
