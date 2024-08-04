
# jenkins-common/npm/Npm.groovy

## Summary
DevOps provided build steps to supplement build pipelines. This file is implemented using Jenkins Shared Libraries [Extending with Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/).

**Important:** Usage of these tasks will help ease the integration between GitHub, Jenkins, and Artifactory for build promotions.

## Usage
The `npm/Npm.groovy` library loads this file to an object called `Npm`.

#### Example: Execute Generic NPM command
```
stage('Install') {
  Devops.genericScriptCmd('npm install --quiet --no-progress')
}
```

#### Example: Execute NPM commands
```
node('nodeName') {
  stage('Version') {
    Devops.npmVersion()
  }
  stage('Install') {
    Devops.npmInstall()
  }
  stage('Build') {
    Devops.npmRun('build-name')
  }
  stage('Lint') {
    Devops.npmLint()
  }
  stage('Test') {
    Devops.npmTest()
    Devops.npmRun('clean')
  }
  stage('Pack') {
    Devops.npmPack()
  }
  stage('Publish') {
    Devops.npmPublis()
  }
}

```

#### Example: Using `Devops.Npm.stages`, execute npm commands. 
 * NOTE: 
   * Each command will be executed in it's own stage using the key name.
   * This will update the version in the file './package.json' (default location)

```
Devops.Npm.stages {
  install = 'npm install --quiet --no-progress'
  lint = 'npm run lint'
  test = 'npm test'
  build = 'npm run babel-transpile'
  pack = 'npm pack'
}
```

#### Example: Using `Devops.Npm.stages` and specifying the location of the package.json file.
```
Devops.Npm.stages("./src/npm-package/package.json") {
  install = 'npm install --quiet --no-progress'
  lint = 'npm run lint'
  test = 'npm test'
  build = 'npm run babel-transpile'
  pack = 'npm pack'
}
```

#### Example: Using `Devops.Npm.pipeline` with customized stages.
```
Devops.Npm.pipeline("./src/npm-package/package.json") {
  stage ('Install') {
    Devops.Npm.install('--quiet --no-progress')
  }
  stage('Lint') {
    Devops.Npm.run('lint')
  }

  ...

  stage('Pack') {
    Devops.Npm.pack()
  }
}
```

### Available Methods
The DevOps Npm pipeline provides the following methods:

Method | Description
|:---|:---|
```Devops.npmInstall(String args = "")```<br/>```Devops.Npm.install(String args = "")``` | Execute `npm install`
```Devops.npmLint(String args = "")```<br/>```Devops.Npm.lint(String args = "")``` | Execute `npm run lint`
```Devops.npmTest(String args = "")```<br/>```Devops.Npm.test(String args = "")``` | Execute `npm test`
```Devops.npmPack(String args = "")```<br/>```Devops.Npm.pack(String args = "")``` | Execute `npm pack`
```Devops.npmRun(String args = "")```<br/>```Devops.Npm.run(String args = "")``` | Execute `npm run <arg>`
```Devops.npmVersion(String packageJson = './package.json', String version = steps.GlobalVars.JKN_VERSION_FULLVERSION, String args = "")``` | Parse name key in package.json for package name and/or package scope. Increment version of package.
```Devops.npmPublish(String pathGlob = "./*.tgz", String rtRepo = "${steps.GlobalVars.JKN_GIT_ORG.toLowerCase()}-npm", String rtGrouping = "(*)-(${steps.GlobalVars.JKN_VERSION_FULLVERSION})", Boolean verbose = false)``` | Publishes NPM package to Artifactory


### Method Closures
Method | Description
|:---|:---|
```Devops.Npm.pipeline``` | Use this method update the package version, execute any additional stages and then upload the package to artifactory. See examle above.
```Devops.Npm.stages``` | Pass in a map of keys (stage names) and values (npm commands). Each key-value pair will generate its own stage and execute the command as is.

### Docker variables
Variables | Description
|:---|:---|
```JKN_NPM_SCOPE``` | NPM scope, set to the scope if found in the name of the package.json
```JKN_NPM_NAME``` | NPM package name.
```JKN_NPM_DEPENDENCIES``` | NPM package dependencies, added to Artifactory build-info for traceability purposes

