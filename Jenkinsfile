#!groovy
@Library('github.com/wooga/atlas-jenkins-pipeline@1.x') _

withCredentials([usernamePassword(credentialsId: 'github_integration', passwordVariable: 'githubPassword', usernameVariable: 'githubUser'),
                 usernamePassword(credentialsId: 'github_integration_2', passwordVariable: 'githubPassword2', usernameVariable: 'githubUser2'),
                 string(credentialsId: 'atlas_releaseNotesGenerator_coveralls_token', variable: 'coveralls_token')]) {

    def testEnvironment = [ 'osx':
                               [
                                   "ATLAS_GITHUB_INTEGRATION_USER=${githubUser}",
                                   "ATLAS_GITHUB_INTEGRATION_PASSWORD=${githubPassword}",
                                   "UNITY_PATH=${env.UNITY_2017_1_0_P_5_PATH}"
                               ],
                             'windows':
                               [
                                   "ATLAS_GITHUB_INTEGRATION_USER=${githubUser2}",
                                   "ATLAS_GITHUB_INTEGRATION_PASSWORD=${githubPassword2}",
                                   "UNITY_PATH=${env.UNITY_2017_1_0_P_5_PATH}"
                               ]
                        ]

    buildGradlePlugin plaforms: ['osx','windows'], coverallsToken: coveralls_token, testEnvironment: testEnvironment
}
