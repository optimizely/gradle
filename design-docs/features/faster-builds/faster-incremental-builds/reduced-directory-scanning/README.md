
Currently there are some inefficiencies in the implementation of incremental build.

### Unnecessary directory scanning 

When a task is about to be executed, Gradle performs an up-to-date check. To do this Gradle scans the declared inputs to create a _snapshot_, which 
is essentially a set of the file paths and their associated hashes. It also scans the declared outputs to create a snapshot of the outputs.
The snapshots are compared against the snapshots from previous task execution. If the snapshots are the same, the task is considered up-to-date and skipped.

If not up-to-date, the task actions are executed. Typically these actions will iterate over the inputs and produce the outputs.

After the actions have run, the task outputs are scanned again and another output snapshot produced. This is compared against the output snapshot taken prior to the 
task actions, to determine which files the task produced. 
Finally, the snapshot of the inputs prior to the task action and outputs after the task action is persisted for next execution.

There are several problems with the implementation of this algorithm:

1. When the same directory is declared multiple times as an input or output, it is scanned multiple times. For example, the test classes directory is used twice as an input 
for the test task, once in the classpath definition and once as the set of candidate to use to detect test classes. Instead, a directory should be scanned exactly once during 
the up-to-date check, regardless of how it is referenced.
2. When a task is out-of-date, the up-to-date check and the task action both scan the same inputs, and in some cases, the same outputs. Instead, the task action should
reuse the result of scanning the inputs. For reliability, the output scan should not be reused, as Gradle has no insight into whether the task action has done anything with those
outputs at the time the scans is requested.
3. When a task uses the output of another task as its input, the generated files are scanned once at the end of the producer task, and again in the up-to-date check of the consumer task.
Instead, the result of scanning the outputs of the producer task should be reused during the up-to-date check of the consumer task.

For #1 and #2, we can assume that the inputs of the task will not change as the task is executing, as this is the contract of task dependency ordering. However, for
item #3, we can reuse the result only when nothing has run that is likely to modify the files. There are several cases where the result should be invalidated: 

- A task that has overlapping outputs has run.
- A task that does not declare its inputs and outputs has run.
- The result was calculated in an earlier build and these files are not being watched for changes.

### Inefficient caching

Currently, Gradle maintains 3 global caches:

- A mapping from file path to (file size, file last modified time, file contents hash). This is used to avoid hashing a file when its hash is already known.
- A mapping from task path to task history. The task history contains the properties of task execution and references the input and output snapshots by an id.
- A mapping from snapshot id to snapshot. A snapshot is essentially a set of (file path, file type, file content hash) tuples. This is separated from the task history
  to avoid loading large snapshots that aren't required, for example when the task is out-of-date. More on this below.

Each of these caches is held in memory and is backed by a persistent store.

Caches are maintained only for the most recent 2 builds, where `buildSrc` counts as a build. This means that switching between builds will cause all of this cached state to
be discarded.

To produce a snapshot, Gradle scans the directories to produce a stream of (file path, file type, file last modified time, file size) tuples. For each tuple, the
global cache is used to calculate the file contents hash. The result is a set of (file path, file type, file content hash) tuples, or the snapshot.

To do an up-to-date check, Gradle produces snapshots of the declared inputs and outputs, then uses the global cache to fetch the task history. If the task properties match, 
Gradle loads the input snapshot from the global cache and compares. If the input snapshots match, Gradle loads the output snapshot from the global cache and compares. 

Using the global cache to fetch the hash for each file is expensive, due to the constraints that allow the caches to be safely shared by multiple processes. 
There are improvements we could make here, but a global cache lookup is going to always be somewhat costly. 
There is another source for this information, which is the most recently calculated snapshot for the same directory, which is immutable and so much cheaper to share. 
The global cache would be used in the case of a miss.

The in memory cache of snapshots is useless. The snapshot object is attached to the task history object, which is also cached in memory, and the snapshots are only reached 
via the task history object.

Loading snapshot objects from the persistent store is expensive, and wasted work when the snapshot ends up the same as the one that is being compared with. 
This could be avoided by first comparing a hash of the snapshot stored in the task history object, against the hash of the calculated snapshot. 
Only when the hashes are different would the snapshot need to be loaded, to generate diagnostics (if required).

## Potential improvements

A potential approach would be to maintain an additional in-memory cache:

- (dir, patterns) to a set of (relative-path, file type, file last modified time, file size, file content hash) tuples, or _tree snapshot_

This cache would contain the most recently calculated tree snapshot for the given directory. When a directory is to be scanned or a snapshot calculated, the matching
entry from this cache can be used instead of scanning the directory.

The entries in this cache would be invalidated as tasks that produce outputs run. It would be discarded at the end of the build.
It could potentially be reused across a build session, and invalidated on file change events. A simple initial implementation could invalidate the cache when _any_ task 
actions are run. This would work well for mostly up-to-date builds.

A second in-memory cache could be added:

- (tree snapshot hash) -> tree snapshot

This would be used to share snapshots across task history entries and avoid loading snapshots when a copy is already loaded into memory. The entries of this cache would
not be invalidated.

### Candidate changes

- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Use a hash to short circuit loading task input or output snapshots into heap, and to share snapshots between tasks
- Parallel scanning of directory trees (in the worker pool)

### Stories

### Incremental build reuses directory scanning results in simple cases

Improve performance when a task takes files as input, that are produced by another task or tasks, by reusing the directory scanning results produced when scanning the outputs of the tasks.

Initially start with reuse in simple, but common, cases, such as when everything in a directory tree is to be scanned.

Add integration test coverage.

##### Goal of changes

There are two main possibilities to reduce directory scanning:
- when task output gets snapshotted, directory scanning results should get reused in the following tasks that use the output
  - the task output snapshotting always scans the whole output directory without any pattern
  - the task input might use a pattern to filter the results. The directory scanning results of the output snapshotting should be reused also in this case. 
- when task input gets snapshotted, directory scanning results should get reused when the task input gets read

This story doesn't implement all of this. This is just to clarify the direction which this story takes the implementation.


##### Implementation notes

- Implementation should change `DefaultFileCollectionSnapshotter` to unpack the file collection to a backing set of file trees, then use an in-memory cache to cache visiting each individual directory tree.
- Simple invalidation strategy, such as invalidate everything when any task action runs. This can be improved later.
- Invalidate cache at the end of the build. 



#### Open issues
- reusing directory scanning results of an output snapshot without a pattern when the input is using a pattern.
- currently the simple cache invalidation strategy flushes the cache before each task execution. A directory scanning result will only get reused when the task that produces the input to a certain task preceeds the task that uses the output.

### Incremental build reuses directory scanning results in most cases

The story implements cache invalidation strategy that makes it possible to reuse directory scanning results across multiple task executions. Besides the cache invalidation strategy change, there should be a solution for reusing a directory scanning result when the input is using a pattern to filter the results. Currently it's a common case that the output filesnapshot will scan the output directory with the _all_ pattern, but the input will be using a pattern to filter the results.

### Incremental build reuses directory scanning results for task inputs

The story adds reusing of directory scanning results for calls to `TaskInputs.getFiles()` or `TaskInputs.getSourceFiles()`.

### Improvement: minimize File.isDirectory, File.lastModified and File.length calls for resolved artifacts

Currently there are a lot of file system operations involved when the file metadata for  classpath artifacts is looked up in snapshotting. It should be safe to cache all lookups for artifact files that are stored under the `fileStoreDirectory` for the duration of the build.

### Incremental build avoids snapshotting duplicate task input or output directories in simple cases

Sometimes a task may accept a given directory as input or output multiple times. The `Test` task is an example of this.

Currently, such directories will be scanned multiple times. Instead, each directory should be scanned once when calculating the input or output snapshots for a task.
   
The implementation of this is made more complex when different patterns or specs are used. For this story, simply merge those file trees with the same base directory
and where one of the file trees has an 'accept everything' spec.

Add integration test coverage.
