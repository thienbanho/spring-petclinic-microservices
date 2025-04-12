pipeline {
    agent any

    environment {
        MAVEN_HOME = tool 'Maven'
        GITHUB_TOKEN = credentials('github-token')
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        SERVICES = "admin-server api-gateway config-server customers-service discovery-server vets-service visits-service genai-service"
        GITHUB_REPO_URL = "https://github.com/thienbanho/spring-petclinic-microservices.git"
        GITHUB_CREDENTIALS_ID = 'jenkins-petclinic-dthien'
    }

    stages {
        stage('Checkout') {
            steps {
                githubNotify context: 'jenkins-ci', 
                             description: 'Jenkins Pipeline Started',
                             status: 'PENDING'

                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: '*/main']], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[
                        credentialsId: 'jenkins-petclinic-dthien', 
                        url: "https://github.com/thienbanho/spring-petclinic-microservices.git"
                    ]]
                ])

                sh "git rev-parse HEAD"
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def changes = []
                    if (env.CHANGE_TARGET) {
                        sh """
                            git fetch --no-tags origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET}
                            git fetch --no-tags origin ${env.GIT_COMMIT}:refs/remotes/origin/PR-${env.CHANGE_ID}
                        """
                        changes = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET} HEAD", returnStdout: true).trim().split('\n')
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        changes = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}", returnStdout: true).trim().split('\n')
                    } else {
                        changes = sh(script: "git diff --name-only HEAD^", returnStdout: true).trim().split('\n')
                    }

                    def servicesToBuild = [:]
                    def services = [
                        'admin-server': 'spring-petclinic-admin-server',
                        'api-gateway': 'spring-petclinic-api-gateway',
                        'config-server': 'spring-petclinic-config-server',
                        'customers-service': 'spring-petclinic-customers-service',
                        'discovery-server': 'spring-petclinic-discovery-server',
                        'vets-service': 'spring-petclinic-vets-service',
                        'visits-service': 'spring-petclinic-visits-service',
                        'genai-service': 'spring-petclinic-genai-service'
                    ]

                    boolean rootPomChanged = changes.any { it == 'pom.xml' }
                    boolean sharedResourcesChanged = changes.any { change ->
                        change.startsWith('docker/') || 
                        change.startsWith('scripts/') || 
                        change.startsWith('.mvn/') ||
                        change == 'docker-compose.yml'
                    }

                    if (rootPomChanged || sharedResourcesChanged) {
                        services.each { k, _ -> servicesToBuild[k] = true }
                        echo "Shared files or root POM changed. Building all services."
                    } else {
                        services.each { key, path ->
                            if (changes.any { it.startsWith("${path}/") }) {
                                servicesToBuild[key] = true
                                echo "Change detected in ${path}, will build ${key}"
                            }
                        }
                    }

                    if (servicesToBuild.isEmpty()) {
                        servicesToBuild = services.collectEntries { k, _ -> [(k): true] }
                        env.NO_SERVICES_TO_BUILD = 'false'
                        echo "No specific changes. Full rebuild triggered."
                    } else {
                        env.NO_SERVICES_TO_BUILD = 'false'
                    }

                    env.SERVICES_TO_BUILD = servicesToBuild.keySet().join(',')
                    echo "Services to build: ${env.SERVICES_TO_BUILD}"
                }
            }
        }

        stage('Build') {
            when {
                expression { env.NO_SERVICES_TO_BUILD == 'false' }
            }
            steps {
                script {
                    env.SERVICES_TO_BUILD.split(',').each { service ->
                        def modulePath = "spring-petclinic-${service}"
                        dir(modulePath) {
                            echo "🔨 Building ${modulePath}..."
                            sh "../mvnw clean package -DskipTests"
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        env.SERVICES_TO_BUILD.split(',').each { service ->
                            def modulePath = "spring-petclinic-${service}"
                            dir(modulePath) {
                                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                            }
                        }
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            when {
                expression { env.NO_SERVICES_TO_BUILD == 'false' }
            }
            steps {
                script {
                    sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"

                    def commitId = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()

                    env.SERVICES_TO_BUILD.split(',').each { service ->
                        def moduleName = "spring-petclinic-${service}"
                        def imageName = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${commitId}"

                        def exists = sh(script: "docker pull ${imageName} > /dev/null 2>&1 || echo 'missing'", returnStdout: true).trim()
                        if (exists == 'missing') {
                            echo "🐳 Building image ${moduleName}"
                            sh "./mvnw clean install -PbuildDocker -pl ${moduleName}"
                            sh "docker tag springcommunity/${moduleName}:latest ${imageName}"
                            sh "docker push ${imageName}"
                            sh "docker tag ${imageName} ${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:latest"
                            sh "docker push ${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:latest"
                        } else {
                            echo "⏭️ Image ${imageName} already exists. Skipping."
                        }
                    }
                }
            }
        }

        stage('Generate Deployment Summary') {
            when {
                expression { env.NO_SERVICES_TO_BUILD == 'false' }
            }
            steps {
                script {
                    def commitId = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    def fullCommitId = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    def commitMsg = sh(script: 'git log -1 --pretty=format:"%s"', returnStdout: true).trim()
                    def commitAuthor = sh(script: 'git log -1 --pretty=format:"%an <%ae>"', returnStdout: true).trim()
                    def commitDate = sh(script: 'git log -1 --pretty=format:"%ad" --date=iso', returnStdout: true).trim()

                    def summary = """
                    ==================== DEPLOYMENT SUMMARY ====================
                    Commit: ${fullCommitId} (${commitId})
                    Author: ${commitAuthor}
                    Date: ${commitDate}
                    Message: ${commitMsg}

                    Services deployed:
                    """

                    env.SERVICES_TO_BUILD.split(',').each { service ->
                        def image = "${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${commitId}"
                        summary += "    - ${service}: ${image}\n"
                    }

                    summary += "=============================================================\n"

                    echo summary
                    writeFile file: 'deployment-summary.txt', text: summary
                    archiveArtifacts artifacts: 'deployment-summary.txt', fingerprint: true
                }
            }
        }
    }

    post {
        success {
            githubNotify context: 'jenkins-ci', description: 'Pipeline completed successfully', status: 'SUCCESS'
            cleanWs()
            echo '✅ Deployment completed successfully!'
        }
        failure {
            githubNotify context: 'jenkins-ci', description: 'Pipeline failed', status: 'FAILURE'
            cleanWs()
            echo '❌ Deployment failed!'
        }
        unstable {
            githubNotify context: 'jenkins-ci', description: 'Pipeline is unstable', status: 'ERROR'
            cleanWs()
        }
    }
}
