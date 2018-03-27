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

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.kohsuke.github.GHRelease
import wooga.gradle.github.base.tasks.internal.AbstractGithubTask
import wooga.gradle.releaseNotesGenerator.utils.ReleaseNotesGenerator

import java.util.concurrent.Callable

/**
 * Generates opinionated release notes text from github release with pull requests and git commits content.
 * <p>
 * It uses the internal github client and fetch a sorted list of all Github releases
 * on the configured repository.
 * If the {@link #getAppendLatestRelease()} flag is set, it will use only the latest release.
 * The list of releases will be converted into {@link ReleaseVersion} objects and passed to a {@link ReleaseNotesGenerator}.
 * The result of the {@link #generateReleaseNotes()}
 * will saved to the configured export file. ({@link ReleaseNotesGenerator#getReleaseNotes()}).
 * <p>
 * Example:
 * <pre>
 * {@code
 *     task(updateReleaseNotes, type:wooga.gradle.releaseNotesGenerator.tasks.GenerateReleaseNotes) {
 *          releaseNotes = file("path/to/release_notes.md")
 *          appendLatestRelease = false
 *     }
 * }
 * </pre>
 *
 * @see ReleaseNotesGenerator
 * @see AbstractGithubTask
 */
class GenerateReleaseNotes extends AbstractGithubTask {
    private static final Logger logger = Logging.getLogger(GenerateReleaseNotes)

    private Grgit git

    private Object releaseNotes
    private Object appendLatestRelease

    GenerateReleaseNotes() {
        super(GenerateReleaseNotes.class)
        outputs.upToDateWhen { false }
    }

    /**
     * Returns a {@code Boolean} value indicating,
     * if only the notes for the latest release should be generated and appended.
     * <p>
     * If the value is {@code false}, the release notes will be generated from all available github releases.
     * If the value is {@code true}, only the release notes from the latest release will be generated and append to the
     * release notes file.
     *
     * @return  {@code true} if release notes for latest release only should be generated and appended.
     * @default false
     */
    @Optional
    @Input
    Boolean getAppendLatestRelease() {
        if (appendLatestRelease == null) {
            null
        } else if (appendLatestRelease instanceof Callable) {
            appendLatestRelease.call().toBoolean()
        } else {
            appendLatestRelease.toBoolean()
        }
    }

    /**
     * Sets the {@code Boolean} flag to indicate
     * if only the notes for the latest release should be generated and appended.
     *
     * The value can be any value {@code Object} or a {@code Closure}.
     * If the value is a {@code Closure} object, it will be called in the getter and {@code toBoolean} executed on the
     * return value.
     *
     * @param isAppending {@code true} if release notes for latest release only should be generated and appended.
     * @return this
     */
    GenerateReleaseNotes setAppendLatestRelease(Object isAppending) {
        this.appendLatestRelease = isAppending
        this
    }

    /**
     * Sets the {@code Boolean} flag to indicate
     * if only the notes for the latest release should be generated and appended.
     *
     * The value can be any value {@code Object} or a {@code Closure}.
     * If the value is a {@code Closure} object, it will be called in the getter and {@code toBoolean} executed on the
     * return value.
     *
     * @param isAppending {@code true} if release notes for latest release only should be generated and appended.
     * @return this
     */
    GenerateReleaseNotes appendLatestRelease(Object releaseNotes) {
        this.setAppendLatestRelease(releaseNotes)
    }

    /**
     * Returns the {@code File} where the generated release notes will be written to.
     *
     * @return the release notes file
     */
    @OutputFile
    File getReleaseNotes() {
        project.file(releaseNotes)
    }

    /**
     * Sets the {@code File} where the generated release notes will be written to.
     *
     * @param releaseNotes the release notes file
     * @return this
     */
    GenerateReleaseNotes setReleaseNotes(Object releaseNotes) {
        this.releaseNotes = releaseNotes
        this
    }

    /**
     * Sets the {@code File} where the generated release notes will be written to.
     *
     * @param releaseNotes the release notes file
     * @return this
     */
    GenerateReleaseNotes releaseNotes(Object releaseNotes) {
        this.setReleaseNotes(releaseNotes)
    }

    /**
     * Returns the {@link Grgit} instance used to fetch commit logs and git tags.
     *
     * @return a {@link Grgit} instance
     */
    @Internal
    Grgit getGit() {
        git
    }

    /**
     * Sets the {@link Grgit} instance used to fetch commit logs and git tags.
     *
     * @param git {@link Grgit} instance
     * @return this
     */
    GenerateReleaseNotes setGit(Grgit git) {
        this.git = git
        this
    }

    /**
     * Sets the {@link Grgit} instance used to fetch commit logs and git tags.
     *
     * @param git {@link Grgit} instance
     * @return this
     */
    GenerateReleaseNotes git(Grgit git) {
        this.setGit(git)
    }

    /**
     * Creates a {@link ReleaseVersion} object from a list of Github releases and an index.
     * <p>
     * The method fetches the release at index the previous release if available and constructs a {@link ReleaseVersion}
     * object with the release {@code name} values.
     * <p>
     * <b>Note</b>
     * By Wooga convention all releases are named after the version.
     * This makes it easy to convert releases back into versions values.
     *
     * @param  index the index of the release to create the {@code ReleaseVersion} object for.
     * @param  releases list of releases
     * @return a {@link ReleaseVersion} with the release version fetched with index from release list and the previous release
     * @see    ReleaseVersion
     */
    @Internal
    protected ReleaseVersion releaseVersionForIndex(int index, List<GHRelease> releases) {
        if (releases.size() > index) {
            GHRelease release = releases[index]
            GHRelease previousRelease = index < releases.size() ? releases[index + 1] : null

            String releaseName = (release) ? release.name : null
            String previousReleaseName = (previousRelease) ? previousRelease.name : null

            logger.info("Create ReleaseVersion with ${releaseName} -> ${previousReleaseName}")
            return new ReleaseVersion(releaseName, previousReleaseName, false)
        }
        return null
    }

    @TaskAction
    protected generate() {
        File notesFile = getReleaseNotes()
        List<GHRelease> r = getRepository().listReleases().asList().findAll { !it.isPrerelease() && !it.isDraft() }

        if (r.size() == 0) {
            logger.info("no releases available")
            throw new StopActionException()
        }

        r = r.toSorted(new Comparator<GHRelease>() {
            @Override
            int compare(GHRelease o1, GHRelease o2) {
                o2.createdAt <=> o1.createdAt
            }
        })

        ReleaseNotesGenerator generator = new ReleaseNotesGenerator(getGit(), getRepository(), project.group.toString())

        int items = getAppendLatestRelease() ? 1 : r.size()
        List<ReleaseVersion> versions = new ArrayList<ReleaseVersion>()

        for (int i = 0; i < items; i++) {
            versions.add(releaseVersionForIndex(i, r))
        }

        logger.info("generate release notes with ${versions.collect { it.version + '->' + it.previousVersion }}")

        if (getAppendLatestRelease() && notesFile.text.contains(versions[0].version)) {
            logger.info("release already contained in release notes")
            throw new StopActionException()
        }

        String notes = generator.generateReleaseNotes(versions, ReleaseNotesGenerator.Template.releaseNotes)

        writeReleaseNotes(notes)
    }

    private void writeReleaseNotes(String notes) {
        File notesFile = getReleaseNotes()
        File tempFile = File.createTempFile("releaseNotes_", "", notesFile.parentFile)
        tempFile.deleteOnExit()
        tempFile.append(notes)
        tempFile.append(System.lineSeparator())
        tempFile.append(System.lineSeparator())

        if (getAppendLatestRelease()) {
            tempFile.append(notesFile.newReader())
        }

        notesFile.delete()
        tempFile.renameTo(notesFile)
    }
}
