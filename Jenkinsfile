pipeline {
    agent any

    environment {
        SONAR_HOST_URL = 'http://raas-sonarqube:9000'
        SONAR_TOKEN    = credentials('sonar-token')
        APP_NAME       = 'raas-backend'
        APP_PORT       = '8090'
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    stages {

        stage('🔍 Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH}"
            }
        }

        stage('🏗 Build') {
            steps {
                sh 'mvn clean compile -q'
            }
        }

        stage('🧪 Tests') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('📦 Package') {
            steps {
                sh 'mvn package -DskipTests -q'
            }
        }

        stage('🔎 SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=raas-backend \
                          -Dsonar.projectName='RaaS Backend' \
                          -Dsonar.host.url=${SONAR_HOST_URL} \
                          -Dsonar.token=${SONAR_TOKEN}
                    """
                }
            }
        }

        stage('✅ Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('🐳 Docker Build') {
            steps {
                sh """
                    docker build -t ${APP_NAME}:${BUILD_NUMBER} .
                    docker tag ${APP_NAME}:${BUILD_NUMBER} ${APP_NAME}:latest
                """
            }
        }

        stage('🚀 Deploy') {
            steps {
                sh """
                    docker stop ${APP_NAME} || true
                    docker rm   ${APP_NAME} || true
                    docker ps -q --filter publish=${APP_PORT} | xargs -r docker stop || true
                    docker ps -aq --filter publish=${APP_PORT} | xargs -r docker rm  || true
                    docker run -d \
                        --name ${APP_NAME} \
                        --network raas-cicd \
                        -p ${APP_PORT}:${APP_PORT} \
                        --env-file .env \
                        ${APP_NAME}:latest
                """
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline réussie — RaaS déployé'
        }
        failure {
            echo '❌ Pipeline échouée — vérifier les logs'
        }
    }
}