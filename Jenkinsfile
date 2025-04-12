pipeline {
    agent any
    
    environment {
        MAVEN_HOME = tool 'Maven'
        GITHUB_TOKEN = credentials('github-token')
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        SERVICES = "admin-server api-gateway config-server customers-service discovery-server vets-service visits-service genai-service"
        // Thêm URL GitHub repository
        GITHUB_REPO_URL = "https://github.com/thienbanho/spring-petclinic-microservices.git"
        // Thêm credentials để clone repository nếu cần
        GITHUB_CREDENTIALS_ID = 'jenkins-petclinic-dthien'
    }
    
    stages {
        stage('Checkout') {
            steps {
                githubNotify context: 'jenkins-ci', 
                           description: 'Jenkins Pipeline Started',
                           status: 'PENDING'
                
                // Checkout repository sử dụng URL đã định nghĩa
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: '*/main']], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[
                        credentialsId: "${GITHUB_CREDENTIALS_ID}", 
                        url: "${GITHUB_REPO_URL}"
                    ]]
                ])
                
                // In ra commit hiện tại
                sh "git rev-parse HEAD"
            }
        }
        
        stage('Detect Changes') {
            steps {
                script {
                    // Debug: Print all environment variables
                    echo "Environment variables:"
                    sh 'env | sort'
                    
                    // Get all changed files
                    def changes = []
                    if (env.CHANGE_TARGET) {
                        // If this is a PR build, fetch the target branch first
                        sh """
                            git fetch --no-tags origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET}
                            git fetch --no-tags origin ${env.GIT_COMMIT}:refs/remotes/origin/PR-${env.CHANGE_ID}
                        """
                        changes = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET} HEAD", returnStdout: true).trim().split('\n')
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        // If this is a branch build with previous successful build
                        changes = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}", returnStdout: true).trim().split('\n')
                    } else {
                        // Fallback to comparing with the previous commit
                        changes = sh(script: "git diff --name-only HEAD^", returnStdout: true).trim().split('\n')
                    }

                    // Map to store which services need to be built
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

                    // Check root pom.xml changes
                    boolean rootPomChanged = changes.any { it == 'pom.xml' }
                    
                    // Check shared resources changes (like docker configs, scripts, etc.)
                    boolean sharedResourcesChanged = changes.any { change ->
                        change.startsWith('docker/') || 
                        change.startsWith('scripts/') || 
                        change.startsWith('.mvn/') ||
                        change == 'docker-compose.yml'
                    }

                    // If shared resources changed, build all services
                    if (rootPomChanged || sharedResourcesChanged) {
                        echo "Shared resources changed. Building all services."
                        services.each { serviceKey, servicePath ->
                            servicesToBuild[serviceKey] = true
                        }
                    } else {
                        // Determine which services have changes
                        services.each { serviceKey, servicePath ->
                            if (changes.any { change ->
                                change.startsWith("${servicePath}/")
                            }) {
                                servicesToBuild[serviceKey] = true
                                echo "Will build ${serviceKey} due to changes in ${servicePath}"
                            }
                        }
                    }

                    // If no services need building, set a flag
                    env.NO_SERVICES_TO_BUILD = servicesToBuild.isEmpty() ? 'true' : 'false'
                    
                    // Nếu không có service nào thay đổi, thiết lập một giá trị mặc định
                    if (servicesToBuild.isEmpty()) {
                        // Build tất cả service trong trường hợp đầu tiên chạy
                        servicesToBuild = services.collectEntries { serviceKey, _ -> 
                            [(serviceKey): true] 
                        }
                        env.NO_SERVICES_TO_BUILD = 'false'
                        echo "First run or no specific changes detected. Will build all services."
                    }
                    
                    // Store the services to build in environment variable
                    env.SERVICES_TO_BUILD = servicesToBuild.keySet().join(',')
                    
                    // Print summary
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
                        dir("spring-petclinic-${service}") {
                            echo "Building ${service}..."
                            try {
                                sh """
                                    echo "Building ${service}"
                                    ../mvnw clean package -DskipTests
                                """
                            } catch (Exception e) {
                                echo "Build failed for ${service}"
                                throw e
                            }
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        // Archive artifacts for changed services
                        env.SERVICES_TO_BUILD.split(',').each { service ->
                            dir("spring-petclinic-${service}") {
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
                    // Login to Docker Hub
                    sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"
                    
                    env.SERVICES_TO_BUILD.split(',').each { service ->
                        def moduleName = "spring-petclinic-${service}"
                        
                        // Get commit ID for tagging
                        def commitId = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        def targetImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${commitId}"
                        
                        // Check if image with this commit already exists
                        def exists = sh(script: "docker pull ${targetImage} > /dev/null 2>&1 || echo 'missing'", returnStdout: true).trim()
                        
                        if (exists == 'missing') {
                            echo "🐳 Building Docker image for ${service}"
                            sh "./mvnw clean install -PbuildDocker -pl ${moduleName}"
                            
                            echo "🏷️ Tagging image as ${targetImage}"
                            sh "docker tag springcommunity/${moduleName}:latest ${targetImage}"
                            
                            echo "📤 Pushing ${targetImage} to Docker Hub"
                            sh "docker push ${targetImage}"
                            
                            // Thêm tag latest
                            def latestImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:latest"
                            sh "docker tag ${targetImage} ${latestImage}"
                            sh "docker push ${latestImage}"
                            
                            echo "✅ Docker image cho ${service} đã được đẩy lên: ${targetImage} và ${latestImage}"
                        } else {
                            echo "⏭️ Skipping ${service}, Docker image ${targetImage} already exists."
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
                        def moduleName = "spring-petclinic-${service}"
                        def imageTag = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${commitId}"
                        summary += """    - ${service}: ${imageTag}
                    """
                    }
                    
                    summary += """
                    =============================================================
                    """
                    
                    echo summary
                    
                    // Lưu summary vào một file
                    writeFile file: 'deployment-summary.txt', text: summary
                    archiveArtifacts artifacts: 'deployment-summary.txt', fingerprint: true
                }
            }
        }
    }
    
    post {
        success {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline completed successfully',
                        status: 'SUCCESS'
            cleanWs()
            echo '✅ Deployment completed successfully!'
        }
        failure {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline failed',
                        status: 'FAILURE'
            cleanWs()
            echo '❌ Deployment failed!'
        }
        unstable {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline is unstable',
                        status: 'ERROR'
            cleanWs()
        }
    }
}