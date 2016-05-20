/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal

import org.gradle.api.Action
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter
import org.gradle.api.internal.changedetection.state.FileSnapshot
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.CacheValidator
import org.gradle.cache.PersistentCache
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.hash.HashValue
import org.gradle.internal.resource.TextResource
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class FileCacheBackedScriptClassCompilerTest extends Specification {
    final ScriptCompilationHandler scriptCompilationHandler = Mock()
    final CacheRepository cacheRepository = Mock()
    final CacheBuilder localCacheBuilder = Mock()
    final CacheBuilder globalCacheBuilder = Mock()
    final CacheValidator validator = Mock()
    final PersistentCache localCache = Mock()
    final PersistentCache globalCache = Mock()
    final ScriptSource source = Mock()
    final TextResource resource = Mock()
    final ClassLoader classLoader = new MockClassLoader()
    final Transformer transformer = Mock()
    final CompileOperation<?> operation = Mock()
    final CachingFileSnapshotter snapshotter = Mock()
    final ClassLoaderCache classLoaderCache = Mock()
    final ClassLoaderRegistry classLoaderRegistry = Mock()
    final File localDir = new File("local-dir")
    final File globalDir = new File("global-dir")
    final File classesDir = new File(globalDir, "classes")
    final File metadataDir = new File(globalDir, "metadata")
    final FileCacheBackedScriptClassCompiler compiler = new FileCacheBackedScriptClassCompiler(cacheRepository, validator, scriptCompilationHandler, Stub(ProgressLoggerFactory), snapshotter, classLoaderCache, classLoaderRegistry)
    final Action verifier = Stub()
    final CompiledScript compiledScript = Stub() {
        loadClass() >> Script
    }
    def classLoaderId = ClassLoaderIds.buildScript("foo", "bar")

    def setup() {
        _ * source.resource >> resource
        _ * resource.contentCached >> true
        _ * resource.text >> 'this is the script'
        _ * source.className >> 'ScriptClassName'
        _ * source.fileName >> 'ScriptFileName'
        _ * source.displayName >> 'Build Script'
        _ * operation.id >> 'TransformerId'
        _ * operation.transformer >> transformer
        _ * localCache.baseDir >> localDir
        _ * globalCache.baseDir >> globalDir
        _ * validator.isValid() >> true
    }

    def "loads classes from cache directory"() {
        def initializer

        when:
        def result = compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier).loadClass()

        then:
        result == Script
        1 * snapshotter.snapshot(resource) >> Stub(FileSnapshot) { getHash() >> new HashValue("123") }
        1 * cacheRepository.cache("scripts-remapped/ScriptClassName/83/TransformerId309980") >> localCacheBuilder
        1 * localCacheBuilder.withInitializer(!null) >> { args ->
            initializer = args[0]
            localCacheBuilder
        }
        1 * localCacheBuilder.withDisplayName(!null) >> localCacheBuilder
        1 * localCacheBuilder.withValidator(!null) >> localCacheBuilder
        1 * localCacheBuilder.open() >> {
            initializer.execute(localCache)
            localCache
        }

        1 * cacheRepository.cache('scripts/83/TransformerId/TransformerId309980') >> globalCacheBuilder
        1 * globalCacheBuilder.withDisplayName(!null) >> globalCacheBuilder
        1 * globalCacheBuilder.withInitializer(!null) >> globalCacheBuilder
        1 * globalCacheBuilder.withValidator(!null) >> globalCacheBuilder
        1 * globalCacheBuilder.open() >> globalCache

        1 * scriptCompilationHandler.loadFromDir(source, classLoader, new File(localDir, 'classes'), new File(localDir, 'metadata'), operation, Script, classLoaderId) >> compiledScript
        0 * scriptCompilationHandler._
    }

    def "passes CacheValidator to cache builders"() {
        setup:
        snapshotter.snapshot(resource) >> Stub(FileSnapshot) { getHash() >> new HashValue("123") }
        cacheRepository.cache("scripts-remapped/ScriptClassName/83/TransformerId309980") >> localCacheBuilder
        localCacheBuilder.withProperties(!null) >> localCacheBuilder
        localCacheBuilder.withInitializer(!null) >> localCacheBuilder
        localCacheBuilder.withDisplayName(!null) >> localCacheBuilder
        localCacheBuilder.open() >> localCache
        scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, metadataDir, operation, Script, classLoaderId) >> compiledScript

        when:
        compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier)

        then:
        1 * localCacheBuilder.withValidator(validator) >> localCacheBuilder
    }

    def "compiles classes to cache directory when cache is invalid"() {
        def initializer, globalInitializer
        def classesDir = classesDir
        def metadataDir = new File(globalDir, "metadata")
        def localMetadataDir = new File(localDir, "metadata")
        def localClassesDir = new File(localDir, "classes")

        when:
        def result = compiler.compile(source, classLoader, classLoaderId, operation, Script, verifier).loadClass()

        then:
        result == Script
        1 * snapshotter.snapshot(resource) >> Stub(FileSnapshot) { getHash() >> new HashValue("123") }
        1 * cacheRepository.cache('scripts-remapped/ScriptClassName/83/TransformerId309980') >> localCacheBuilder
        1 * localCacheBuilder.withInitializer(!null) >> { args ->
            initializer = args[0]
            localCacheBuilder
        }
        1 * localCacheBuilder.withDisplayName(!null) >> localCacheBuilder
        1 * localCacheBuilder.withValidator(!null) >> localCacheBuilder
        1 * localCacheBuilder.open() >> {
            initializer.execute(localCache)
            localCache
        }

        1 * cacheRepository.cache('scripts/83/TransformerId/TransformerId309980') >> globalCacheBuilder
        1 * globalCacheBuilder.withDisplayName(!null) >> globalCacheBuilder
        1 * globalCacheBuilder.withInitializer(!null) >> { args ->
            globalInitializer = args[0]
            globalCacheBuilder
        }
        1 * globalCacheBuilder.withValidator(!null) >> globalCacheBuilder
        1 * globalCacheBuilder.open() >> {
            globalInitializer.execute(globalCache)
            globalCache
        }

        1 * scriptCompilationHandler.compileToDir({ it instanceof RemappingScriptSource }, classLoader, classesDir, metadataDir, operation, Script, verifier)
        1 * scriptCompilationHandler.loadFromDir(source, classLoader, localClassesDir, localMetadataDir, operation, Script, classLoaderId) >> compiledScript
        0 * scriptCompilationHandler._
    }

    def "reports compilation progress even in case of a failure"() {
        def factory = Mock(ProgressLoggerFactory)
        def delegate = Mock(Action)
        def cache = Mock(PersistentCache)
        def logger = Mock(ProgressLogger)

        def initializer = new FileCacheBackedScriptClassCompiler.ProgressReportingInitializer(factory, delegate, 'Compile script into cache', 'Compiling script into cache')

        when:
        initializer.execute(cache)

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Boo!"

        1 * factory.newOperation(FileCacheBackedScriptClassCompiler) >> logger
        1 * logger.start("Compile script into cache", "Compiling script into cache") >> logger

        then:
        1 * delegate.execute(cache) >> { throw new RuntimeException("Boo!") } //stress it a bit with a failure

        then:
        1 * logger.completed()
    }

    private static class MockClassLoader extends ClassLoader implements DefaultClassLoaderCache.HashedClassLoader {
        @Override
        int getClassLoaderHash() {
            9999
        }
    }
}
