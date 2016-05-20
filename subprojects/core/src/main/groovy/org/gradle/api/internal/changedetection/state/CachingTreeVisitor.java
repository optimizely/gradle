/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

// Visits a FileTreeInternal for snapshotting, caches some directory scans
public class CachingTreeVisitor {
    private ConcurrentMap<String, Collection<FileTreeElement>> cachedTrees = new MapMaker().weakValues().makeMap();

    public Collection<FileTreeElement> visitTreeForSnapshotting(FileTreeInternal fileTree, boolean allowReuse) {
        if (isDirectoryFileTree(fileTree)) {
            DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(((FileTreeAdapter) fileTree).getTree());
            if (isEligibleForCaching(directoryFileTree)) {
                final String absolutePath = directoryFileTree.getDir().getAbsolutePath();
                Collection<FileTreeElement> cachedTree = allowReuse ? cachedTrees.get(absolutePath) : null;
                if (cachedTree != null) {
                    return cachedTree;
                } else {
                    cachedTree = doVisitTree(fileTree);
                    cachedTrees.put(absolutePath, cachedTree);
                    return cachedTree;
                }
            }
        }
        return doVisitTree(fileTree);
    }

    private boolean isEligibleForCaching(DirectoryFileTree directoryFileTree) {
        return directoryFileTree.getPatterns().isEmpty();
    }

    private boolean isDirectoryFileTree(FileTreeInternal fileTree) {
        return fileTree instanceof FileTreeAdapter && ((FileTreeAdapter) fileTree).getTree() instanceof DirectoryFileTree;
    }

    private Collection<FileTreeElement> doVisitTree(FileTreeInternal fileTree) {
        final ImmutableList.Builder<FileTreeElement> fileTreeElements = ImmutableList.builder();
        fileTree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileTreeElements.add(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileTreeElements.add(fileDetails);
            }
        });
        return fileTreeElements.build();
    }

    public void clearCache() {
        cachedTrees.clear();
    }
}
