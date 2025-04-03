pipeline {
    agent any
    environment {
        DOCKER_IMAGE = 'thienbanho/petclinic'
        COMMIT_ID = "${env.GIT_COMMIT}"
    }
    stages {
        stage('Clone Repository') {
            steps {
                git 'https://github.com/spring-petclinic/spring-petclinic-microservices.git'
            }
        }
        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $DOCKER_IMAGE:$COMMIT_ID .'
            }
        }
        stage('Push to Docker Hub') {
            steps {
                withDockerRegistry([credentialsId: 'docker-hub-credentials']) {
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
