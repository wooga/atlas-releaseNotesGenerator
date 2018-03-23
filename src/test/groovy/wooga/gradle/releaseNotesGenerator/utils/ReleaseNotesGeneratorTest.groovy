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

package wooga.gradle.releaseNotesGenerator.utils

import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.service.TagService
import org.apache.tools.ant.filters.StringInputStream
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHLabel
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.PagedIterable
import spock.lang.Specification

class ReleaseNotesGeneratorTest extends Specification {


    Grgit git
    TagService tag
    GHRepository hub
    ReleaseNotesGenerator releaseNoteGenerator
    String packageId = "Wooga.Test"

    List<GHRelease> releases

    def setup() {
        git = Grgit.init(dir: File.createTempDir())
        git.commit(message: 'initial commit')

        releases = new ArrayList<GHRelease>()
        PagedIterable<GHRelease> iterable = Mock()
        iterable.asList() >> releases

        hub = Mock(GHRepository)
        hub.fullName >> "wooga/TestRepo"
        hub.listReleases() >> iterable

        GHContent ghContent = Mock()
        ghContent.read() >> {new StringInputStream(TestContent.PAKET_TEMPLATE_V1)}

        GHRef ref = Mock()

        hub.getRef(_) >> ref

        hub.getFileContent(_, _) >> ghContent

        releaseNoteGenerator = new ReleaseNotesGenerator(git, hub, packageId)
    }

    def mockPullRequest(int number, Boolean changeSet = true) {
        def bodyOut = new StringBuilder()

        bodyOut << """
        ## Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        """.stripIndent()

        if (changeSet) {
            bodyOut << """
            ## Changes
            * ![ADD] some stuff
            * ![REMOVE] some stuff
            * ![FIX] some stuff
            
            Yada Yada Yada Yada Yada
            Yada Yada Yada Yada Yada
            Yada Yada Yada Yada Yada

            """.stripIndent() << TestContent.MIX_URL_ICON_IDS
        }

        def pr = Mock(GHPullRequest)
        pr.body >> bodyOut.toString()
        pr.number >> number
        pr.title >> "Pullrequest ${number}"
        pr.issueUrl >> new URL("https://github.com/${hub.fullName}/pull/${number}")
        pr.repository >> hub
        return pr
    }

    def mockRelease(String name, createdAt = new Date()) {
        GHRelease release = Mock(GHRelease)

        release.name >> name
        release.createdAt >> createdAt
        release.tagName >> "v${name}"
        release.assets >> new ArrayList<GHAsset>()
        releases.add(release)

        return release
    }

    def mockAsset(String name, GHRelease releaseMock) {
        GHAsset asset = Mock()
        asset.name >> name
        asset.browserDownloadUrl >> "http://github_asset/$name"
        releaseMock.assets.add(asset)
    }

    def "creates release notes from log and pull requests changes"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        """.stripIndent().stripMargin() + TestContent.ICON_IDS).trim()
    }

    def "creates release notes with full log when previousVersion tag can't be found"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "skips pull requests it can't find"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "allows multiple hash signs in commit message"() {
        given: "one commit with multiple hash signs"

        git.commit(message: '#44 fixes (#1), (#2) and (#3)')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * ![ADD] some stuff [#3]
        * ![REMOVE] some stuff [#3]
        * ![FIX] some stuff [#3]
        * ![ADD] some stuff [#2]
        * ![REMOVE] some stuff [#2]
        * ![FIX] some stuff [#2]
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "creates initial change message when previousVersion is not set"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", null, false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * ![NEW] Initial Release
        * ![ADD] some stuff [#1]
        * ![REMOVE] some stuff [#1]
        * ![FIX] some stuff [#1]
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "prints commit log when pull requests are empty"() {
        given: "a git log with pull requests commits and tags"
        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        def c1 = git.commit(message: 'commit')
        def c2 = git.commit(message: 'commit (#2)')
        def c3 = git.commit(message: 'commit (#3)')
        def c4 = git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(3) >> { throw new FileNotFoundException("missing") }

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * [`${c4.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c4.id}) ${c4.shortMessage}
        * [`${c3.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c3.id}) ${c3.shortMessage}
        * [`${c2.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c2.id}) ${c2.shortMessage}
        * [`${c1.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c1.id}) ${c1.shortMessage}
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "prints commit log when pull requests have no changeset list"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        def c1 = git.commit(message: 'commit')
        def c2 = git.commit(message: 'commit (#2)')
        def c3 = git.commit(message: 'commit (#3)')
        def c4 = git.commit(message: 'commit')

        and: "a version"
        def version = new ReleaseVersion("1.1.0", "1.0.0", false)

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1, false)
        hub.getPullRequest(2) >> { throw new FileNotFoundException("missing") }
        hub.getPullRequest(3) >> mockPullRequest(3, false)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.githubRelease)

        then:
        notes.normalize() == ("""
        * [`${c4.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c4.id}) ${c4.shortMessage}
        * [`${c3.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c3.id}) ${c3.shortMessage}
        * [`${c2.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c2.id}) ${c2.shortMessage}
        * [`${c1.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c1.id}) ${c1.shortMessage}
        """.stripIndent() + TestContent.ICON_IDS).trim()
    }

    def "creates full release notes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Major Changes ##
                
        ### Pullrequest 2 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Additional Changes ##
        
        * [`#3`](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3
        * [`#1`](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes for specific version with assets links"() {
        given: "a git log with pull requests commits"

        git.tag.add(name: "v1.0.0")
        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')
        git.tag.add(name: "v${currentVersion}")

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)

        and: "mocked releases"
        def release = mockRelease(currentVersion, releaseDate)
        mockAsset("sources.zip", release)
        mockAsset("binary.obj", release)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Major Changes ##
                
        ### Pullrequest 2 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Additional Changes ##
        
        * [`#3`](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3
        * [`#1`](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1

        ## Assets ##
        
        * [binary.obj](http://github_asset/binary.obj)
        * [sources.zip](http://github_asset/sources.zip)

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        releaseDate = new Date()
        date = releaseDate.format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes with multiple major changes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#4)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        def majorPR2 = mockPullRequest(4)
        majorPR2.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)
        hub.getPullRequest(4) >> majorPR2

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Major Changes ##
                
        ### Pullrequest 4 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ### Pullrequest 2 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Additional Changes ##
        
        * [`#3`](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3
        * [`#1`](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes with multiple major changes and no additional changes for specific version"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#4)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        def majorLabel = Mock(GHLabel)
        majorLabel.name >> "Major Change"

        def majorPR = mockPullRequest(2)
        majorPR.labels >> [majorLabel]

        def majorPR2 = mockPullRequest(4)
        majorPR2.labels >> [majorLabel]

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> majorPR
        hub.getPullRequest(3) >> mockPullRequest(3)
        hub.getPullRequest(4) >> majorPR2

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Major Changes ##
                
        ### Pullrequest 4 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ### Pullrequest 2 ###
                
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes for specific version without major versions when missing"() {
        given: "a git log with pull requests commits"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')

        and: "mocked pull requests"

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Changes ##
        
        * [`#3`](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3
        * [`#2`](https://github.com/wooga/TestRepo/pull/2) Pullrequest 2
        * [`#1`](https://github.com/wooga/TestRepo/pull/1) Pullrequest 1

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```
        
        ## How to install ##
        
        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        date = new Date().format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes for specific version with list of commits when no pull requests are available"() {
        given: "a git log with pull requests commits"

        git.tag.add(name: 'v1.0.0')
        def c1 = git.commit(message: 'initial commit')
        def c2 = git.commit(message: 'Change this')
        def c3 = git.commit(message: 'Add cool stuff')
        def c4 = git.commit(message: 'Fix ugly bug')
        def c5 = git.commit(message: 'Update that')
        def c6 = git.commit(message: 'ugly message')
        git.tag.add(name: "v${currentVersion}")

        and: "mocked pull requests"

        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        and: "mocked releases"
        mockRelease(currentVersion, releaseDate)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(version, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$currentVersion - $date](https://github.com/wooga/TestRepo/releases/tag/v$currentVersion) #

        ## Changes ##

        * [`${c6.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c6.id}) ${c6.shortMessage}
        * [`${c5.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c5.id}) ${c5.shortMessage}
        * [`${c4.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c4.id}) ${c4.shortMessage}
        * [`${c3.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c3.id}) ${c3.shortMessage}
        * [`${c2.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c2.id}) ${c2.shortMessage}
        * [`${c1.abbreviatedId}`](https://github.com/wooga/TestRepo/commit/${c1.id}) ${c1.shortMessage}

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##

        ```bash
        # latest stable
        nuget $packageId ~> 1
        # latest stable with only patch updates
        nuget $packageId ~> 1.1
        # latest build from master
        nuget $packageId ~> 1 master
        # latest build with release candidates
        nuget $packageId ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        currentVersion = "1.1.0"
        releaseDate = new Date()
        date = releaseDate.format("dd MMMM yyyy")
        version = new ReleaseVersion(currentVersion, "1.0.0", false)
    }

    def "creates full release notes with multiple versions"() {
        given: "a git log with pull requests commits and tags"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')
        git.commit(message: 'commit')
        git.commit(message: 'commit (#2)')
        git.commit(message: 'commit (#3)')
        git.commit(message: 'commit')
        git.tag.add(name: 'v1.1.0')

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)
        hub.getPullRequest(2) >> mockPullRequest(2)
        hub.getPullRequest(3) >> mockPullRequest(3)

        and: "mocked releases"
        mockRelease("1.0.0", releaseDateB)
        mockRelease("1.1.0", releaseDateA)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(versions, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$versionA.version - $dateA](https://github.com/wooga/TestRepo/releases/tag/v$versionA.version) #
        
        ## Changes ##
        
        * [`#3`](https://github.com/wooga/TestRepo/pull/3) Pullrequest 3
        * [`#2`](https://github.com/wooga/TestRepo/pull/2) Pullrequest 2
                
        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget Wooga.Test ~> 1
        # latest stable with only patch updates
        nuget Wooga.Test ~> 1.1
        # latest build from master
        nuget Wooga.Test ~> 1 master
        # latest build with release candidates
        nuget Wooga.Test ~> 1 rc
        ```
        
        # [$versionB.version - $dateB](https://github.com/wooga/TestRepo/releases/tag/v$versionB.version) #
        
        ## Major Changes ##

        ### Pullrequest 1 ###
        
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget Wooga.Test ~> 1
        # latest stable with only patch updates
        nuget Wooga.Test ~> 1.0
        # latest build from master
        nuget Wooga.Test ~> 1 master
        # latest build with release candidates
        nuget Wooga.Test ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        versionA = new ReleaseVersion("1.1.0", "1.0.0", false)
        versionB = new ReleaseVersion("1.0.0", null, false)
        releaseDateA = new Date(2012, 8, 12)
        releaseDateB = new Date(2012, 5, 1)

        dateA = releaseDateA.format("dd MMMM yyyy")
        dateB = releaseDateB.format("dd MMMM yyyy")
        versions = [versionA, versionB]
    }


    def "creates full first release notes with a single pull request"() {
        given: "a git log with a single pull request, commits and a tag"

        git.commit(message: 'commit')
        git.commit(message: 'commit (#1)')
        git.tag.add(name: 'v1.0.0')

        and: "mocked pull requests"
        hub.getPullRequest(1) >> mockPullRequest(1)

        and: "mocked releases"
        mockRelease("1.0.0", releaseDateA)

        when:
        def notes = releaseNoteGenerator.generateReleaseNotes(versions, ReleaseNotesGenerator.Template.releaseNotes)

        then:
        notes.normalize() == ("""
        # [$versionA.version - $dateA](https://github.com/wooga/TestRepo/releases/tag/v$versionA.version) #
        
        ## Major Changes ##

        ### Pullrequest 1 ###
        
        #### Description
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        
        #### Changes
        * ![ADD] some stuff
        * ![REMOVE] some stuff
        * ![FIX] some stuff
        
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada
        Yada Yada Yada Yada Yada

        ## Dependencies ##
        
        ```bash
        Wooga.TestDependency1 ~> 0.1.0
        Wooga.TestDependency2 = 0.7
        Wooga.TestDependency3 
        Wooga.TestDependency4 master
        Wooga.TestDependency4 > 1, <2
        ```

        ## How to install ##
        
        ```bash
        # latest stable
        nuget Wooga.Test ~> 1
        # latest stable with only patch updates
        nuget Wooga.Test ~> 1.0
        # latest build from master
        nuget Wooga.Test ~> 1 master
        # latest build with release candidates
        nuget Wooga.Test ~> 1 rc
        ```
        """.stripIndent() + TestContent.ICON_IDS).trim()

        where:
        versionA = new ReleaseVersion("1.0.0", null, false)
        releaseDateA = new Date(2012, 8, 12)

        dateA = releaseDateA.format("dd MMMM yyyy")
        versions = [versionA]
    }
}
