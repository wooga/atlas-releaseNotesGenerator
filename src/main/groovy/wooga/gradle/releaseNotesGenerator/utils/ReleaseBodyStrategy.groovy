/*
 * Copyright 2018 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package wooga.gradle.releaseNotesGenerator.utils

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GHRepository
import wooga.gradle.github.publish.PublishBodyStrategy

class ReleaseBodyStrategy implements PublishBodyStrategy {

    private Grgit git
    private ReleaseVersion version

    ReleaseBodyStrategy(ReleaseVersion version, Grgit git) {
        this.git = git
        this.version = version
    }

    @Override
    String getBody(GHRepository repository) {
        return new ReleaseNotesGenerator(git, repository).generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)
    }
}
