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

import nebula.test.IntegrationSpec
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import spock.lang.Shared

abstract class GithubIntegration extends IntegrationSpec {

    String uniquePostfix() {
        String key = "TRAVIS_JOB_NUMBER"
        def env = System.getenv()
        if (env.containsKey(key)) {
            return env.get(key)
        }
        return ""
    }

    @Shared
    def testUserName = System.getenv("ATLAS_GITHUB_INTEGRATION_USER")

    @Shared
    def testUserToken = System.getenv("ATLAS_GITHUB_INTEGRATION_PASSWORD")

    @Shared
    def testRepositoryName = "${testUserName}/atlas-release-integration" + uniquePostfix()

    @Shared
    GitHub client

    @Shared
    GHRepository testRepo

    def maybeDelete(String repoName) {
        try {
            def repository = client.getRepository(repoName)
            repository.delete()
        }
        catch (Exception e) {
        }
    }

    def createTestRepo() {
        maybeDelete(testRepositoryName)

        def builder = client.createRepository(testRepositoryName.split('/')[1])
        builder.description("Integration test repo for wooga/atlas-github")
        builder.autoInit(false)
        builder.licenseTemplate('MIT')
        builder.private_(false)
        builder.issues(false)
        builder.wiki(false)
        testRepo = builder.create()
    }

    def createRelease(String name, String tagName = null) {
        def releaseBuilder = testRepo.createRelease(tagName ? tagName : name)
        releaseBuilder.name(name)
        releaseBuilder.draft(false)
        releaseBuilder.prerelease(false)
        releaseBuilder.create()
    }

    def createClosedPullRequest(String name, String body, Boolean majorChange = true) {
        final String branchName = "testBranch"
        final String refName = "refs/heads/$branchName"

        def ref = testRepo.createRef(refName, testRepo.getBranch("master").getSHA1())
        testRepo.createContent("test file content", "test file", "Test.md", branchName)
        def pr = testRepo.createPullRequest(name, branchName, "master", body)

        if (majorChange) {
            try {
                testRepo.createLabel("Major Change", "000000")
            }
            catch (IOException exception) {
            }

            pr.setLabels("Major Change")
        }

        pr.close()
        ref.delete()
        pr
    }

    def setupSpec() {
        client = GitHub.connectUsingOAuth(testUserToken)
        createTestRepo()
    }

    def setup() {

        buildFile << """
            ${applyPlugin(ReleaseNotesGeneratorPlugin)}
        """.stripIndent()

    }

    def cleanup() {
        cleanupReleases()
    }

    void cleanupReleases() {
        try {
            PagedIterable<GHRelease> releases = testRepo.listReleases()
            releases.each {
                it.delete()
            }
        }
        catch (Error e) {

        }

    }

    def cleanupSpec() {
        maybeDelete(testRepositoryName)
    }
}
