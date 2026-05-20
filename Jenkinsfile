pipeline {
    agent any

    environment {
        SONAR_HOST_URL = 'http://raas-sonarqube:9000'
        SONAR_TOKEN    = credentials('sonar-token')
        APP_NAME       = 'raas-backend'
        APP_PORT       = '8086'
        DOCKER_NETWORK = 'cicd_raas-cicd'
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
                sh 'mvn verify -q'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }



        stage('🔎 SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh '''
                            mvn sonar:sonar \
                              -Dsonar.projectKey=raas-backend \
                              -Dsonar.projectName="RaaS Backend" \
                              -Dsonar.host.url=http://raas-sonarqube:9000 \
                              -Dsonar.token=$SONAR_TOKEN \
                              -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                              -Dsonar.coverage.exclusions="**/entites/**,**/dtos/**,**/mappers/**,**/enums/**,**/config/**,**/exception/**,**/kafka/**,**/security/**,**/repositories/**,**/scheduler/**,**/util/**,**/services/llm/**,**/*Application.java"
                        '''
                    }
                }
            }
        }

        stage('✅ Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
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
                    docker run -d \
                        --name ${APP_NAME} \
                        --network ${DOCKER_NETWORK} \
                        -p ${APP_PORT}:${APP_PORT} \
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