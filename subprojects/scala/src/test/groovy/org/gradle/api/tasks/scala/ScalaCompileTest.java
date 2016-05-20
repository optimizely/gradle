/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.core.IsNull;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.util.HashSet;

public class ScalaCompileTest extends AbstractCompileTest {
    public static final boolean NO_USE_ANT = false;
    public static final boolean USE_ANT = true;
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ScalaCompile scalaCompile;

    private Compiler<ScalaJavaJointCompileSpec> scalaCompiler;
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private FileTreeInternal scalaClasspath;

    @Override
    public AbstractCompile getCompile() {
        return scalaCompile;
    }

    @Override
    public ConventionTask getTask() {
        return scalaCompile;
    }

    @Before
    public void setUp() {
        scalaCompile = createTask(ScalaCompile.class);
        scalaCompiler = context.mock(Compiler.class);
        scalaCompile.setCompiler(scalaCompiler);

        GFileUtils.touch(new File(srcDir, "incl/file.scala"));
        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(scalaCompile, NO_USE_ANT);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(false));
            one(scalaCompiler).execute((ScalaJavaJointCompileSpec) with(IsNull.notNullValue()));
        }});

        scalaCompile.execute();
    }

    @Test
    public void testMoansIfScalaClasspathIsEmpty() {
        setUpMocksAndAttributes(scalaCompile, NO_USE_ANT);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(TaskExecutionException.class);
        thrown.expectCause(new CauseMatcher(InvalidUserDataException.class, "'testTask.scalaClasspath' must not be empty"));

        scalaCompile.execute();
    }

    @Test
    public void testExecuteDoingWorkWithAnt() {
        setUpMocksAndAttributes(scalaCompile, USE_ANT);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(false));
            one(scalaCompiler).execute((ScalaJavaJointCompileSpec) with(IsNull.notNullValue()));
        }});

        scalaCompile.execute();
    }

    @Test
    public void testMoansIfScalaClasspathIsEmptyWithAnt() {
        setUpMocksAndAttributes(scalaCompile, USE_ANT);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(TaskExecutionException.class);
        thrown.expectCause(new CauseMatcher(InvalidUserDataException.class, "'testTask.scalaClasspath' must not be empty"));

        scalaCompile.execute();
    }

    @SuppressWarnings("deprecation")  //setUseAnt()
    protected void setUpMocksAndAttributes(final ScalaCompile compile, boolean useAnt) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        scalaClasspath = context.mock(FileTreeInternal.class);
        compile.setScalaClasspath(scalaClasspath);
        final FileTreeInternal classpath = context.mock(FileTreeInternal.class);
        final FileTreeInternal zincClasspath = context.mock(FileTreeInternal.class);

        context.checking(new Expectations(){{
            allowing(scalaClasspath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(scalaClasspath).visit((FileVisitor) with(anything()));
            allowing(scalaClasspath).visitTreeOrBackingFile((FileVisitor) with(anything()));
            allowing(scalaClasspath).iterator(); will(returnIterator());
            allowing(classpath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(classpath).visit((FileVisitor) with(anything()));
            allowing(classpath).visitTreeOrBackingFile((FileVisitor) with(anything()));
            allowing(classpath).iterator(); will(returnIterator());
            allowing(zincClasspath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(zincClasspath).visit((FileVisitor) with(anything()));
            allowing(zincClasspath).visitTreeOrBackingFile((FileVisitor) with(anything()));
            allowing(zincClasspath).iterator(); will(returnIterator());
        }});
        compile.setClasspath(classpath);
        compile.setZincClasspath(zincClasspath);
        ScalaCompileOptions options = compile.getScalaCompileOptions();
        if (useAnt) {
            options.setUseAnt(true);
            options.setFork(false);
        }
        options.getIncrementalOptions().setAnalysisFile(new File("analysisFile"));
    }


    private class CauseMatcher<T extends Exception> extends BaseMatcher<T> {
        private final Class<T> throwableClass;
        private final String expectedMessage;

        public CauseMatcher(Class<T> throwableClass, String expectedMessage) {
            this.throwableClass = throwableClass;
            this.expectedMessage = expectedMessage;
        }

        public boolean matches(Object item) {
            return item.getClass().isAssignableFrom(throwableClass)
                        && ((T)item).getMessage().contains(expectedMessage);
        }

        public void describeTo(Description description) {

        }
    }
}
