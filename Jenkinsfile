#!groovy
@Library('github.com/wooga/atlas-jenkins-pipeline@0.0.3') _

pipeline {
    agent none

    stages {
        stage('Preparation') {
            agent any

            steps {
                sendSlackNotification "STARTED", true
            }
        }

        stage('check') {
            parallel {
                stage('Windows') {
                    agent {
                        label 'windows&&atlas'
                    }

                    environment {
                        COVERALLS_REPO_TOKEN    			= credentials('atlas_releaseNotesGenerator_coveralls_token')
                        TRAVIS_JOB_NUMBER       			= "${BUILD_NUMBER}.WIN"
                        UNITY_PATH              			= "${UNITY_2017_1_0_P_5_PATH}"
						GITHUB                            	= credentials('github_integration')
                        ATLAS_GITHUB_INTEGRATION_USER     	= "${GITHUB_USR}"
                        ATLAS_GITHUB_INTEGRATION_PASSWORD 	= "${GITHUB_PSW}"
                    }

                    steps {
                        gradleWrapper "check"
                    }

                    post {
                        success {
                            gradleWrapper "jacocoTestReport coveralls"
                        }

                        always {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/jacoco/test/html',
                                reportFiles: 'index.html',
                                reportName: 'Coverage',
                                reportTitles: ''
                            ])

                            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                            gradleWrapper "clean"
                        }
                    }
                }

                stage('macOS') {
                    agent {
                        label 'osx&&atlas&&secondary'
                    }

                    environment {
                        COVERALLS_REPO_TOKEN    			= credentials('atlas_releaseNotesGenerator_coveralls_token')
                        TRAVIS_JOB_NUMBER       			= "${BUILD_NUMBER}.MACOS"
                        UNITY_PATH              			= "${UNITY_2017_1_0_P_5_PATH}"
						GITHUB                            	= credentials('github_integration')
                        ATLAS_GITHUB_INTEGRATION_USER     	= "${GITHUB_USR}"
						ATLAS_GITHUB_INTEGRATION_PASSWORD 	= "${GITHUB_PSW}"
                    }

                    steps {
                        gradleWrapper "check"
                    }

                    post {
                        success {
                            gradleWrapper "jacocoTestReport coveralls"
                        }

                        always {
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/jacoco/test/html',
                                reportFiles: 'index.html',
                                reportName: 'Coverage',
                                reportTitles: ''
                            ])

                            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                            gradleWrapper "clean"
                        }
                    }
                }
            }

            post {
                always {
                    sendSlackNotification currentBuild.result, true
                }
            }
        }
    }
}
