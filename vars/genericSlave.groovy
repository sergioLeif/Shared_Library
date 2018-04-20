import org.bbva.sharedLibraries.Utilidades
import org.bbva.ketr.sharedLibraries.KETRUtilities

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def utils = new Utilidades(steps)
	def utilsKETR = new KETRUtilities(steps)
	def config = [:]
	
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	// now build, based on the configuration provided
	//docker.image('jenkins/jnlp-slave').inside {
	node{
		def date = new Date().format("yyyyMMddHHmmss")
		def containerID = "${config.UUAA}_${env.BRANCH_NAME}-${date}"
		//sh "docker run -d --rm --name ${containerID} jenkins/jnlp-slave -url http://192.168.2.127:8080 -workDir=/home/jenkins/agent 05d9f81216df782f70cef38ff2ae25030c18188c27a36e6cea138ab3b6b14048 jenkins-slave &"
		sh "docker run --rm --name ${containerID} xva_slave:latest &"
		sh "docker exec -d ${containerID} java -jar /home/jenkins/slave.jar -jnlpUrl http://192.168.2.127:8080/computer/jenkins-slave/slave-agent.jnlp -secret 05d9f81216df782f70cef38ff2ae25030c18188c27a36e6cea138ab3b6b14048"
	node ('slave') {
		/**
		 * DEFINICION DE VARIBLES
		 */
		jdk = tool name: "${config.JAVA_VERSION}"
		env.JAVA_HOME="${jdk}"
		def String UUAA = config.UUAA.toUpperCase()
		def String jobName = env.JOB_NAME.replace("%2F", "_").replace("/", "_")
		def String workspace = "/home/jenkins/jenkins_home/jobs/"+ jobName +"/workspace/"
		def String rutaBinario = workspace + "/" + UUAA
		def String release = 'NO'
		def String sonar = ''
		def String ISSUE_KEY = ''
		def String ArtifactoryURL = 'http://urlEjemplo:8083/artifactory'
		//def String JAVA = "tool '${config.JAVA_VERSION}'"
		def server
		def buildInfo
		def rtMaven
		
		def String DEPLOYMENT_TASK
		if (config.DEPLOYMENT_TASK != null) DEPLOYMENT_TASK = config.DEPLOYMENT_TASK.toUpperCase()
		else DEPLOYMENT_TASK = 'NO'
		
		def String CIRCUITO 
		if (config.DEPLOYMENT_PLAN != null) CIRCUITO = config.DEPLOYMENT_PLAN.toUpperCase()
		else DEPLOYMENT_TASK = 'VACIO'
		
		def String AUTO_DEPLOY
		if (config.AUTO_DEPLOY != null) AUTO_DEPLOY = config.AUTO_DEPLOY.toUpperCase()
		else AUTO_DEPLOY = 'NO'
		
		def String DEPLOY_LIBS_ARTIFACTORY
		if (config.DEPLOY_LIBS_ARTIFACTORY != null) DEPLOY_LIBS_ARTIFACTORY = config.DEPLOY_LIBS_ARTIFACTORY.toUpperCase()
		else DEPLOY_LIBS_ARTIFACTORY = 'NO'
		
		def String UPDATE_SENSITIVE_INFORMATION
		if (config.UPDATE_SENSITIVE_INFORMATION != null) UPDATE_SENSITIVE_INFORMATION = config.UPDATE_SENSITIVE_INFORMATION.toUpperCase()
		else UPDATE_SENSITIVE_INFORMATION = 'NO'
		
		
		/** 
		 * FIJAR QUE SOLO SE GUARDEN x EJECUCIONES 
		 */
		//properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '2']]]);

		/**
		 * ESTRUCTURA DE LA PIPELINE
		 */
		//if(utils.validateUUAA(config.GIT_REPO_URL, UUAA)){
			//if(utils.validateBranchName(env.BRANCH_NAME)){
				ws (workspace) {
					stage ('Donwload Sources') {
						//utils.downLoadSources(config.GIT_REPO_URL, jobName, env.BRANCH_NAME, JH)
					}
					
					parallel package: {
						stage ('Package'){
							sh "echo Package"
							//sh "${SCRIPTS_CI}/deleteRepository.sh ${jobName}"
							//steps.withCredentials([usernamePassword(credentialsId: UUAA,
								//usernameVariable: 'USER_ARTIFACTORY', passwordVariable: 'PASSWORD_ARTIFACTORY')]){
									if (UUAA == "KETR"){
										//utilsKETR.createPackage(env.WORKSPACE, config.MAVEN_VERSION, config.JAVA_VERSION)
										
									}
								//}
						}
						
						if (DEPLOYMENT_TASK == "YES" || AUTO_DEPLOY == "YES"){
							stage ('Create release structure'){
								if (UUAA == "KETR"){ 
									//utilsKETR.createReleaseStructure(env.WORKSPACE, UUAA, SCRIPTS_DEVOPS)
								}
							}
						
							stage ('Deploy release to artifactory'){
								//utils.subidaArtifactory(UUAA, rutaBinario, config.DEPLOY_TYPE, jobName, AUTO_DEPLOY, env.BRANCH_NAME, env.JOB_NAME, CIRCUITO, SCRIPTS_DEVOPS)
							}
						}
						
						if (DEPLOY_LIBS_ARTIFACTORY == "YES"){
							stage ('Deploy library to artifactory'){
								steps.withCredentials([usernamePassword(credentialsId: UUAA,
								usernameVariable: 'USER_ARTIFACTORY', passwordVariable: 'PASSWORD_ARTIFACTORY')]){
									//withEnv (["env.JAVA_HOME=\"${tool 'JDK_1.7'}\""]) {
									    sh "echo el java_home es: ${env.JAVA_HOME}"
										server = Artifactory.newServer url: ArtifactoryURL , credentialsId: UUAA
										rtMaven = Artifactory.newMavenBuild()
										rtMaven.tool = config.MAVEN_VERSION // Tool name from Jenkins configuration
										def rutaPom = ""
										if (config.LIB_POM_PATH == "") {
											rutaPom = env.WORKSPACE+"/pom.xml"
										} else {
											rutaPom = env.WORKSPACE+"/"+config.LIB_POM_PATH+"/pom.xml"
										}
										rtMaven.deployer releaseRepo: 'APP_Release', snapshotRepo: 'APP_Snapshot', server: server
										rtMaven.deployer.deployArtifacts = false // Disable artifacts deployment during Maven run
										buildInfo = Artifactory.newBuildInfo()
										rtMaven.run pom: rutaPom, goals: 'install -Dmaven.repo.local=${WORKSPACE}/.repository', buildInfo: buildInfo 
										
										def Boolean isRelease = utils.getRelease(env.WORKSPACE)
										if (isRelease){
											release = 'YES'
											sh "echo Valor promotioner: $release"
										}else{
											rtMaven.deployer.deployArtifacts buildInfo
											server.publishBuildInfo buildInfo
										}
									//}
								}
							}
						}
						
						if (DEPLOYMENT_TASK == "YES"){
							stage ('Create deployment issue'){
								sleep 5
									//ISSUE_KEY=utils.createDeploymentIssue(jobName, config.LAST_TAG, config.JIRA_PROJECT_ID, AUTO_DEPLOY, UUAA, config.DEPLOYMENT_PLAN, config.JIRA_AFFECTED_VERSION, env.BRANCH_NAME, env.BUILD_NUMBER, UPDATE_SENSITIVE_INFORMATION, config.DEPLOY_TYPE, SCRIPTS_DEVOPS)
							}
						}
				        
						if (AUTO_DEPLOY == "YES"){
							stage ('Auto deploy'){
							    //utils.deployApp(ISSUE_KEY, UUAA, DEPLOYMENT_TASK, jobName, env.BUILD_NUMBER, config.DEPLOYMENT_PLAN, config.ENVIRONMENT, UPDATE_SENSITIVE_INFORMATION, SCRIPTS_DEVOPS)
							}
						}
						
						if (DEPLOYMENT_TASK == "YES" || AUTO_DEPLOY == "YES"){
							stage ('Create tag in repository'){
								steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'DownloadGit', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
									def MENSAJE=''
									def FECHA = new Date().format("HHmmddMM")
									//def REPO = utils.getRepoWithoutProtocol(config.GIT_REPO_URL, jobName, SCRIPTS_DEVOPS)
									
									if(env.BRANCH_NAME.startsWith("master") || env.BRANCH_NAME.startsWith("release/") || env.BRANCH_NAME.startsWith("hotfix")){
										MENSAJE='Codigo desplegado en la peticion ${ISSUE_KEY}'
									} else {
										MENSAJE='Codigo asociado al despliegue realizado en la compilacion de Jenkins ${BUILD_NUMBER}'
									}
									
									def fileTemp = "${SCRIPTS_DEVOPS}/tmp/Repo_${jobName}.txt"
									sh "echo ${config.GIT_REPO_URL} | cut -c8- > ${fileTemp}"
									def REPO = readFile(fileTemp).trim()
									sh "rm ${fileTemp}"

									sh "cd ${env.WORKSPACE}"
									sh "git tag -a Devops_${FECHA} -m \"${MENSAJE}\""
									sh "git push http://${GIT_USERNAME}:${GIT_PASSWORD}@${REPO} --tags"
								}
							}		
						}
					},
					qa: {
						stage ('Execute QA Analysis'){
							//utils.executeQA(config.ARQUITECTURA, config.LENGUAJE, UUAA, jobName, env.BRANCH_NAME, SCRIPTS_SQA)
							sh "echo SQA"	
						}
						
						if (DEPLOYMENT_TASK == "YES" || DEPLOY_LIBS_ARTIFACTORY == "YES"){
							stage ('Report QA Analysis'){
								try {
									sh "echo check"
									sonar = utils.informQA(this, env.JOB_NAME, SCRIPTS_DEVOPS)
								} catch (err){
									throw err
									sh "exit -1"
								}
							}
						}
					}
						
					if (DEPLOY_LIBS_ARTIFACTORY == "YES"){
						stage ('Promotioner library'){
							if (release == 'YES' && sonar == 'ok'){	
								rtMaven.deployer.deployArtifacts buildInfo
								server.publishBuildInfo buildInfo
								sonar = 'FINISHED'
								sh "echo $sonar"
							}
						}
					}
					
				}
			//}else{
				//sh "echo Branch has some strange character"
				//sh "exit -1"
			//}
		//}else{
		//	sh "echo UUAA is wrong"
		//	sh "exit -1"
		//}
		//}
		}
		sh "docker stop ${containerID}"
	}
}
