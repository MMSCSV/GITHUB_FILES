# jenkins-common/jenkins/auto-methods.groovy
Common build tasks for Jenkinsfile, used primarily for SWET functions in the Jenkins Pipeline.

## Summary
This file is used for JENKINS automation.<br />
triggerRemoteJenkins: This file can be used to trigger builds to remote Jenkins servers from the origin pipeline.<br />
getLastGoodBuild: This file is used to get the last known good build (Jenkins lastSuccessfulbuild) of the software that <br />
automation will execute on. <br />
This is highly usefull for projects that need to be spawned off of CICD pipelines for products, most notable automation. When a build passes certain tests in the build we will want to spawn an automated test to fully vet the build (regression). The original CICD pipeline will not wait for these tests but kick them off in another Jenkins server. This is also valuable to component projects that need to be build in conjunction with a product (CEDR).

This will be included in build.groovy. If build.groovy is loaded in the pipeline then this should be triggerable by Devops.TRIGGER.triggerRemoteJenkins(JSON INPUT).....(if Devops = fileLoader.load('build/build.groovy').


## Jenkins API and supported schemas
The Jenkins API can be access via `[jenkins-url]/api`, i.e. https://jenkins-infusion.bddevops.io/api <br/>
The Jenkins API schema can be access via `[jenkins-url]/api/schema`, i.e. https://jenkins-infusion.bddevops.io/api/schema <br/>


## Usage 
#### EXAMPLE: Devops.JENKINS.triggerRemoteJenkins( String jobJson )
```
// BUILD WITH PARAMETERS: Use a JSON object to execute triggerRemoteJenkins:
def _json = """
{
  "remoteJenkins": "https://jenkins-pyxis.bddevops.io",    // REMOTE JENKINS SERVER URL
  "jenkinsJob": "job/bd-pyxis/job/es-database/job/5.1.0", // JOB PATH ON REMOTE SERVER
  "parameters":
  {
    "parameter1": "value1", // build parameter 1
    "parameter2": "value2   // build parameter 2
  }
}
"""
Devops.JENKINS.triggerRemoteJenkins( _json )


// BUILD W/OUT PARAMETERS
def _json = """
{
  "remoteJenkins": "https://jenkins-swet.bddevops.io",                 // REMOTE JENKINS SERVER URL
  "jenkinsJob": "job/swet/job/es-auto17/job/system-release-1.7.3-sqe", // JOB PATH ON REMOTE SERVER
}
"""
Devops.JENKINS.triggerRemoteJenkins( _json )


// example
def _json = """
{
  "remoteJenkins": "https://jenkins-swet.bddevops.io",
  "jenkinsJob": "job/swet/job/neo-auto/job/dev",
  "parameters":
  {  
    "TESTONLY":"YES"
  }
}
"""
Devops.JENKINS.triggerRemoteJenkins( _json )
```

#### EXAMPLE: Devops.JENKINS.triggerRemoteJenkins Closure
Closures uses predetermined named keys to pass into a method. <br/>
The following parameters are supported:
 * `url`: Required. The Jenkins job URL
 * `params`: Optional. Build parameters in the format `key1=value1&key2=value2&key3=value3`
```
// Trigger a build without parameters Example 1:
Devops.JENKINS.triggerRemoteJenkins {
  url = "https://jenkins-swet.bddevops.io/job/swet/job/es-auto17/job/system-release-1.7.3-sqe"
}

// Trigger a build without parameters Example 2:
def _url = "https://jenkins-swet.bddevops.io/job/swet/job/es-auto17/job/system-release-1.7.3-sqe"
Devops.JENKINS.triggerRemoteJenkins {
  url = _url
}

// Trigger a build with parameters Example 1:
Devops.JENKINS.triggerRemoteJenkins {
  url = "https://jenkins-swet.bddevops.io/job/swet/job/es-auto17/job/system-release-1.7.3-sqe"
  params = "BuildNumber=145&TestOnly=Yes"
}

// Trigger a build with parameters Example 2:
def _url = "https://jenkins-swet.bddevops.io/job/swet/job/es-auto17/job/system-release-1.7.3-sqe"
def _params = "BuildNumber=145&TestOnly=Yes"
Devops.JENKINS.triggerRemoteJenkins {
  url = _url
  params = _params
}

```

#### EXAMPLE: Devops.JENKINS.getLastGoodBuild( String jobJson )
```
// Use a JSON object to execute and return the last good build number from a Jenkins job:
// tree id just returns the build id clean to the requestor
def _json = """
{
  "remoteJenkins": "https://jenkins-swet.bddevops.io", //REMOTE JENKINS SERVER URL
  "jenkinsJob": "job/swet/job/neo-auto/job/dev",       //JOB PATH ON REMOTE SERVER
  "parameters":
  {
    "tree": "id"
  }
}
"""
def _buildId = Devops.JENKINS.getLastGoodBuild( _json )
println "Last Good Build: " + _buildId
```


#### EXAMPLE: Devops.JENKINS.getLastSuccessfulBuild( String jenkinsJobUrl )
```
// This is the same as "getLastGoodBuild()" but instead of sending a JSON
//  a Jenkins job URL is passed as a parameter
def _jenkinsJobUrl = "https://jenkins-swet.bddevops.io/job/swet/job/neo-auto/job/dev"
def _buildId = Devops.JENKINS.getLastSuccessfulBuild( _jenkinsJobUrl )
println "Last Good Build: " + _buildId

// Example 2:
def _buildId = Devops.JENKINS.getLastSuccessfulBuild( "https://jenkins-swet.bddevops.io/job/swet/job/neo-auto/job/dev" )
println "Last Good Build: " + _buildId
```

#### EXAMPLE: Devops.JENKINS.getLastSuccessfulBuildCommit( String jenkinsJobUrl )
```
// This returns the commit of the last successful build
//  a Jenkins job URL is passed as a parameter
def _jenkinsJobUrl = "https://jenkins-swet.bddevops.io/job/swet/job/neo-auto/job/dev"
def _buildId = Devops.JENKINS.getLastSuccessfulBuildCommit( _jenkinsJobUrl )
println "Commit of the Last Good Build: " + _buildId

// Example 2:
def _buildId = Devops.JENKINS.getLastSuccessfulBuildCommit( "https://jenkins-swet.bddevops.io/job/swet/job/neo-auto/job/dev" )
println "Commit of the Last Good Build: " + _buildId

```

## updatConfig
Send the IP, path to the batch file, and product (i.e. NEO, ES) to update a file mostly used for .config files

## psexecLaunch
Used to launch batch files on remote machines that contain automation commands

## postSlackResults
Used to post Slack results in the SWET environment to other SLACK URLS

