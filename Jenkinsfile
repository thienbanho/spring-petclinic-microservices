pipeline {
    agent any

    parameters {
        string(name: 'CUSTOMERS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for customers-service')
        string(name: 'VISITS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for visits-service')
        string(name: 'VETS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for vets-service')
        string(name: 'GENAI_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for genai-service')
    }

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        SERVICES = "customers-service visits-service vets-service genai-service admin-server config-server api-gateway discovery-server"
        MAVEN_HOME = tool 'Maven'
        GITHUB_TOKEN = credentials('github-token')
    }


    stages {

        stage('Checkout') {
            steps {
                githubNotify context: 'jenkins-ci', 
                           description: 'Jenkins Pipeline Started',
                           status: 'PENDING'
                checkout scm
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
                    // Store the services to build in environment variable
                    env.SERVICES_TO_BUILD = servicesToBuild.keySet().join(',')
                    
                    // Print summary
                    if (env.NO_SERVICES_TO_BUILD == 'true') {
                        echo "No service changes detected. Pipeline will skip build and test stages."
                    } else {
                        echo "Services to build: ${env.SERVICES_TO_BUILD}"
                    }
                }
            }
        }   

        stage('Checkout Code & Check Changes') {
            steps {
                script {
                    COMMIT_IDS = [:]
                    SHOULD_BUILD = [:]

                    def branchMap = [
                        'customers-service': params.CUSTOMERS_SERVICE_BRANCH,
                        'visits-service'   : params.VISITS_SERVICE_BRANCH,
                        'vets-service'     : params.VETS_SERVICE_BRANCH,
                        'genai-service'    : params.GENAI_SERVICE_BRANCH,
                        'admin-server'     : 'main',
                        'config-server'    : 'main',
                        'api-gateway'      : 'main',
                        'discovery-server' : 'main'
                    ]

                    SERVICES.split().each { service ->
                        def branch = branchMap[service]
                        def commitId = checkoutService(service, branch)
                        COMMIT_IDS[service] = commitId

                        // Check if image with this commit already exists
                        def imageTag = "${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${commitId}"
                        def exists = sh(script: "docker pull ${imageTag} > /dev/null 2>&1 || echo 'missing'", returnStdout: true).trim()

                        SHOULD_BUILD[service] = (exists == 'missing')
                        echo "⏱️ Should build ${service}? ${SHOULD_BUILD[service]}"
                    }
                }
            }
        }

        

        stage('Build, Verify & Push Docker Images') {
            steps {
                script {
                    SERVICES.split().each { service ->
                        if (!SHOULD_BUILD[service]) {
                            echo "⏭️ Skipping ${service}, already built."
                            return
                        }

                        def commitId = COMMIT_IDS[service]
                        def moduleName = "spring-petclinic-${service}"
                        def targetImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${commitId}"

                        echo "🔍 Verifying ${service}"
                        sh "./mvnw -pl ${moduleName} verify"

                        echo "🐳 Building Docker image for ${service}"
                        sh "./mvnw clean install -PbuildDocker -pl ${moduleName}"

                        echo "🔐 Logging in to Docker Hub"
                        sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"

                        echo "🏷️ Tagging image as ${targetImage}"
                        sh "docker tag springcommunity/${moduleName}:latest ${targetImage}"

                        echo "📤 Pushing ${targetImage} to Docker Hub"
                        sh "docker push ${targetImage}"
                    }
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
        }

        // stage('Deploy to Kubernetes with Helm') {
        //     steps {
        //         script {
        //             def yaml = SERVICES.split().collect { service ->
        //                 def imageTag = COMMIT_IDS[service]
        //                 def imagePath = "${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${imageTag}"
        //                 def serviceBlock = (service == 'api-gateway') ? """
        //                   service:
        //                     type: NodePort
        //                     port: 80
        //                     nodePort: 30080
        //                 """ : ""
        //                 """  ${service}:\n    image: ${imagePath}${serviceBlock}"""
        //             }.join("\n")

        //             writeFile file: 'values.yaml', text: "services:\n${yaml}"
        //             sh "helm upgrade --install petclinic ./helm-chart -f values.yaml --namespace developer --create-namespace"
        //         }
        //     }
        // }

        // stage('Provide Access URL') {
        //     steps {
        //         script {
        //             def ip = sh(script: "minikube ip || kubectl get nodes -o wide | awk 'NR==2{print \$6}'", returnStdout: true).trim()
        //             echo "Access the app at: http://petclinic.local:30080"
        //             echo "Add to /etc/hosts: ${ip} petclinic.local"
        //         }
        //     }
        // }
    }

    post {
        success {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline completed successfully',
                        status: 'SUCCESS'
            cleanWs()
        }
        failure {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline failed',
                        status: 'FAILURE'
            cleanWs()
        }
        unstable {
            githubNotify context: 'jenkins-ci',
                        description: 'Pipeline is unstable',
                        status: 'ERROR'
            cleanWs()
        }
    }
}

def checkoutService(String service, String branch) {
    dir(service) {
        checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            userRemoteConfigs: [[
                url: "https://github.com/thienbanho/spring-petclinic-microservices.git",
                credentialsId: 'jenkins-petclinic-dthien'
            ]]
        ])
        return sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }
}