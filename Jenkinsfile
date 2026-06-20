@Library("ontrack-jenkins-cli-pipeline@v5") _

pipeline {

    parameters {
        booleanParam(
                name: 'RELEASE',
                defaultValue: false,
                description: 'Perform release operations (Docker Hub push, GitHub Release, Documentation)'
        )
        booleanParam(
                name: 'JUST_BUILD_AND_PUSH',
                defaultValue: false,
                description: 'Just create the Docker images and push them'
        )
        booleanParam(
                name: 'SKIP_KDSL_ACCEPTANCE',
                defaultValue: false,
                description: 'Skipping KDSL acceptance tests'
        )
        booleanParam(
                name: 'SKIP_NEXT_UI_TESTS',
                defaultValue: false,
                description: 'Skipping legacy acceptance tests'
        )
        booleanParam(
                name: 'SKIP_GITHUB_IT',
                defaultValue: true,
                description: 'Skipping integration tests for GitHub'
        )
        booleanParam(
                name: 'SKIP_BITBUCKET_CLOUD_IT',
                defaultValue: true,
                description: 'Skipping integration tests for Bitbucket Cloud'
        )
    }

    agent {
        docker {
            label "do && c-16"
            image "nemerosa/ontrack-build:5.2.0"
            args "--volume /var/run/docker.sock:/var/run/docker.sock --network host"
        }
    }

    options {
        // Max. 1 hour
        timeout(time: 1, unit: 'HOURS')
        // General Jenkins job properties
        buildDiscarder(logRotator(numToKeepStr: '40'))
        // Timestamps
        timestamps()
        // No durability
        durabilityHint('PERFORMANCE_OPTIMIZED')
        // ANSI colours
        ansiColor('xterm')
        // No concurrent builds
        disableConcurrentBuilds()
    }

    stages {

        stage('Build') {
            environment {
                // Documentation environment (see ontrack-docs/build.gradle.kts)
                YONTRACK_DOCS_EDIT = "edit/${env.BRANCH_NAME}/ontrack-docs/docs"
                // Bitbucket Cloud system tests
                ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_WORKSPACE = credentials('ontrack-test-extension-bitbucket-cloud-workspace')
                ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_USER = credentials('ontrack-test-extension-bitbucket-cloud-user')
                ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_TOKEN = credentials('ontrack-test-extension-bitbucket-cloud-token')
                ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_EXPECTED_PROJECT = 'ONTRACK'
                ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_EXPECTED_REPOSITORY = 'ontrack-pipeline-bitbucket-cloud'
                // GitHub system tests
                ONTRACK_TEST_EXTENSION_GITHUB_USER = credentials('ontrack-test-extension-github-user')
                ONTRACK_TEST_EXTENSION_GITHUB_TOKEN = credentials('ontrack-test-extension-github-token')
                ONTRACK_TEST_EXTENSION_GITHUB_ORGANIZATION = 'nemerosa'
                ONTRACK_TEST_EXTENSION_GITHUB_REPOSITORY = 'ontrack-github-integration-test'
                ONTRACK_TEST_EXTENSION_GITHUB_BRANCH = 'v1'
                ONTRACK_TEST_EXTENSION_GITHUB_PR = '3'
                ONTRACK_TEST_EXTENSION_GITHUB_TEAM = 'ontrack-integration-tests'
                ONTRACK_TEST_EXTENSION_GITHUB_APP_ID = '143291'
                ONTRACK_TEST_EXTENSION_GITHUB_APP_PEM = credentials('ontrack-test-extension-github-app-pem')
                ONTRACK_TEST_EXTENSION_GITHUB_APP_INSTALLATION = 'nemerosa'
                ONTRACK_TEST_EXTENSION_GITHUB_PATHS_IMAGES_PROMOTION = 'images/iron.png'
                ONTRACK_TEST_EXTENSION_GITHUB_PATHS_IMAGES_VALIDATION = 'images/site.png'
                ONTRACK_TEST_EXTENSION_GITHUB_CHANGELOG_FROM = '5b0db7c7132eaf29820b42aef3a018a47e1f411f'
                ONTRACK_TEST_EXTENSION_GITHUB_CHANGELOG_TO = '36e95eded56bcfead06a55238ccb315fbfe211a8'
                ONTRACK_TEST_EXTENSION_GITHUB_CHANGELOG_MESSAGES = 'nemerosa/ontrack#978 Promotion image|nemerosa/ontrack#978 Validation image|nemerosa/ontrack#928 Explicitly skipping the jobs|nemerosa/ontrack#928 Sample GitHub ingestion configuration file'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_FROM = '36e95eded56bcfead06a55238ccb315fbfe211a8'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_ISSUE = '1'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_ISSUE_COMMIT = '901130bc4655a8180c2034f0619768c7343095bb'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_ISSUESUMMARY = 'Test issue'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_TO = '901130bc4655a8180c2034f0619768c7343095bb'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_MESSAGES = '#1 Commit with issue'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_ISSUELABELS = 'type:defect'
                ONTRACK_TEST_EXTENSION_GITHUB_ISSUES_MILESTONE = 'v1'
                ONTRACK_TEST_EXTENSION_GITHUB_ACTIONS_WORKFLOWID = "main.yml"
                ONTRACK_TEST_EXTENSION_GITHUB_ACTIONS_BRANCH = "main"
            }
            steps {
                sh '''
                    ./gradlew writeVersion \\
                        --stacktrace \\
                        --parallel \\
                        --console plain
                '''
                script {
                    // Additional options
                    env.ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_IGNORE = params.SKIP_BITBUCKET_CLOUD_IT
                    env.ONTRACK_TEST_EXTENSION_GITHUB_IGNORE = params.SKIP_GITHUB_IT
                    // Reads version information
                    env.VERSION = readFile(file: 'build/version.txt')
                    env.GIT_COMMIT = sh(
                            returnStdout: true,
                            script: 'git rev-parse HEAD'
                    ).trim()
                    currentBuild.description = env.VERSION
                    // A bit of logging
                    echo "Version = ${env.VERSION}"
                    echo "Git commit = ${env.GIT_COMMIT}"
                    // Setup
                    ontrackCliCIConfig(logging: true)
                }
                script {
                    if (!params.JUST_BUILD_AND_PUSH) {
                        sh '''
                            ./gradlew \\
                                build \\
                                --stacktrace \\
                                --parallel \\
                                --console plain
                        '''
                    }
                    echo "Building the Docker images..."
                    sh '''
                            ./gradlew \\
                                dockerBuild \\
                                jibDockerBuild \\
                                --stacktrace \\
                                --parallel \\
                                --console plain
                        '''
                }
            }
            post {
                always {
                    recordIssues(tools: [kotlin(), javaDoc(), java()])
                    // Build validation stamps
                    ontrackCliValidateTests(
                        stamp: 'BUILD',
                        pattern: '**/build/test-results/**/*.xml',
                    )
                    ontrackCliValidateTests(
                            stamp: 'UI_UNIT',
                            pattern: 'ontrack-web-core/reports/*.xml',
                    )
                }
            }
        }

        stage('KDSL acceptance tests') {
            when {
                expression {
                    return !params.SKIP_KDSL_ACCEPTANCE && !params.JUST_BUILD_AND_PUSH
                }
            }
            environment {
                // Non empty is enough to activate the GitHub tests
                ONTRACK_TEST_EXTENSION_GITHUB_ORGANIZATION = credentials("ontrack-acceptance-github-organization")
                // Credentials & setup for the GitHub tests
                ONTRACK_ACCEPTANCE_GITHUB_ORGANIZATION = credentials("ontrack-acceptance-github-organization")
                ONTRACK_ACCEPTANCE_GITHUB_TOKEN = credentials("ontrack-acceptance-github-token")
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    sh '''
                        ./gradlew \\
                            --stacktrace \\
                            --console plain \\
                            --parallel \\
                            :ontrack-kdsl-acceptance:kdslAcceptanceTest
                        '''
                }
            }
            post {
                always {
                    ontrackCliValidateTests(
                            stamp: 'KDSL.ACCEPTANCE',
                            pattern: 'ontrack-kdsl-acceptance/build/test-results/**/*.xml',
                    )
                }
                failure {
                    script {
                        sh 'mv ontrack-kdsl-acceptance/build/logs ontrack-kdsl-acceptance/build/logs-kdsl'
                    }
                    archiveArtifacts(artifacts: "ontrack-kdsl-acceptance/build/logs-kdsl/**", allowEmptyArchive: true)
                }
            }
        }

        stage('Local UI tests') {
            when {
                expression {
                    return !params.SKIP_NEXT_UI_TESTS && !params.JUST_BUILD_AND_PUSH
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    sh '''
                        ./gradlew \\
                            --stacktrace \\
                            --console plain \\
                            --parallel \\
                            :ontrack-web-tests:uiTests
                        '''
                }
            }
            post {
                always {
                    ontrackCliValidateTests(
                            stamp: 'PLAYWRIGHT',
                            pattern: 'ontrack-web-tests/reports/*/junit/*.xml',
                    )
                }
                failure {
                    archiveArtifacts(artifacts: "ontrack-kdsl-acceptance/build/logs/**", allowEmptyArchive: true)
                    archiveArtifacts(artifacts: "ontrack-web-tests/reports/*/html/**", allowEmptyArchive: true)
                }
            }
        }

        // We stop here for pull requests and feature branches

        // Publication

        stage('Docker Hub') {
            when {
                anyOf {
                    branch 'main'
                    branch 'release/*'
                    branch 'feature/*publication'
                    expression {
                        params.JUST_BUILD_AND_PUSH
                    }
                }
                expression {
                    params.RELEASE
                }
            }
            environment {
                DOCKER_HUB = credentials("docker-hub")
            }
            steps {
                echo "Docker push"
                sh '''
                    echo ${DOCKER_HUB_PSW} | docker login --username ${DOCKER_HUB_USR} --password-stdin
                    
                    # Legacy organization
                    docker image push nemerosa/ontrack:${VERSION}
                    docker image push nemerosa/ontrack-ui:${VERSION}
                    
                    # Yontrack organization
                    docker image push yontrack/yontrack:${VERSION}
                    docker image push yontrack/yontrack-ui:${VERSION}
                '''
            }
            post {
                always {
                    ontrackCliValidate(
                            stamp: 'DOCKER.HUB'
                    )
                }
            }
        }

        // Release

        stage('Release') {
            when {
                beforeAgent true
                anyOf {
                    branch 'main'
                    branch 'release/*'
                }
                expression {
                    params.RELEASE
                }
            }
            steps {
                script {
                    // Getting the changelog since the last RELEASE promotion
                    String changelog = ontrackCliChangelogSincePromotion(
                            promotion: 'RELEASE',
                            renderer: 'markdown',
                            config: [
                                    title: true,
                                    commitsOption: "OPTIONAL",
                            ],
                    )
                    // Creating the release in GitHub
                    createGitHubRelease(
                            credentialId: 'github-token',
                            repository: 'yontrack/yontrack',
                            name: env.VERSION,
                            tag: env.VERSION,
                            commitish: env.GIT_COMMIT,
                            bodyText: changelog,
                    )
                }
            }
            post {
                always {
                    ontrackCliValidate(
                            stamp: 'GITHUB.RELEASE',
                    )
                }
            }
        }

        // Documentation

        stage('Documentation') {
            environment {
                AMS3_DELIVERY = credentials("do-yontrack-docs-space")
            }
            when {
                beforeAgent true
                anyOf {
                    branch 'main'
                    branch 'release/*'
                }
                expression {
                    params.RELEASE
                }
            }
            steps {
                sh '''
                    rm -rf build/docs
                    mkdir -p build/docs
                    
                    s3cmd \\
                        --access_key=${AMS3_DELIVERY_USR} \\
                        --secret_key=${AMS3_DELIVERY_PSW} \\
                        --host=fra1.digitaloceanspaces.com \\
                        --host-bucket='%(bucket)s.fra1.digitaloceanspaces.com' \\
                        put \\
                        ontrack-docs/site/* \\
                        s3://yontrack-docs/release/${VERSION}/docs/site/ \\
                        --acl-public \\
                        --add-header=Cache-Control:max-age=86400 \\
                        --recursive
                '''

            }
            post {
                always {
                    ontrackCliValidate(
                            stamp: 'DOCUMENTATION',
                    )
                }
            }
        }

    }

}
