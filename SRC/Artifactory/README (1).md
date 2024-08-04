
# RT.groovy

## Summary
DevOps provided build steps to supplement build pipelines. This file is implemented using Jenkins Shared Libraries [Extending with Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

**Important:** Usage of these tasks will help ease the integration between GitHub, Jenkins, and Artifactory for build promotions.

## Usage
The Devops library loads this file to an object called `RT`.

```
stage('stage-name') {
  Devops.RT.downloadArtifact("artifact-name","output-dir")
}
```

### Build Retention
Build artifacts generated from pre-release branches and pull requests have a retention policy. 

Pre-release build artifacts are kept for a maximum of ```30``` days or ```10``` builds, whichever comes first.

Pull request build artifacts are kept for a maximum of ```15``` days or ```3``` builds, whichever comes first.

### Available Methods
The DevOps build pipeline provides the following methods:

Method | Description 
|:---|:---
```uploadSpec(String rtRepo = JKN_ARTIFACTORY_REPO, String rtFiles = "**/*.nupkg" )``` | create/append files to be deployed to Artifactory.
```uploadArtifacts()``` | Deploys artifact(s) defined with uploadspecs to Artifactory. This method is called as part of Devops.postBuild() to ensure all artifacts are deployed to Artifactory.
```downloadBuildArtifacts(String org, String repo, String version, String outdir = "rtPackages")``` | Download artifacts from a GitRepo using GitOrg, GitRepo, and build version. 
```getBuildArtifacts(String gitOrg, String gitRepo, String version)``` | Returns a list of artifacts from a GitRepo. 
```getBuildName()``` | Calling this method without any parameters will return the Artifactory Build Info name of the current pipeline job
```getBuildName(String org, String repo, String version)``` | Returns the Artifactory Build Info name using GitOrg, GitRepo, and build version. 
```getLatestBuild(String name, String buildnumber, String status, Boolean verbose)``` | Searches for the latest build with build number (optional) and status (optional), returning build info and artifacts (name, repo, path, md5, sha256)
```getLatestPromotedBuild(String status, Boolean verbose)``` | Searches for the latest build (using the name of the from which it was called) with the given status, returning build info. 
```getBuildTag(String org, String repo, String version)``` | Returns the build tag using GitOrg, GitRepo, and build version. 
```downloadArtifact(String artifactName, String outdir = "rtPackages")``` | Download an artifact using the full artifact name 
```downloadItemArtifact(String packageName, String version, String outdir = "rtPackages", Boolean prerelease = false, Boolean verbose = false)``` | Download a nuget package using the package id and build version. 
```getItemInfo(String packageId, String version, Boolean prelease = false, Boolean verbose = false, String token = "")``` | Returns a JSON of build metadata for a build artifact. 
```getItemInfo``` | Closure method used to get build metadata for a build artifact. See example on how to use this method
```getItemBuildName(String packageId, String version)``` | Returns the Build Info Name using the package id and version 
```getItemBuildSource(String packageId, String version)``` | Returns the Build Source using the package id and version 
```getItemBuildTag(String packageId, String version)``` | Returns the Build Tag using the package id and version 
```getItemGitCommit(String packageId, String version)``` | Returns the Git Commit using the package id and version 
```setArtifactRetentionPolicy(Boolean enabled = true)``` | Enable or disable the artifact retention policy 
```downloadUnzipArtifact(String artifactName, String outdir = "rtPackages")``` | Downloads an archive file and unzips it to the specified folder
```downloadArtifactUsingRepoPath(String repoPath, String outdir = "rtPackages")``` | Download file(s) using a repository path from Artifactory
```downloadArtifactoryFolder(String repoPath, String outdir = "rtFolder")``` | Download a folder using a repository path from Artifactory. *NOTE: a large set of files may cause this method to fail
```getArtifactBuildInfo( String artifact )``` | Returns the Artifactory BuildInfo name of a build artifact
```searchAql( String body, String accepttype = "APPLICATION_JSON", String contenttype = "TEXT_PLAIN", Boolean verbose = false )``` | Execute an Artifactory search aql api call
```getBuildInfoPromotionStatus( String buildname, String buildnumber, Boolean verbose = false )``` | Returns promotion status(es) of an Artifactory buildinfo
```promote( Closure body )``` | Promotes the current or an artifactory buildinfo
```searchArtifacts( String artifact, String rtsource = artifactory.bddevops.io, Boolean verbose = false )``` | Searches for artifact by name in artifactory identifed in rtsource. Returns json string with results. [See Example](#example-searchartifacts)
```searchArtifacts( Closure body )``` | Searches for artifact by name in artifactory identifed in rtsource. Returns json string with results. [See Example](#example-searchartifacts)

### Global variables
Variables | Description
|:---|:---|
```JKN_ARTIFACTORY_NAME``` | defaults to ```"devops-artifactory"``` update if deploying to a different Artifactory Server
```JKN_ARTIFACTORY_SOURCE_NUGET``` | defaults to ```"http://artifactory.bddevops.io/artifactory/api/nuget/BD-nuget"``` update if the nuget source changes
```JKN_ARTIFACTORY_BUILD_NAME``` | The Build Info name deployed to Artifactory

## Deploying build artifacts to Artifactory
Artifactory uploadspecs are used to specify a list of files which can be uploaded to Artifactory. They are specificed in JSON format. The `uploadSpec()` method simplifies this call by only requiring the artifactory repository and an ANT style pattern file paths representing the files to be deployed to Artifactory. This method would either create the uploadspec or append the uploadspec to an existing one. DevOps has already identified the most common deployment processes and have provided methods to automatically deploy files.

Individual Artifactory repositories are created for each GitHub Organization. Multiple Artifactory repositories may exists for each GitHub Organization as they host different file types, nupkg, docker containers, generic, npm, etc. The majoirty of the package types are defined by the Devops library and will automatically upload. Files that does not conform to upload naming patterns will not upload and will require customization. 

Devops Library uploadSpec requirements:
* Artifactory Repository - specify the Artifactory repository to upload
  * Examples: `pyxis-generic | infusion-nuget | devops-docker | etc.`
* File Glob - specify the file(s) to upload, Ant style pattern of file paths that should match
  * Examples: `**/*.nupkg | **/*.zip | etc.`
* Artifactory grouping - we are grouping files so each file would be placed in its own folder for searching/cleaning purposes
  * Examples: `(*)_(buildVersion) | (*).(buildVersion) | etc.`

Artifacts are uploaded to a path in Artifactory that follows the naming convention: `<GitOrg>-<ArtifactoryRepoType>/<GitRepo>/<GitBranch>/<TargetVersion>`
<br/>In the `TargetVersion` folder would be folders representing the package id name of the file and then the file(s) itself.

Instances where the DevOps library will automatically create uploadspecs and deploy artifacts to Artifactory:
* Generic Repository
  * Devops.zipPkg
* Nuget Repository
  * Devops.nugetPack 
  * Devops.packNuspecList
  * Devops.dotnetPack
  * Devops.chocoPack
  * Devops.msbuildPack
* NPM Repository
  * Devops.npmPublish
* Docker Repository
  * Devops.Docker.pipeline
  * Devops.Docker.pipelineWithRegistry
* Helm Repository
  * Devops.Helm.pipeline

Files created using the DevOps Library will have the version appended to its file name making them as unique as possible and be easily found. Users determined to upload their own files must consider the following:
* Subsequent branches and PRs will also upload the same named file to the same location, thus overwriting the original file.
* When multiple same named files are deployed to different locations in Artifactory, searching for the latest file may return the oldest file.

## Artifactory BuildInfo
The artifactory buildinfo is the metadata associated with a build in Artifactory. The buildinfo is published to Artifactory separately from build artifacts. It links together all artifacts that were produced in the Jenkins pipeline and deployed to Artifactory. The buildinfo is required for build promotion. Build artifacts not associated with a buildinfo are cannot be considered for promotion. The buildinfo tracks the following but is not limited to:
* build artifacts
* build environment of the Jenkins pipeline
* autogenerated/custom build properties
* source code repository properties and metadata

Artifactory buildinfo can be generated for build artifacts already deployed to Artifactory, however any information regarding source control system or build infrastructure and other build specific traceability are lost. BuildInfo consists of two parts:
* Name - naming convention `<GitOrg>_<GitRepo>_<GitBranch>_<TargetVersion>`
* Number - build version `X.X.X.X`, or `X.X.X.X-pr-1234`

BuildInfos can be found at: https://artifactory.bddevops.io/ui/builds by searching for the BuildInfo Name and then selecting the BuildInfo Number.

## Build Promotion
Build configuration items are created for all builds deployed from a release branch to Artifactory:
 * git tag - a tag representing the commit of the source code used to create the build
 * Artifactory BuildInfo:
   * Artifactory Buildname - the name of the buildinfo pushed to Artifactory
   * Artifactory Buildnumber - the number of the buildinfo pushed to Artifactory
The git tag is representative of the build source code and the artifacts and documented in design control documents.
The Artifactory buildinfo is a collective identifier of all build artifacts deployed to Artifactory. It is used to tag each artifact and capable of identifying their location in Artifactory. Most importantly, it is used to promote the build in Artifactory.

### BuildInfo Statuses
Buildinfo statuses exists in Artifactory to help identify the state of the build. Builds deployed to Artifactory will not be tagged with any status. Only when the build is promoted, a status will be applied to the buildinfo.
<br/>NOTE: promotion statuses may not be available for all Artifactory repos. Please check with DevOps if the promotion request fails
<br/>Supported buildinfo statuses:
 * `release` - for release purposes
 * `sqe` - for sprint build purposes, requires another promotion to change the status to release


### How do I find the Artifactory BuildInfo for my build?
The artifactory buildinfo is created as part of the Jenkins pipeline process, pushed to Artifactory and its URL is reported at the end of a successful build. Buildinfos are available for all builds, including prerelease builds. *NOTE: Jenkins pipeline builds not using the DevOps library will not have the appropriate buildinfo pushed to Artifactory and will not be promotable.

Artifactory buildinfo can also be found in Artifactory using the build link: http://artifactory.bddevops.io/ui/builds
<br/>Buildinfo names uses the following format: `<GitOrg>_<GitRepo>_<GitBranch>_<TargetVersion>`
<br/>Clicking on a buildinfo name will bring up a list of build ids or build numbers and it's associated build status.

### How do I promote a build?
Promoting a buildinfo to the status of `release` is only supported through the use of submitting a SharePoint request: https://collaboration.carefusion.com/sites/Nintex/scm/Lists/ServiceRequest/default.aspx, using the Request Type `Promote Artifactory Build`

Promoting a buildinfo the the status of `sqe` can be done using the SharePoint request or through a Jenkins pipeline.

#### EXAMPLE: Artifactory Promotion Closure
```groovy
// Supported parameters
Devops.RT.promote {
  buildname = ""    //Optional. Artifactory Build Name. Defaults to the current buildinfo name.
  buildnumber = ""  //Optional. Artifactory Build Number. Defaults to the current buildinfo number.
  status = ""       //Optional. Defaults to sqe. Currently, any other status will cause the promotion to fail.
  requester = ""    //Optional. Defaults to the user that triggers/replays the build or the user email from the latest commit.
  recipients = ""   //Optional. Comma delimited string of emails. Defaults to the requester parameter.
  dryrun = false    //Optional. Set to true to simulate a build promotion.
  verbose = false   //Optional. Set to true to output additional output.
}

// Example Usage
Devops.RT.promote {
  buildname = "bd-devops_jenkins-utils_master_1.0.0"
  buildnumber = "1.0.0.29"
  status = "sqe"
  requester = "jane.doe@bd.com"
  recipients = "group-distribution@bd.com,user1@bd.com"
  dryrun = false
  verbose = false
}
```

#### EXAMPLE: Jenkins pipeline buildinfo promotion
```groovy
// promoting the current build
// Since the buildinfo is pushed during the Devops.postBuild() step, the promotion
//  must be made after the postbuild step otherwise the promotion will fail.
Devops.postBuild()
Devops.RT.promote {
  requester = "bd-email@bd.com"
  recipients = "distribution-list@bd.com"
}

// promoting existing builds
Devops.RT.promote {
  buildname = "bd-devops_jenkins-utils_master_1.0.0"
  buildnumber = "1.0.0.29"
  requester = "bd-email@bd.com"
}
```

#### EXAMPLE: Jenkins pipeline promoting multiple buildinfos
```groovy
// EXAMPLE 1 - Using an ArrayList
def builds = [ "buildname1,buildnumber1",
                "buildname2,buildnumber2",
                "buildname3,buildnumber3" ]

builds.each { 
  def tokens = it.tokenize(',')
  Devops.RT.promote {
    buildname = tokens[0]
    buildnumber = tokens[1]
    requester = "bd-email@bd.com"
  }
}

// EXAMPLE 2 - Using a Map
def builds = [ "buildname1": "buildnumber1", "buildname2": "buildnumber2", "buildname3": "buildnumber3" ]

builds.each { name, version ->
  Devops.RT.promote {
    buildname = name
    buildnumber = version
    requester = "bd-email@bd.com"
    status = "sqe"
  }
}
```

#### EXAMPLE: Download build artifacts
```groovy
// download build artifacts with git org, git repo, and version number
// default download location: <workspace>\rtPackages
Devops.RT.downloadBuildArtifacts("pyxis", "es-database", "4.15.0.15")

// download build artifacts with git org, git repo, and version number to folder: "artifacts"
Devops.RT.downloadBuildArtifacts("pyxis", "es-database", "4.15.0.15", "artifacts")

// download artifact using full artifact name
// default download location: <workspace>\rtPackages
Devops.RT.downloadArtifact("Pyxis.Dataz.4.15.0.218.nupkg")

// download multiple artifacts
def artifacts = [ "artifact-name1.nupkg", "artifact-name2.nupkg", "artifact-name3.nupkg" ]
Devops.RT.downloadArtifact( artifacts )

// download artifact using full artifact name to folder "artifact\\dir"
Devops.RT.downloadArtifact("Pyxis.Dataz.4.15.0.218.nupkg","artifact\\dir")

// download artifact using package id and specific version number
// default download location: <workspace>\rtPackages
Devops.RT.downloadItemArtifact("Pyxis.Dataz", "4.15.0.216")

// download artifact using package id and latest target version to folder "artifact\\dir"
Devops.RT.downloadItemArtifact("Pyxis.Dataz", "4.15.0.0", "artifact\\dir")

// download prerelease artifact using package id and latest target version to folder "artifact\\dir"
Devops.RT.downloadItemArtifact("Pyxis.Dataz", "4.15.0.*-pre-dev", "artifact\\dir", true)

// download artifact using package id and latest version
Devops.RT.downloadItemArtifact("Pyxis.Dataz", "latest")

// download artifact using downloadItemArtifact closure
Devops.RT.downloadItemArtifact {
  packageid = "Pyxis.Dataz"
  version = "latest"
  outdir = "rtPackages"
}

// download prerelease artifact using downloadItemArtifact closure
Devops.RT.downloadItemArtifact {
  packageid = "Pyxis.Dataz"
  version = "4.15.0.*-pre-dev"
  outdir = "rtPackages"
  prerelease = true
}

// download an archive file and unzip it to a folder
// archive is downloaded into folder: <workspace>\\rtDownload
// default unzipped location: <workspace>\\rtUnzipped
Devops.RT.downloadUnzipArtifact( "artifact.zip" )

// download an archive file and unzip it to folder "unzipped"
// archive is downloaded into folder: <workspace>\\rtDownload
Devops.RT.downloadUnzipArtifact( "artifact.nupkg", "unzipped" )

// download an archive using package id and version, unzip it to folder "unzipped"
// versioning supported: 
//  - "latest" 
//  - specific version: "5.0.0.27"
//  - latest targetversion: "X.X.X.0" i.e. "4.15.0.0"
Devops.RT.downloadItemArtifact {
  packageid = "Pyxis.Dataz"
  version = "4.15.0.0"
  outdir = "unzipped"
}

// download a prerelease archive using package id and version, unzip it to folder "unzipped"
Devops.RT.downloadItemArtifact {
  packageid = "Pyxis.Dataz"
  version = "4.15.0.*-pre-dev"
  outdir = "unzipped"
  prerelease = true
}

// download file(s) using the Artifactory Repository path
// default download location: <workspace>\rtPackages
Devops.RT.downloadArtifactUsingRepoPath("pyxis-nuget/es-database/5.3/5.3.0/pyxis.dataz/Pyxis.Dataz.5.3.0.134.nupkg")

// download file(s) using the Artifactory Repository path to folder "output"
Devops.RT.downloadArtifactUsingRepoPath("pyxis-nuget/es-database/5.3/5.3.0/pyxis.dataz/Pyxis.Dataz.5.3.0.134.nupkg","output")

// download multiple file(s) using the Artifactory Repository path
def artifacts = ["pyxis-nuget/es-database/5.3/5.3.0/pyxis.dataz/Pyxis.Dataz.5.3.0.134.nupkg","pyxis-generic/path/to/file.zip"]
Devops.RT.downloadArtifactUsingRepoPath( artifacts )

// download a folder using the Artifactory Repository path
// default download location: <workspace>\rtFolder
Devops.RT.downloadArtifactoryFolder("pyxis-nuget/es-database/5.3/5.3.0/pyxis.dataz")

```

#### EXAMPLE: Get a list of build artifacts
```groovy
// Exact Version
Devops.RT.getBuildArtifacts("pyxis", "es-database", "4.15.0.216")

// Latest Version
Devops.RT.getBuildArtifacts("pyxis", "es-database", "latest")

// Max Specific Version
Devops.RT.getBuildArtifacts("pyxis", "es-database", "4.15.0.0")
```

#### EXAMPLE: Get Artifactory build info metadata
```groovy
// Get the Artifactory buildinfo name with git org, git repo, and latest
Devops.RT.getBuildName("pyxis", "es-database", "latest")

// Get the Artifactory buildinfo name with git org, git repo, and version
Devops.RT.getBuildName("pyxis", "es-database", "4.15.0.216")

// Get the Artifactory buildinfo name with git org, git repo, and max specific version
Devops.RT.getBuildName("pyxis", "es-database", "4.14.0.0")

// Get the build tag with git org, git repo, and latest
Devops.RT.getBuildTag("pyxis", "es-database", "latest")

// Get the build tag with git org, git repo, and version
Devops.RT.getBuildTag("pyxis", "es-database", "4.15.0.216")

// Get the build tag with git org, git repo, and max specific version
Devops.RT.getBuildTag("pyxis", "es-database", "4.14.0.0")

// For the current pipeline, get the latest promoted build with state "release"
Devops.RT.getLatestPromotedBuild("release")

// Returns JSON of build metadata for package id and latest
Devops.RT.getItemInfo("Pyxis.Dataz", "latest")

// Returns JSON of build metadata for package id and exact version
Devops.RT.getItemInfo("Pyxis.Dataz", "4.15.0.216")

// Returns JSON of build metadata for package id and latest target version
Devops.RT.getItemInfo("Pyxis.Dataz", "4.13.0.0")

// Returns JSON of build metadata for package id and exact prerelease version
Devops.RT.getItemInfo("Pyxis.Dataz", "4.15.0.5-pr-12345", true)

// Returns JSON of build metadata for package id and latest prerelease version
Devops.RT.getItemInfo("Pyxis.Dataz", "4.15.0.*-pr-12345", true)

// uses closure (with optional token parameter) to return JSON of build metadata for package id and latest version
Devops.RT.getItemInfo{
  packageid = "plx-db-decryption-pci"
  version = "1.0.0.0"
  verbose = "true"
  token = "_"         
  }  

// Returns the Artifactory buildinfo name using the package id and version
Devops.RT.getItemBuildName("Pyxis.Dataz", "4.15.0.216")

// Returns the build source URL using the package id and version
Devops.RT.getItemBuildSource("Pyxis.Dataz", "4.15.0.216")

// Returns the git build tag using the package id and version
Devops.RT.getItemBuildTag("Pyxis.Dataz", "4.15.0.216")

// Returns the git commit of a package using the package id and version
Devops.RT.getItemGitCommit("Pyxis.Dataz", "4.15.0.216")

// Returns the Artifactory BuildInfo of an Artifact
Devops.RT.getArtifactBuildInfo("Pyxis.Dataz.4.15.0.216.nupkg")
```

#### EXAMPLE: Use an artifactory uploadspec to deploy files to Artifactory
```groovy
// Artifactory uploadspecs are used to create mappings to upload files to Artifactory

// Create an uploadSpec for nupkgs
// Uses default Artifactory grouping of (*).(ReleaseVersion)
// This ensures only nupkgs with the Release Version are uploaded
Devops.RT.uploadSpec( "pyxis-nuget", "**/*.nupkg" )

// Create an uploadSpec for npm packages
Devops.RT.uploadSpec( "pyxis-npm", "**/*.tgz", "(*)-(${Devops.getReleaseVersion()})" )

// Create a customized uploadSpec for zipfile name only using the Jenkins build number
// filename: "zip-output/testpackage-316.zip"
Devops.RT.uploadSpec( "infusion-generic", "**/*.zip", "(*)-(${Devops.getBuildNumber()})" )

// Create a customized uploadSpec for nupkg with a specific target version
// filename = "nuget-output/packageid.1.2.3.nupkg"
def targetVersion = "1.2.3"
Devops.RT.uploadSpec( "infusion-nuget", "**/*.nupkg", "(*).(${targetVersion})" )

// Create a customized uploadSpec for any file
// NOTE: this will upload every file specified
Devops.RT.uploadSpec( "infusion-generic", "buildoutput/**/*", "(*)" )
```

#### EXAMPLE: Disable the artifactory retention policy for PR or prerelease builds
```groovy
// this must be set prior to Devops.preBuild

stage('Pre-Build') {
  Devops.RT.setArtifactRetentionPolicy( false )
  Devops.preBuild(targetVersion)
}
```

#### EXAMPLE: searchArtifacts 
```groovy
// Method call: Supplying required artifact, using default value for rtsource
def responseJsonString = Devops.RT.searchArtifacts("mms-services.2.2.0.9.nupkg")
// Closure call: Supplying required artifact, using default value for rtsource
def responseJsonString = Devops.RT.searchArtifacts{
  artifact = "mms-services.2.2.0.9.nupkg"
}

// Method call: Supplying required artifact and rtsource (domain name only - as in dl.bd.com)
def responseJsonString = Devops.RT.searchArtifacts("mms-services.2.2.0.9.nupkg","dl.bd.com")
// Closure call: Supplying required artifact and rtsource (domain name only - as in dl.bd.com)
def responseJsonString = Devops.RT.searchArtifacts{
  artifact = "mms-services.2.2.0.9.nupkg"
  rtsource = "dl.bd.com"
}
```

#### EXAMPLE: Get Latest Build using Artifactory Build Info
This supports retrieving the latest build as well as specific builds.

Supported Parameters:
1) Artifactory Build Info Name 
2) Artifactory Build Info Number
3) Artifactory Build Info Status
4) Verbosity

NOTE: 
* It is recommended to use the full buildinfo name. Using a partial buildinfo name may result in retrieving the wrong build info
* Use the target version, 1.2.3, to return the latest buildinfo for that target version
* Using the full version, 1.2.3.4, will return the exact buildinfo for that version

If successful, this method returns the following:
```json
{
  "build.name": "pyxis_es-system-releases_SR1.7.2-server_1.7.2",
  "build.number": "1.7.2.171",
  "buildCreated": "2022-04-07T10:47:15.942-07:00",
  "buildStarted": "2022-04-05T11:49:11.156-07:00",
  "buildUrl": "http://jenkins-pyxis.cfnp.local:8080/job/Pyxis/job/es-system-releases/job/SR1.7.2-server/171/",  
  "build_sourcecode": "https://github-rd.carefusion.com/pyxis/es-system-releases.git",
  "devops_build_tag": "pyxis_es-system-releases_sr1.7.2-server_1.7.2.171",
  "GIT_COMMIT": "163f45f3a4f782b3346801fdb0a2efd9c2aad79e",
  "BRANCH_NAME": "SR1.7.2-server"
}
```
Using the default call with no parameters, will return the buildinfo for the CURRENT jenkins job
```groovy
// Default
Devops.RT.getLatestBuild()
```

Find the latest build metadata and artifact(s) using the BuildInfo Name // defaults to latest build by date uploaded
```groovy
_latestBuild = Devops.RT.getLatestBuild("bd-infusion_device-alaris-embsw_12.3.1-post-dev_12.3.1")
def _latestBuildJson = steps.readJSON text: _latestBuild
if (_latestBuildJson){
  steps.echo "build.name=" + _latestBuildJson.'build.name' + ", build.number=" + _latestBuildJson.'build.number' + ", devops_build_tag=" + _latestBuildJson.devops_build_tag
  _artList = _latestBuildJson.artifacts
  _artList.each {
    steps.echo "name=" + it.name + ", repo=" + it.repo + ", path=" + it.path + ", md5=" + it.md5 + ",sha256=" + it.sha256
  }
}
```
RETURN:
```json
{
  "build.name": "bd-infusion_device-alaris-embsw_12.3.1-post-dev_12.3.1",
  "build.number": "12.3.1-post-dev.61",
  "devops_build_tag": ,     // Empty in this instance 
  ...
  "artifacts": [
        {
            "name": "BD.Medley.Embedded_12.3.1-post-dev.61.zip",
            "repo": "infusion-generic",
            "path": "device-alaris-embsw/12.3.1-post-dev/12.3.1/BD.Medley.Embedded",
            "md5": "9ba946ceb236f21d3a38996f0c0b9dae",
            "sha256": "1874954f2f8dc11153751450aa670188c8be63539d6f567dd86b5687c134323e"
        },
        {
            "name": "buildresults-device-alaris-embsw_12.3.1-post-dev.61.zip",
            "repo": "infusion-generic",
            "path": "device-alaris-embsw/12.3.1-post-dev/12.3.1/buildresults-device-alaris-embsw",
            "md5": "a9b69b5559a4ea8d950a4ca5396526fe",
            "sha256": "9ed813860bf64f6f93533b37e976d2e8d8405a8617ef541e38fc0f925818248d"
        }
    ]
}
```

Find the build metadata using the buildinfo and the full version
```groovy
Devops.RT.getLatestBuild("pyxis_es-system-releases_SR1.7.2-server_1.7.2","1.7.2.162")
RETURN:
```json
{
  "build.name": "pyxis_es-system-releases_SR1.7.2-server_1.7.2",
  "build.number": "1.7.2.162", // specific version 1.7.2.162
  ...
  "BRANCH_NAME": "SR1.7.2-server"
}
```

Find the build metadata using the buildinfo, target version and promotion status of `release`
```groovy
Devops.RT.getLatestBuild("pyxis_es-system-releases_SR1.7.2-server_1.7.2","1.7.2","release")
```
This method returns the following json content:
```json
{
  "build.name": "pyxis_es-system-releases_SR1.7.2-server_1.7.2",
  "build.number": "1.7.2.170", // promoted version
  ...
  "BRANCH_NAME": "SR1.7.2-server"
}
```