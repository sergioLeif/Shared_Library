import org.bbva.sharedLibraries.Utilidades

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def utils = new Utilidades(steps)
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	// now build, based on the configuration provided
	node () {
		//def String jobName = env.JOB_NAME.replace("/", "_")
		def String UUAA = config.UUAA.toUpperCase()
		
		// Deployment task
		def String DEPLOYMENT_TASK
		if (config.DEPLOYMENT_TASK != null) DEPLOYMENT_TASK = config.DEPLOYMENT_TASK.toUpperCase()
		else DEPLOYMENT_TASK = 'NO'
		// Auto deploy
		def String AUTO_DEPLOY
		if (config.AUTO_DEPLOY != null) AUTO_DEPLOY = config.AUTO_DEPLOY.toUpperCase()
		else AUTO_DEPLOY = 'NO'
		// Deploy Libs Artifactory
		def String DEPLOY_LIBS_ARTIFACTORY
		if (config.DEPLOY_LIBS_ARTIFACTORY != null) DEPLOY_LIBS_ARTIFACTORY = config.DEPLOY_LIBS_ARTIFACTORY.toUpperCase()
		else DEPLOY_LIBS_ARTIFACTORY = 'NO'
		// Sensitive Information
		def String UPDATE_SENSITIVE_INFORMATION
		if (config.UPDATE_SENSITIVE_INFORMATION != null) UPDATE_SENSITIVE_INFORMATION = config.UPDATE_SENSITIVE_INFORMATION.toUpperCase()
		else UPDATE_SENSITIVE_INFORMATION = 'NO'
		
		def String jobName = env.JOB_NAME.replace("%2F", "_").replace("/", "_")
		def String workspace = env.JENKINS_HOME +"/jobs/"+ jobName +"/workspace/"
		def String rutaBinario = workspace + "/" + UUAA
		def String release = 'NO'
		def String sonar = ''
		def String ISSUE_KEY = config.ISSUE_KEY
		def String ArtifactoryURL = 'http://cipdartifactory.igrupobbva:8083/artifactory'
		def String JAVA = "tool ${config.JAVA_VERSION}"		
		def server
		def buildInfo
		def rtMaven
		properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '2']]]);

		//if(utils.validateUUAA(config.GIT_REPO_URL, UUAA)){
			if(utils.validateBranchName(env.BRANCH_NAME)){
				ws (workspace) {
					stage ('Donwload Sources') {
						//utils.downLoadSources(config.GIT_REPO_URL, jobName, env.BRANCH_NAME)
					}
					
					parallel package: {
						stage ('Package'){
							sh "echo Package"
							//INCLUIR EL PACKAGE DEL DOCUMENTO DE LINX
							withMaven (	maven : config.MAVEN_VERSION,
												jdk : config.JAVA_VERSION) { 
													//sh "mvn clean install -f ${WORKSPACE}/java/ION_Environment"
												}
						}
						
						if (DEPLOYMENT_TASK == "YES" || AUTO_DEPLOY == "YES"){
							stage ('Create release structure'){
								//sh "mkdir -p pr/ebdm/proc/linx/${config.LINX_PROJECT_NAME}"
								//sh "cp linx-${config.LINX_PROJECT_NAME}/${config.LINX_PROJECT_NAME}-distribution/target/${config.LINX_PROJECT_NAME}-distribution-${config.BRANCH}.jar pr/ebdm/proc/linx/${config.LINX_PROJECT_NAME}/"
								//sh "tar -xvf pr/ebdm/proc/linx/${config.LINX_PROJECT_NAME}/${config.LINX_PROJECT_NAME}-distribution-${config.BRANCH}.tar.gz"
								sh "mkdir -p ${UUAA}/${config.LINX_PROJECT_NAME}"
								sh "cp ${config.LINX_PROJECT_NAME}-apps/target/${config.LINX_PROJECT_NAME}-apps-*-jar-with-dependencies.jar ${UUAA}/${config.LINX_PROJECT_NAME}/"
								sh "cp -R ${config.LINX_PROJECT_NAME}-apps/target/deploy-resources-files/* ${UUAA}/${config.LINX_PROJECT_NAME}/"
							}
						
							stage ('Deploy release to artifactory'){
								utils.subidaArtifactory(UUAA, rutaBinario, config.DEPLOY_TYPE, jobName, AUTO_DEPLOY, env.BRANCH_NAME, env.JOB_NAME)
							}
						}
						
						if (DEPLOY_LIBS_ARTIFACTORY == "YES"){
							stage ('Deploy library to artifactory'){
	                    
								steps.withCredentials([usernamePassword(credentialsId: UUAA,
								usernameVariable: 'USER_ARTIFACTORY', passwordVariable: 'PASSWORD_ARTIFACTORY')]){
									withEnv (["env.JAVA_HOME=${JAVA}"])
										{
										server = Artifactory.newServer url: ArtifactoryURL , credentialsId: UUAA
										rtMaven = Artifactory.newMavenBuild()
										rtMaven.tool = config.MAVEN_VERSION // Tool name from Jenkins configuration
										rtMaven.deployer releaseRepo: 'APP_Release', snapshotRepo: 'APP_Snapshot', server: server
										rtMaven.deployer.deployArtifacts = false // Disable artifacts deployment during Maven run
										buildInfo = Artifactory.newBuildInfo()
										rtMaven.run pom: '${WORKSPACE}/pom.xml', goals: 'install -Dmaven.repo.local=${WORKSPACE}/.repository', buildInfo: buildInfo 
										def Boolean isRelease = utils.getRelease(env.WORKSPACE)
										if (isRelease)
										{
											release = 'YES'
											sh "echo Valor promotioner: $release"
										}
										else{
											rtMaven.deployer.deployArtifacts buildInfo
											server.publishBuildInfo buildInfo
										}
									}
								}
							}
						}
				        
						if (DEPLOYMENT_TASK == "YES"){
							stage ('Create deployment issue'){
								  ISSUE_KEY=utils.createDeploymentIssue(jobName, config.LAST_TAG, config.JIRA_PROJECT_ID, AUTO_DEPLOY, UUAA, config.DEPLOYMENT_PLAN, config.JIRA_AFFECTED_VERSION, env.BRANCH_NAME, env.BUILD_NUMBER, UPDATE_SENSITIVE_INFORMATION)
							}
						}
				        
						if (AUTO_DEPLOY == "YES"){
							stage ('Auto deploy'){
								sh "echo AutoDeploy"
								utils.deployApp(ISSUE_KEY, UUAA, DEPLOYMENT_TASK, jobName, env.BUILD_NUMBER, config.DEPLOYMENT_PLAN, env.BRANCH_NAME, config.ENVIRONMENT, UPDATE_SENSITIVE_INFORMATION)
							}
						}
					},
					qa: 
						stage ('Execute QA Analysis'){
							//utils.executeQA(config.ARQUITECTURA, config.LENGUAJE, UUAA, jobName, env.BRANCH_NAME)
							sh "echo SQA"	
						}
						
						if (DEPLOYMENT_TASK == "YES" || DEPLOY_LIBS_ARTIFACTORY == "YES"){
							stage ('Report QA Analysis'){
								try {
									sh "echo check"
									sonar = utils.informQA(this, env.JOB_NAME)
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
			}else{
				sh "echo Branch has some strange character"
				sh "exit -1"
			}
		//}else{
		//	sh "echo UUAA is wrong"
		//	sh "exit -1"
		//}
	}
}
