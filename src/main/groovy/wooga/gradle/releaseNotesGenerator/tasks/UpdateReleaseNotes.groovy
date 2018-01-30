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
import wooga.gradle.github.base.AbstractGithubTask

import java.util.concurrent.Callable

/**
 * A gradle task class which allows to update a <code>textfile<code> on github
 */
class UpdateReleaseNotes extends AbstractGithubTask {

    private Object commitMessage
    private Object releaseNotes

    UpdateReleaseNotes() {
        super(UpdateReleaseNotes)
        outputs.upToDateWhen { false }
    }

    /**
     * @return the file to update on github
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
     * @return The commit message to use for the update process
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

    UpdateReleaseNotes setCommitMessage(Object commitMessage) {
        this.commitMessage = commitMessage
        this
    }

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
