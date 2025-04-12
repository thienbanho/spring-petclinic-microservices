pipeline {
    agent any

    // parameters {
    //     string(name: 'CUSTOMERS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for customers-service')
    //     string(name: 'VISITS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for visits-service')
    //     string(name: 'VETS_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for vets-service')
    //     string(name: 'GENAI_SERVICE_BRANCH', defaultValue: 'main', description: 'Branch for genai-service')
    // }

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        SERVICES = "customers-service visits-service vets-service genai-service admin-server config-server api-gateway discovery-server"
    }

    stages {


        stage('Checkout Code & Check Changes') {
            steps {
                script {
                    COMMIT_IDS = [:]
                    SHOULD_BUILD = [:]

                    def branchMap = [
                        'customers-service': 'main',
                        'visits-service'   : 'main',
                        'vets-service'     : 'main',
                        'genai-service'    : 'main',
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

        stage('Checkout') {
            steps {
                checkout scm
                echo "✅ Checked out source code."
            }
        }


        // stage('Build, Verify & Push Docker Images') {
        //     steps {
        //         script {
        //             SERVICES.split().each { service ->
        //                 if (!SHOULD_BUILD[service]) {
        //                     echo "⏭️ Skipping ${service}, already built."
        //                     return
        //                 }

        //                 def commitId = COMMIT_IDS[service]
        //                 def moduleName = "spring-petclinic-${service}"
        //                 def targetImage = "${DOCKERHUB_CREDENTIALS_USR}/${moduleName}:${commitId}"

        //                 echo "🔍 Verifying ${service}"
        //                 sh "./mvnw -pl ${moduleName} verify"

        //                 echo "🐳 Building Docker image for ${service}"
        //                 sh "./mvnw clean install -PbuildDocker -pl ${moduleName}"

        //                 echo "🔐 Logging in to Docker Hub"
        //                 sh "echo '${DOCKERHUB_CREDENTIALS_PSW}' | docker login -u ${DOCKERHUB_CREDENTIALS_USR} --password-stdin"

        //                 echo "🏷️ Tagging image as ${targetImage}"
        //                 sh "docker tag springcommunity/${moduleName}:latest ${targetImage}"

        //                 echo "📤 Pushing ${targetImage} to Docker Hub"
        //                 sh "docker push ${targetImage}"
        //             }
        //         }
        //     }
        // }

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
        return sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }
}