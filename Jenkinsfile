pipeline {
    agent any
    environment {
        DOCKER_IMAGE = 'thienbanho/petclinic'
        COMMIT_ID = "${env.GIT_COMMIT ?: 'latest'}"  // Nếu env.GIT_COMMIT null thì dùng 'latest'
        ARTIFACT_NAME = "spring-petclinic"  // Đặt giá trị phù hợp với tên file .jar
    }
    stages {
        stage('Clone Repository') {
            steps {
                git branch: 'main', credentialsId: 'github-credentials-id', url: 'https://github.com/thienbanho/spring-petclinic-microservices.git'
            }
        }
        stage('Build Java Application') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }
        stage('Build Docker Image') {
            steps {
                sh 'docker build --build-arg ARTIFACT_NAME=spring-petclinic -t $DOCKER_IMAGE:$COMMIT_ID -f docker/Dockerfile .'
            }
        }
        stage('Push to Docker Hub') {
            steps {
                withDockerRegistry([url: '', credentialsId: 'docker-hub-credentials']) {  // URL trống là mặc định Docker Hub
                    sh 'docker push $DOCKER_IMAGE:$COMMIT_ID'
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                kubectl set image deployment/petclinic petclinic=$DOCKER_IMAGE:$COMMIT_ID -n petclinic
                kubectl rollout status deployment/petclinic -n petclinic
                '''
            }
        }
    }
}
