/*
 *  Copyright 2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package wooga.gradle.releaseNotesGenerator.tasks

import org.gradle.api.tasks.*
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHFileNotFoundException
import wooga.gradle.github.base.tasks.internal.AbstractGithubTask

import java.util.concurrent.Callable

/**
 * A gradle task class which allows to update a {@code releaseNotes} text file on github.
 * The task is based on {@link AbstractGithubTask} so all properties apply here as well.
 * <p>
 * Example:
 * <pre>
 * {@code
 *      task(updateReleaseNotes, type:wooga.gradle.releaseNotesGenerator.tasks.UpdateReleaseNotes) {
 *          releaseNotes = file("path/to/release_notes.md")
 *          commitMessage = "update"
 *      }
 * }
 * </pre>
 */
class UpdateReleaseNotes extends AbstractGithubTask {

    private Object commitMessage
    private Object releaseNotes

    UpdateReleaseNotes() {
        super(UpdateReleaseNotes)
        outputs.upToDateWhen { false }
    }

    /**
     * Returns the release notes file.
     * <p>
     * The content of this file will be written to a counterpart file on github.
     * If the file doesn't exist on github it will be created.
     * This task takes only the {@code master} branch into account.
     *
     * @return a file
     */
    @SkipWhenEmpty
    @InputFile
    File getReleaseNotes() {
        project.file(releaseNotes)
    }

    UpdateReleaseNotes setReleaseNotes(Object releaseNotes) {
        this.releaseNotes = releaseNotes
        this
    }

    UpdateReleaseNotes releaseNotes(Object releaseNotes) {
        this.setReleaseNotes(releaseNotes)
    }

    /**
     * Returns the commit message to use for the update process.
     *
     * @return the commit message
     * @default "Update release notes"
     */
    @Optional
    @Input
    String getCommitMessage() {
        if (commitMessage == null) {
            "Update release notes"
        } else if (commitMessage instanceof Callable) {
            ((Callable) commitMessage).call().toString()
        } else {
            commitMessage.toString()
        }
    }

    /**
     * Sets the commit message to use for the update process.
     * <p>
     * The value can be any value {@code Object} or a {@code Closure}.
     * If the value is a {@code Closure} object, it will be called in the getter and {@code toString} executed on the
     * return value.
     *
     * @param commitMessage
     * @return this
     */
    UpdateReleaseNotes setCommitMessage(Object commitMessage) {
        this.commitMessage = commitMessage
        this
    }

    /**
     * Sets the commit message to use for the update process.
     * <p>
     * The value can be any value {@code Object} or a {@code Closure}.
     * If the value is a {@code Closure} object, it will be called in the getter and {@code toString} executed on the
     * return value.
     *
     * @param commitMessage
     * @return this
     */
    UpdateReleaseNotes commitMessage(Object commitMessage) {
        this.setCommitMessage(commitMessage)
    }

    @TaskAction
    protected update() {
        logger.debug("update release notes")
        def body = getReleaseNotes().text.normalize()
        GHCommit lastCommit = ++repository.listCommits().iterator()
        String releaseNotesFileName = project.relativePath(getReleaseNotes())
        GHContent content
        try {
            content = repository.getFileContent(releaseNotesFileName, lastCommit.getSHA1())
            if (content.read().text == body) {
                logger.info("no content change in release notes")
                throw new StopActionException()
            }
            logger.info("update release notes")
            content.update(body, getCommitMessage())
        }
        catch (GHFileNotFoundException error) {
            logger.info("release notes file not found")
            logger.debug(error.message)
            logger.info("create release notes file")
            repository.createContent(body, getCommitMessage(), releaseNotesFileName)
        }
    }
}
