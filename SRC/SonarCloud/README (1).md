
# jenkins-common/sonarcloud/Sonarcloud.groovy

## Summary
Use this class when running SonarCloud, a cloud based static code analysis solution.

Scan results are pushed to sonarcloud, and are displayed there. Sonarcloud is integrated with github.com, and will reflect github orgs/repos structure.

## Usage
The `sonarcloud/Sonarcloud.groovy` library loads this file to an object called `Sonarcloud`: and supports ONLY sonarcloud scans to https://sonarcloud.io.

*NOTE: Sonarcloud scans will run on all branches.

## Example non-MSBuild or non-Dotnet processes
```groovy
stage('stage-name') {
  Devops.Sonarcloud.execute( sonarParameters )
}
```

## Version
Defaults to using version pointed to be ALT_SONAR_SCANNER_MSBUILD_DLL which defaults to the version installed on the build agent at this location: C:\sonar-scanner\sonar-scanner-msbuild-net5.0\SonarScanner.MSBuild.dll

### Available Methods

The `Sonarcloud` object provides the following methods:

Method | Description 
|:---|:---|
```Devops.Sonarcloud.execute()```<br/> or <br/>```Devops.Sonarcloud.execute(scan:".",parameters:"-Dsonar.test.exclusions="**/**test*",verbose:true)```| Checks if a project exists, if not, creates a project using `<GitRepo>`. Will execute scan of project files and push results to sonarcloud (can be accessed with github.com credentials). <br/><br/>Executes sonar-scanner on project. <br/>Parameters: <br/>`scan:"." default=current directory`  <br/>`parameters:""` <br/>if branch is Pullrequest, then default: <br/>`sonar.pullrequest.branch=<pullrequest.branch.name> sonar.pullrequest.base=<pullrequest.base.branch.name> sonar.pullrequest.key=<pullrequest.id>.` <br/>if branch is not default, then default: none (will scan whole branch, not just changes).<br/>If parameters are added, they must be in format `"-Dsonar.<parametername>="<value(s)>"  default=""`  <br/>`ignorefile:".sonarignore"  default=file named ".sonarignore"` Will read file and ignore files listed on each line <br/>`credential:""  default="sonar-cloud-token_sa-devops-jenkins_bdgh"` Jenkins credential which will be used to execute sonar scan. <br/>`verbose:true  default=false`  <br/>Note: by default, the following parameters are used, but can be overriden <br/>` -Dsonar.test.exclusions="**/**test*"`  <br>`-Dsonar.coverage.exclusions="**/**test*"`
```Devops.Sonarcloud.scan(name:'msbuild',process:'begin')``` <br/> ```Devops.Sonarcloud.scan(name:'msbuild',process:'end')```<br/><br/>```Devops.Sonarcloud.scan(name:'dotnet',process:'begin')``` <br/> ```Devops.Sonarcloud.scan(name:'dotnet',process:'end')```   | Execute a scan on either dotnet or msbuild compilation and push results to sonarcloud.
```Devops.Sonarcloud.getProjectMetricsCloud(String projectName = null, branch = null, String metricsCsv)``` | Returns Sonar Cloud latest generated metrics for a project as Json. ```projectName``` and ```branch``` default to current pipeline values if ommited, ```metricsCsv``` default to Sonar valid metrics https://docs.sonarsource.com/sonarcloud/digging-deeper/metric-definitions/

### Additional Parameters
Additional information on parameters can be found at: <br/>
(on cloud) https://docs.sonarsource.com/sonarcloud/advanced-setup/analysis-parameters/


Parameter Description | Single Path Example | Multi-path Example
|:---|:---|:---|
```Source file exclusions``` | `-Dsonar.exclusions="<file>"` | `-Dsonar.exclusions="<file1>,<file2>"`
```Coverage file exclusions``` | `-Dsonar.coverage.exclusions="<file>"` | `-Dsonar.coverage.exclusions="<file1>,<file2>"`
```Duplication detection exclusions``` | `-Dsonar.cpd.exclusions="<file>"` | `-Dsonar.cpd.exclusions="<file1>,<file2>"`
```Test file exclusions``` | `-Dsonar.test.exclusions="<file>"` | `-Dsonar.test.exclusions="<file1>,<file2>"`
  

### Usage
If using a non-SonarCloud instance, projects must specify a Sonar URL to post results to.

#### Set a non-SonarCloud URL
```groovy
  Devops.Sonarcloud.setUrl("https://sonarcloud-shared.uis.bdservices.io")
```

#### Default parameters: Devops.Sonarcloud.execute()
This is generally used for non-MSBuild/Dotnet projects. All required sonar parameters must be set prior to using the `execute` method.
```groovy
// listed are the default Sonar parameters passed into Devops.Sonarcloud.execute()
  -Dsonar.projectKey=${projectKey} 
  -Dsonar.sources=. 
  -Dsonar.host.url=${sonarUrl} 
  -Dsonar.login=${sonarUser} 
  -Dsonar.password=${sonarPassword} 
  -Dsonar.scm.provider=git 
  -Dsonar.test.exclusions="**/**test*" 
  -Dsonar.coverage.exclusions="**/**test*"
```
Default parameters `sonar.test.exclusions` and `sonar.coverage.exclusions` can be overwritten by passing in the same parameter.
```groovy
// passing in the same parameter will overwrite the default parameters above
// Overriding the default `sonar.test.exclusions` parameter:
Devops.Sonarcloud.execute("-Dsonar.test.exclusions=\"**/path/*\"")

// Adding multiple exclusion paths for `sonar.test.exclusions` parameter.
// Use comma-separated paths to directories containing main source files 
Devops.Sonarcloud.execute("-Dsonar.test.exclusions=\"**/path1/*,**/path2/*\"")

// Overriding the default `sonar.coverage.exclusions` parameter:
Devops.Sonarcloud.execute("-Dsonar.coverage.exclusions=\"**/path/*\"")
```

#### Example: Start a scan using Devops.Sonarcloud.scan()
```groovy
  // start a scan for a MSBUILD compilation
  Devops.Sonarcloud.scan(name: 'msbuild', process: 'begin')
  // MSBuild compilation
  // Unit test execution
  Devops.Sonarcloud.scan(name: 'msbuild', process: 'end')

  // start a scan for a DOTNET compilation
  Devops.Sonarcloud.scan(name: 'dotnet', process: 'begin')
  // DOTNET compilation
  // Unit test execution
  Devops.Sonarcloud.scan(name: 'dotnet', process: 'end')
```

#### Example: Customizing the Devops.Sonarcloud.scan method
```groovy
  // MSBuild scan and set a Sonar URL
  Devops.Sonarcloud.scan(name: 'msbuild', process: 'begin', url: "https://sonarcloud-internal.example")
  def slnFiles = "path/to/foo.sln"
  Devops.msbuild(slnFiles)
  Devops.mstest()
  Devops.Sonarcloud.scan(name: 'msbuild', process: 'end')

  // Scan Dotnet compilation and dotnet tests
  Devops.Sonarcloud.scan(name: 'dotnet', process: 'begin')
  def slnFiles = "path/to/foo.sln"
  Devops.Dotnet.build(slnFiles)
  Devops.Dotnet.test()
  Devops.Sonarcloud.scan(name: 'dotnet', process: 'end')
```

#### Example: Using Devops.Sonarcloud.execute()
```groovy
Devops.Sonarcloud.execute(parameters:"-Dsonar.test.exclusions=\"**/path/*\"")
or
Devops.Sonarcloud.execute(scan:".",parameters:"-Dsonar.test.exclusions=\"**/path/*\"",verbose:true)
```

