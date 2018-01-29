package wooga.gradle.releaseNotesGenerator

import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskContainer
import wooga.gradle.github.publish.GithubPublish
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.releaseNotesGenerator.tasks.GenerateReleaseNotes
import wooga.gradle.releaseNotesGenerator.tasks.UpdateReleaseNotes

class ReleaseNotesGeneratorPlugin implements Plugin<Project> {

    static Logger logger = Logging.getLogger(ReleaseNotesGeneratorPlugin)

    static final String APPEND_RELEASE_NOTES_TASK = "appendReleaseNotes"
    static final String GENERATE_RELEASE_NOTES_TASK = "generateReleaseNotes"
    static final String UPDATE_RELEASE_NOTES_TASK = "updateReleaseNotes"

    TaskContainer tasks
    Project project

    @Override
    void apply(Project project) {

        this.project = project
        this.tasks = project.tasks

        ReleasePluginExtension releaseExtension = project.extensions.findByType(ReleasePluginExtension)
        createReleaseNoteTasks(releaseExtension.grgit)
    }

    void createReleaseNoteTasks(Grgit git) {
        GithubPublish githubPublishTask = (GithubPublish) tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)

        GenerateReleaseNotes appendLatestRelease = (GenerateReleaseNotes) tasks.create(APPEND_RELEASE_NOTES_TASK, GenerateReleaseNotes)
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
