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

package wooga.gradle.releaseNotesGenerator

import org.ajoberstar.gradle.git.release.base.BaseReleasePlugin
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.plugins.PublishingPlugin
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.publish.tasks.GithubPublish
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.releaseNotesGenerator.tasks.GenerateReleaseNotes
import wooga.gradle.releaseNotesGenerator.tasks.UpdateReleaseNotes

/**
 * A Wooga internal plugin to generate release notes for Unity library packages.
 * This plugin is very opinionated. The release notes templates are static and the information parsed from github
 * should be in a specified format.
 */
class ReleaseNotesGeneratorPlugin implements Plugin<Project> {

    static Logger logger = Logging.getLogger(ReleaseNotesGeneratorPlugin)

    static final String APPEND_RELEASE_NOTES_TASK = "appendReleaseNotes"
    static final String GENERATE_RELEASE_NOTES_TASK = "generateReleaseNotes"
    static final String UPDATE_RELEASE_NOTES_TASK = "updateReleaseNotes"

    @Override
    void apply(Project project) {
        project.pluginManager.apply(BaseReleasePlugin)
        project.pluginManager.apply(GithubPlugin)

        def gitRoot = project.hasProperty('git.root') ? project.property('git.root') : project.rootProject.projectDir

        def git
        try {
            git = Grgit.open(dir: gitRoot)
        }
        catch(RepositoryNotFoundException e) {
            logger.warn("Git repository not found at $gitRoot -- Can't activate net.wooga.releaseNotesGenerator")
            return
        }

        createReleaseNoteTasks(project, git)
    }

    private static void createReleaseNoteTasks(Project project, Grgit git) {
        def tasks = project.tasks
        def githubPublishTask = tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME) as GithubPublish

        def appendLatestRelease = tasks.create(APPEND_RELEASE_NOTES_TASK, GenerateReleaseNotes)
        appendLatestRelease.appendLatestRelease(true)

        def generateReleaseNotes = tasks.create(GENERATE_RELEASE_NOTES_TASK, GenerateReleaseNotes)

        [appendLatestRelease, generateReleaseNotes].each {
            it.git(git)
            it.releaseNotes(project.file("RELEASE_NOTES.md"))
            it.group = "Release Notes"
        }

        UpdateReleaseNotes updateReleaseNotes = (UpdateReleaseNotes) tasks.create(UPDATE_RELEASE_NOTES_TASK, UpdateReleaseNotes)
        updateReleaseNotes.releaseNotes(project.file("RELEASE_NOTES.md"))
        updateReleaseNotes.dependsOn(appendLatestRelease)
        updateReleaseNotes.group = "Release Notes"

        Task publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        publishTask.dependsOn(updateReleaseNotes)
        updateReleaseNotes.mustRunAfter(githubPublishTask)
    }
}
