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
    }

    stages {
        stage('Checkout Code') {
            steps {
                script {
                    COMMIT_IDS = [:]
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
                        COMMIT_IDS[service] = checkoutService(service, branchMap[service])
                        echo "Commit ID of ${service}: ${COMMIT_IDS[service]}"
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            steps {
                script {
                    SERVICES.split().each { service ->
                        def tag = (COMMIT_IDS[service] && COMMIT_IDS[service] != 'main') ? COMMIT_IDS[service] : 'latest'
                        def moduleName = "spring-petclinic-${service}"
                        def sourceImage = "springcommunity/${moduleName}:latest"
                        def targetImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${tag}"

                        echo "🐳 Building Docker image for ${service} using Maven"
                        sh "./mvnw clean install -PbuildDocker -pl ${moduleName}"

                        echo "🔐 Logging in to Docker Hub"
                        sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"

                        echo "🏷️ Tagging image: ${sourceImage} -> ${targetImage}"
                        sh "docker tag ${sourceImage} ${targetImage}"

                        echo "📤 Pushing ${targetImage} to Docker Hub"
                        sh "docker push ${targetImage}"
                }
            }
        }

        stage('Deploy to Kubernetes with Helm') {
            steps {
                script {
                    def yaml = SERVICES.split().collect { service ->
                        def imageTag = COMMIT_IDS[service] ?: 'latest'
                        def imagePath = "${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${imageTag}"
                        def serviceBlock = (service == 'api-gateway') ? """
                          service:
                            type: NodePort
                            port: 80
                            nodePort: 30080
                        """ : ""
                        """  ${service}:\n    image: ${imagePath}${serviceBlock}"""
                    }.join("\n")

                    writeFile file: 'values.yaml', text: "services:\n${yaml}"
                    sh "helm upgrade --install petclinic ./helm-chart -f values.yaml --namespace developer --create-namespace"
                }
            }
        }

        stage('Provide Access URL') {
            steps {
                script {
                    def ip = sh(script: "minikube ip || kubectl get nodes -o wide | awk 'NR==2{print \$6}'", returnStdout: true).trim()
                    echo "Access the app at: http://petclinic.local:30080"
                    echo "Add to /etc/hosts: ${ip} petclinic.local"
                }
            }
        }
    }

    post {
        success {
            echo '✅ Deployment completed successfully!'
        }
        failure {
            echo '❌ Deployment failed!'
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
        return (branch == 'main') ? 'main' : sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }
}

def buildAndPushDockerImage(String service, String tag, String port) {
    dir(service) {
        echo "▶ Building JAR for ${service}"
        sh '../mvnw clean install -PbuildDocker -DskipTests'

        def artifactName = getJarArtifactName(service)
        echo "▶ Found artifact: ${artifactName}.jar"

        echo "🐳 Building Docker image for ${service} with tag ${tag}"
        sh """
            docker build -f docker/Dockerfile \\
                --build-arg ARTIFACT_NAME=${artifactName} \\
                --build-arg EXPOSED_PORT=${port} \\
                -t ${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${tag} .
        """

        echo "📤 Pushing image to Docker Hub"
        sh """
            docker login -u ${DOCKERHUB_CREDENTIALS_USR} -p ${DOCKERHUB_CREDENTIALS_PSW}
            docker push ${DOCKERHUB_CREDENTIALS_USR}/spring-petclinic-${service}:${tag}
        """
    }
}

def getJarArtifactName(String service) {
    dir(service) {
        def jarPath = sh(
        script: "ls target/*.jar | grep -v 'original' | head -n 1",
            returnStdout: true
        ).trim()
    }

    return jarPath.replaceFirst(/^target\//, '').replaceFirst(/\.jar$/, '')
}