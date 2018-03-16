//generic.groovy

import org.bbva.sharedLibraries.UtilidadesWindows
import org.bbva.envm.sharedLibraries.ENVMUtilities
import org.bbva.kyuf.sharedLibraries.KYUFUtilities
import org.bbva.ketr.sharedLibraries.KETRUtilities

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def utils = new UtilidadesWindows(steps)
	def utilsENVM = new ENVMUtilities(steps)
	def utilsKYUF = new KYUFUtilities(steps)
	def utilsKETR = new KETRUtilities(steps)
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	// now build, based on the configuration provided
	node () {
		def String UUAA = config.UUAA.toUpperCase()
		def String DEPLOYMENT_TASK = config.DEPLOYMENT_TASK.toUpperCase()
		def String AUTO_DEPLOY = config.AUTO_DEPLOY.toUpperCase()
		def String DEPLOY_LIBS_ARTIFACTORY = config.DEPLOY_LIBS_ARTIFACTORY.toUpperCase()
		def String UPDATE_SENSITIVE_INFORMATION = config.UPDATE_SENSITIVE_INFORMATION.toUpperCase()
		def String jobName = env.JOB_NAME.replace("%2F", "_").replace("/", "_")
		def String workspace = env.JENKINS_HOME +"/jobs/"+ jobName +"/workspace/"
		def String rutaBinario = workspace + "/" + UUAA
		def String release = 'NO'
		def String sonar = ''
		def String ISSUE_KEY = ''
		def String ArtifactoryURL = 'http://cipdartifactory.igrupobbva:8083/artifactory'
		def String JAVA = "tool ${config.JAVA_VERSION}"
		//def  = (env.BRANCH_NAME).split("/").(2);
		def server
		def buildInfo
		def rtMaven
		properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '2']]]);
		
		//DESCOMENTAR PARA PRODUCCION
		//if(utils.validateUUAA(config.GIT_REPO_URL, UUAA)){
			if(utils.validateBranchName(env.BRANCH_NAME)){
				ws (workspace) {
					node (config.NODO) {
						def String slavePath = 'C:/JenkinsSlave'
						def String workspaceSlave = slavePath +'/jobs/'+ jobName +'/workspace/'
						ws (workspaceSlave) {
							stage ('Donwload Sources') {
								utils.downLoadSources(config.GIT_REPO_URL, jobName, env.BRANCH_NAME)
							}
							
							stage ('Package'){
								bat "echo Package"
								//bat "${env.RUTA_SCRIPT_CI}/deleteRepository.sh ${jobName}"
								steps.withCredentials([usernamePassword(credentialsId: UUAA,
									usernameVariable: 'USER_ARTIFACTORY', passwordVariable: 'PASSWORD_ARTIFACTORY')]){
										if (UUAA == "ENVM"){ 
											utilsENVM.createPackage(env.WORKSPACE, config.MAVEN_VERSION, config.JAVA_VERSION)
										}
										if (UUAA == "KYUF"){ 
											utilsKYUF.createPackage(env.WORKSPACE, config.MAVEN_VERSION, config.JAVA_VERSION)
										}
										if (UUAA == "KETR"){
											utilsKETR.createPackage(env.WORKSPACE, config.MAVEN_VERSION, config.JAVA_VERSION)
											
										}
									}
							}
						}
					}
					parallel package: {	
						if (DEPLOYMENT_TASK == "YES" || AUTO_DEPLOY == "YES"){
							stage ('Create release structure'){
								if (UUAA == "ENVM"){ 
									utilsENVM.createReleaseStructure(env.WORKSPACE, UUAA)
								}
								if (UUAA == "KYUF"){ 
									utilsKYUF.createReleaseStructure(env.WORKSPACE, UUAA)
								}
								if (UUAA == "KETR"){ 
									utilsKETR.createReleaseStructure(env.WORKSPACE, UUAA)
								}
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
					qa: {
						stage ('Execute QA Analysis'){
							//utils.executeQA(config.ARQUITECTURA, config.LENGUAJE, UUAA, jobName, env.BRANCH_NAME)
							sh "echo SQA"	
						}

						if (DEPLOYMENT_TASK == "YES" || DEPLOY_LIBS_ARTIFACTORY == "YES"){			
							stage ('Report QA Analysis'){
							
									try {
										sh "echo check"
										sonar = utils.informQA(this, env.JOB_NAME)
										//condicionar valor dependiendo del codigo extraido
										sh "echo $sonar"
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
									}
								}
						}
				}
			}else{
				sh "echo Branch has some strange character"
				sh "exit -1"
			}
		//}else{
			//sh "echo UUAA is wrong"
			//sh "exit -1"
		//}	
	}
}
