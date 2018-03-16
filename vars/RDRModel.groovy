import org.bbva.sharedLibraries.Utilities

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def utils = new Utilities(steps)
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	// now build, based on the configuration provided
	node () {
		ws (env.JENKINS_HOME +"/jobs/"+ env.JOB_NAME.replace("/", "_") +"/workspace/") {
			stage ('Donwload Sources') {
				utils.downLoadSources(this, config.GIT_REPO_URL, config.GIT_CREDENTIALS_ID)
			}
			
			stage ('Validate release') {
				//utils.validateRelease(this, config.UUAA)
				sh "echo Validate Release"
			}
			
			parallel package: {
				stage ('Package'){
			
						
								sh "echo Packaging "
							//	sh "/usr/local/pd/ant_1.8.4/apache-ant-1.8.4/bin/ant -buildfile RDR/custom/workstation gen-clean meta-clean build-all ear -DconfigGS=1"
							//	sh "${RUTA_SCRIPT_CI}/RDR/createZipandMove.sh"
			
				}
				
				stage ('Deploy release to artifactory'){
				    if (config.DEPLOYMENT_TASK == "SI" || config.AUTO_DEPLOY == "SI"){
				       def JOB_RDR = env.JOB_NAME.replace("/", "_")+"_RDR"
					try {
						RUTA_BINARIO=env.WORKSPACE + "/" + config.UUAA +"/RDR"
						sh "mkdir -p ${config.UUAA}/RDR/online/multipais/multicanal/vrs/7.0"
						sh "mv ${env.WORKSPACE}/online/multipais/multicanal/vrs/7.0/Workstation-configGS.ear ${env.WORKSPACE}/${config.UUAA}/RDR/online/multipais/multicanal/vrs/7.0/Workstation-configGS.ear"						
					
						utils.subidaArtifactory(this, config.UUAA, RUTA_BINARIO, config.TIPO_PASE, JOB_RDR, config.AUTO_DEPLOY, config.DEPLOYMENT_TASK)
						
						def JOB_GLDSRC = env.JOB_NAME.replace("/", "_")+"_GLDSRC"
						RUTA_BINARIO=env.WORKSPACE + "/" + config.UUAA +"/GLDSRC"
						sh "mkdir -p ${config.UUAA}/GLDSRC/RDR/custom/PostInstallation/"
						sh "mv ${env.WORKSPACE}/RDR/custom/PostInstallation/workstation.zip ${env.WORKSPACE}/${config.UUAA}/GLDSRC/RDR/custom/PostInstallation/workstation.zip"
						
						utils.subidaArtifactory(this, config.UUAA, RUTA_BINARIO, config.TIPO_PASE, JOB_GLDSRC, config.AUTO_DEPLOY, config.DEPLOYMENT_TASK)
					}catch (err){
						throw err
					sh "exit -1"
					}
				 }
				}
				
				stage ('Create deployment issue'){
				    if (env.BRANCH_NAME == "master" || env.BRANCH_NAME.contains("release") || env.BRANCH_NAME.contains("hotfix")) {
    				    if (config.DEPLOYMENT_TASK == "SI"){
    					   /* utils.createDeploymentIssue(this, env.JOB_NAME.replace("/", "_"), config.LAST_TAG, config.JIRA_SERVICE_USER, config.AUTO_DEPLOY, config.UUAA, config.JIRA_PROJECT_ID, config.JIRA_AFFECTED_VERSION)
    					*/}
    				}
				}
				
				stage ('Auto deploy'){
					sh "echo AutoDeploy"	
				}
			},
			qa: {
				stage ('Execute QA Analysis'){
					sh "echo SQA"	
				}
				
				stage ('Report QA Analysis'){
					if (env.BRANCH_NAME == "master" || env.BRANCH_NAME.contains("release") || env.BRANCH_NAME.contains("hotfix"))
					{/*
						if (config.DEPLOYMENT_TASK == "SI"){
							try {
								sh "echo check"
							} catch (err){
								throw err
								sh "exit -1"
							}
						}
					*/}
				}
			}
		}
	}
}