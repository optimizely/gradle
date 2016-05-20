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

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Ignore

/**
 * Tooling models for composite are produced by a single daemon instance.
 * We only do this for target gradle versions that support composite build.
 */
@Ignore("Daemon coordinator is disabled until we branch for 2.13 release")
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_COMPOSITE_BUILD)
class CoordinatorCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def setup() {
        toolingApi.requireIsolatedDaemons()
    }

    def "check that retrieving a model causes a daemon to be started for the composite"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << "apply plugin: 'java'"
        }
        when:
        def daemonsBefore = toolingApi.getDaemons()
        then:
        daemonsBefore.daemons.size() == 0
        when:
        def models = withCompositeConnection(singleBuild) { connection ->
            connection.getModels(EclipseProject)
        }
        def daemonsAfter = toolingApi.getDaemons()
        then:
        models.size() == 1
        daemonsAfter.daemons.size() == 1
    }
}
