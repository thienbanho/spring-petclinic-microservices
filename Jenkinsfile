pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        SERVICES = "customers-service visits-service vets-service genai-service admin-server config-server api-gateway discovery-server"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "✅ Checked out source code."
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    // Map để lưu thông tin về các service cần build và commit ID
                    SERVICES_TO_BUILD = [:]
                    
                    // Định nghĩa tên các service và đường dẫn module tương ứng
                    def serviceModuleMap = [
                        'customers-service': 'spring-petclinic-customers-service',
                        'visits-service': 'spring-petclinic-visits-service',
                        'vets-service': 'spring-petclinic-vets-service',
                        'genai-service': 'spring-petclinic-genai-service',
                        'admin-server': 'spring-petclinic-admin-server',
                        'config-server': 'spring-petclinic-config-server',
                        'api-gateway': 'spring-petclinic-api-gateway',
                        'discovery-server': 'spring-petclinic-discovery-server'
                    ]
                    
                    // Lấy danh sách các file đã thay đổi
                    def changes = []
                    if (env.CHANGE_TARGET) {
                        // Nếu là PR build
                        sh """
                            git fetch --no-tags origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET}
                            git fetch --no-tags origin ${env.GIT_COMMIT}:refs/remotes/origin/PR-${env.CHANGE_ID}
                        """
                        changes = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET} HEAD", returnStdout: true).trim().split('\n')
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        // Nếu là branch build với commit thành công trước đó
                        changes = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}", returnStdout: true).trim().split('\n')
                    } else {
                        // So sánh với commit trước đó
                        changes = sh(script: "git diff --name-only HEAD^", returnStdout: true).trim().split('\n')
                    }
                    
                    // Kiểm tra thay đổi trong file pom.xml gốc hoặc tài nguyên dùng chung
                    boolean rootPomChanged = changes.any { it == 'pom.xml' }
                    boolean sharedResourcesChanged = changes.any { change ->
                        change.startsWith('docker/') || 
                        change.startsWith('scripts/') || 
                        change.startsWith('.mvn/') ||
                        change == 'docker-compose.yml'
                    }
                    
                    // Nếu tài nguyên dùng chung thay đổi, build tất cả các service
                    if (rootPomChanged || sharedResourcesChanged) {
                        echo "Phát hiện thay đổi trong tài nguyên dùng chung. Cần build lại tất cả service."
                        SERVICES.split().each { service ->
                            def commitId = getCommitId(serviceModuleMap[service])
                            SERVICES_TO_BUILD[service] = [
                                'commitId': commitId,
                                'shouldBuild': shouldRebuildImage(commitId, service)
                            ]
                        }
                    } else {
                        // Kiểm tra từng service có thay đổi không
                        SERVICES.split().each { service ->
                            def modulePath = serviceModuleMap[service]
                            if (changes.any { it.startsWith("${modulePath}/") }) {
                                def commitId = getCommitId(modulePath)
                                SERVICES_TO_BUILD[service] = [
                                    'commitId': commitId,
                                    'shouldBuild': shouldRebuildImage(commitId, service)
                                ]
                                echo "Phát hiện thay đổi trong service: ${service}"
                            }
                        }
                    }
                    
                    // Hiển thị tóm tắt
                    if (SERVICES_TO_BUILD.isEmpty()) {
                        echo "Không phát hiện thay đổi nào. Bỏ qua các bước build và deploy."
                    } else {
                        echo "Danh sách service cần build: ${SERVICES_TO_BUILD.keySet().join(', ')}"
                        SERVICES_TO_BUILD.each { service, info ->
                            echo "  - ${service}: commit=${info.commitId}, shouldBuild=${info.shouldBuild}"
                        }
                    }
                }
            }
        }

        stage('Build and Test') {
            when {
                expression { return !SERVICES_TO_BUILD.isEmpty() }
            }
            steps {
                script {
                    SERVICES_TO_BUILD.each { service, info ->
                        if (info.shouldBuild) {
                            def moduleName = "spring-petclinic-${service}"
                            echo "Building và testing ${service}..."
                            sh "./mvnw -pl ${moduleName} verify"
                        } else {
                            echo "Bỏ qua build cho ${service}, image đã tồn tại."
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        SERVICES_TO_BUILD.each { service, info ->
                            if (info.shouldBuild) {
                                def moduleName = "spring-petclinic-${service}"
                                archiveArtifacts artifacts: "${moduleName}/target/*.jar", fingerprint: true
                            }
                        }
                    }
                }
            }
        }

        stage('Build JAR Files') {
            when {
                expression { return !SERVICES_TO_BUILD.isEmpty() }
            }
            steps {
                script {
                    SERVICES_TO_BUILD.each { service, info ->
                        if (info.shouldBuild) {
                            def moduleName = "spring-petclinic-${service}"
                            echo "Building JAR cho module: ${moduleName}"
                            sh "./mvnw clean package -pl ${moduleName} -am -DskipTests"
                        }
                    }
                }
            }
        }

        stage('Build and Push Docker Images') {
            when {
                expression { return !SERVICES_TO_BUILD.isEmpty() }
            }
            steps {
                script {
                    // Login to Docker Hub once
                    echo "Đăng nhập vào Docker Hub"
                    sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"
                    
                    SERVICES_TO_BUILD.each { service, info ->
                        if (info.shouldBuild) {
                            def moduleName = "spring-petclinic-${service}"
                            def targetImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${info.commitId}"
                            def jarFilePath = sh(script: "ls ${moduleName}/target/*.jar | head -n 1", returnStdout: true).trim()
                            def artifactName = jarFilePath.tokenize('/').last().replace('.jar', '')

                            echo "Kiểm tra file JAR: ${jarFilePath}"
                            if (!fileExists(jarFilePath)) {
                                error "Không tìm thấy file JAR: ${jarFilePath}. Hãy đảm bảo đã build trước đó."
                            }
                            echo "Building Docker image cho ${service}"
                            sh """
                            docker build \
                            -f docker/Dockerfile \
                            --build-arg ARTIFACT_NAME=${artifactName} \
                            -t ${targetImage} \
                            ${moduleName}/target
                            """
                            echo "Đẩy image ${targetImage} lên Docker Hub"
                            sh "docker push ${targetImage}"
                            sh "docker rmi ${targetImage} || true"

                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'CI/CD pipeline hoàn thành thành công!'
        }
        failure {
            echo 'CI/CD pipeline thất bại!'
        }
        always {
            echo 'Dọn dẹp workspace...'
            cleanWs()
        }
    }
}

// Lấy commit ID cho một module
def getCommitId(String modulePath) {
    return sh(script: "cd ${modulePath} && git rev-parse --short HEAD", returnStdout: true).trim()
}

// Kiểm tra xem có cần build lại image không
def shouldRebuildImage(String commitId, String service) {
    def imageTag = "${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${commitId}"
    def exists = sh(script: "docker pull ${imageTag} > /dev/null 2>&1 || echo 'missing'", returnStdout: true).trim()
    return (exists == 'missing')
}