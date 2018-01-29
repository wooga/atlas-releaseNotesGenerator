package wooga.gradle.releaseNotesGenerator

abstract class GithubIntegrationWithDefaultAuth extends GithubIntegration {
    def setup() {
        buildFile << """
            github {
                userName = "$testUserName"
                repositoryName = "$testRepositoryName"
                token = "$testUserToken"
            }
        """.stripIndent()
    }
}
