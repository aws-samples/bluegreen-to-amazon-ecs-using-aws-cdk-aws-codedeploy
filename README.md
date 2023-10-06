# Blue/Green Deployments to Amazon ECS using AWS CDK and AWS CodeDeploy

The project deploys a Java-based microservice using a CI/CD pipeline. The pipeline is implemented using the CDK Pipelines construct. The deployment uses AWS CodeDeploy Blue/Green deployment strategy. The service can be deployed in **single** or **cross-account** and **single** or **cross-Region** scenarios.

![Architecture](/imgs/architecture-general.png)

The AWS CDK application defines a top-level stack that deploys the CI/CD pipeline using AWS CodeDeploy in the specified AWS account and region. The pipeline can deploy the *Service* to a single environment or multiple environments. The **blue** version of the *Service* runtime code is deployed only once when the Service is deployed the first time in an environment. Onwards, the **green** version of the Service runtime code is deployed using AWS CodeDeploy. This Git repository contains the code of the Service and its toolchain as a self-contained solution.

[Considerations when managing ECS blue/green deployments using CloudFormation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/blue-green.html#blue-green-considerations) documentation includes the following: _"When managing Amazon ECS blue/green deployments using CloudFormation, you can't include updates to resources that initiate blue/green deployments and updates to other resources in the same stack update"_. The approach used in this project allows to update the Service infrastructure and runtime code in a single commit. To achieve that, the project leverages AWS CodeDeploy's [deployment model](https://docs.aws.amazon.com/codedeploy/latest/userguide/deployment-configurations.html#deployment-configuration-ecs) using configuration files to allow updating all resources in the same Git commit.

## Prerequisites 

The project requires the following tools:
* Amazon Corretto 8 - See installation instructions in the [user guide](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/what-is-corretto-11.html)
* Apache Maven - See [installation instructions](https://maven.apache.org/install.html)
* Docker - See [installation instructions](https://docs.docker.com/engine/install/)
* AWS CLI v2 - See [installation instructions](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
* Node.js - See [installation instructions](https://nodejs.org/en/download/package-manager/)

Although instructions in this document are specific for Linux environments, the project can also be built and executed from a Windows environment.  

## Installation

After all prerequisites are met, it usually takes around 10 minutes to follow the instructions below and deploy the AWS CDK Application for the first time. This approach supports all combinations of deploying the microservice and its toolchain to AWS accounts and Regions.

### Push the project to AWS CodeCommit

To make it easier following the example, the next steps creates an AWS CodeCommit repository and use it as source. In this example, I'm authenticating into AWS CodeCommit using [git-remote-codecommit](https://docs.aws.amazon.com/codecommit/latest/userguide/setting-up-git-remote-codecommit.html). Once you have `git-remote-codecommit` configured, you can copy and paste the following commands:

```
git clone https://github.com/aws-samples/bluegreen-to-amazon-ecs-using-aws-cdk-aws-codedeploy.git
cd bluegreen-to-amazon-ecs-using-aws-cdk-aws-codedeploy
repo_name=$(aws codecommit create-repository \
    --repository-name Demo \
    --output text \
    --query repositoryMetadata.repositoryName)
git remote set-url --push origin codecommit://${repo_name}
git add .
git commit -m "initial import"
git push 
```

## Deploy

This approach supports all combinations of deploying the Service and its toolchain to AWS accounts and Regions. Below you can find a walkthrough for two scenarios: 1/ single account and single region 2/ cross-account and cross-region. 

*Note: As of today, single account and cross-Region scenario leads to a circular dependency during `synth` phase. If you want details about this issue, please refer to [this](https://github.com/aws/aws-cdk/issues/26691) report. As an option, you can deploy cross-account and cross-Region.*

### Single Account and Single Region

If you already executed the cross-account scenario you should [cleanup](#cleanup) first.

![Architecture](/imgs/region-single.png)

Service is a component from the *Demo* application. Let's deploy the Service in single account and single Region scenario. Demo application belongs to the *Example Corp*. This can be accomplished in 5 steps.

**1. Configure environment**

Edit `src/main/java/com/example/demo/Demo.java` and update value of the following 2 properties: account number and region:
```java

    public static final String TOOLCHAIN_ACCOUNT             = "111111111111";
    public static final String TOOLCHAIN_REGION              = "us-east-1";
```

**2. Install AWS CDK locally and Synth**
```
npm install
mvn clean package
npx cdk synth
```

**3. Push configuration changes to AWS CodeCommit**

```
git add src/main/java/com/example/demo/Demo.java
git add cdk.context.json
git commit -m "initial config"
git push codecommit://${repo_name}
```

**4. One-Time Bootstrap**

 - **AWS CDK**

Deploying AWS CDK apps into an AWS environment (a combination of an AWS account and region) requires that you provision resources the AWS CDK needs to perform the deployment. Use the AWS CDK Toolkit's `cdk bootstrap` command to achieve that. See the [documentation](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping.html) for more information.

You need to run cdk bootstrap once for each deployment environment (account and Region). Below is an example of bootstrapping the account `111111111111` for both toolchain and service:
```
 npx cdk bootstrap 111111111111/us-east-1
```


 
**5. Deploy the Toolchain stack**
It will deploy the microservice in the same account and region as the toolchain:
```
npx cdk deploy DemoToolchain
```

### Cross-Acccount and Cross-Region

If you already executed the single account and single region scenario you should [clean up](#cleanup) first.

![Architecture](/imgs/region-multi.png)

Service is a component from the *Demo* application. Demo application belongs to the *Example Corp*. Let's deploy the Demo service in cross-account and cross-Region scenario. This can be accomplished in 5 steps:

**1. Configure environment:**

Edit `src/main/java/com/example/demo/Demo.java` and update the following environment variables:
```java
    //this is the account and region where the pipeline will be deployed
    public static final String TOOLCHAIN_ACCOUNT             = "111111111111";
    public static final String TOOLCHAIN_REGION              = "us-east-1";

    //this is the account and region where the component (service) will be deployed
    public static final String SERVICE_ACCOUNT          = "222222222222";
    public static final String SERVICE_REGION           = "us-east-2";
```

**2. Install AWS CDK locally and Synth**
```
npm install
mvn clean package
npx cdk synth
```

**3. Push configuration changes to AWS CodeCommit**
```
git add src/main/java/com/example/demo/Demo.java
git add cdk.context.json
git commit -m "cross-account config"
git push codecommit://${repo_name}
```

**4. One-Time Bootstrap**

 - **AWS CDK**

Deploying AWS CDK apps into an AWS environment (a combination of an AWS account and region) requires that you provision resources the AWS CDK needs to perform the deployment. Use the AWS CDK Toolkit's `cdk bootstrap` command to achieve that. See the [documentation](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping.html) for more information.

You need to run `cdk bootstrap` once for each deployment environment (account and region). For cross-account scenarios, you should add the parameter `--trust`. For more information, please see the [AWS CDK Bootstrapping documentation](https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping.html). Below is an example for service in account `222222222222` and toolchain in account `111111111111`:


```
 npx cdk bootstrap 111111111111/us-east-1
```
```
 #make sure your aws credentials are pointing to account 222222222222
 npx cdk bootstrap 222222222222/us-east-2 --trust 111111111111
```

**5. Deploy the Toolchain stack**
```
npx cdk deploy DemoToolchain
```
## **The CI/CD Pipeline**

The `Toolchain` Stack instantiates a CI/CD pipeline that builds Java based HTTP microservices. As a result, each new `Pipeline` comes with 2 stages: source and build and we configure as many deployment stages as needed. The example below shows how to create a new `Toolchain` pipeline using a builder pattern:

```java
Toolchain.Builder.create(this, "BlueGreenPipeline")
    .setGitRepo(Toolchain.CODECOMMIT_REPO)
    .setGitBranch(Toolchain.CODECOMMIT_BRANCH)
    .addStage("UAT", 
        EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES, 
        Environment.builder()
            .account(Toolchain.SERVICE_ACCOUNT)
            .region(Toolchain.SERVICE_REGION)
            .build())
    .build();
```

The `addStage` method needs to be invoked at least once to add a deployment stage. When invoked, it creates deployment stages to different AWS accounts and regions. This feature enables the implementation of different scenarios, going from single region to cross-region deployment (DR).

In the example below, there is a pipeline that has three deployment stages: `UAT` (User Acceptance Test), `Prod` and `DR` (Disaster Recovery). Each deployment stage has a name, a [deployment configuration](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-bluegreen.html) and environment information, such as, account and Region where the component should be deployed.

In detail:

```java

public class Demo {

    private static final String TOOLCHAIN_ACCOUNT         = "111111111111";
    private static final String TOOLCHAIN_REGION          = "us-east-1";
    //CodeCommit account is the same as the toolchain account
    public static final String CODECOMMIT_REPO            = "DemoService";
    public static final String CODECOMMIT_BRANCH          = "main";

    public static final String SERVICE_ACCOUNT          = "222222222222";
    public static final String SERVICE_REGION           = "us-east-1";     
    
    public static final String SERVICE_DR_ACCOUNT       = "333333333333";
    public static final String SERVICE_DR_REGION        = "us-east-2";    

    public static void main(String args[]) {

        super(scope, id, props);           
        Toolchain.Builder.create(app, Constants.APP_NAME)
            .stackProperties(StackProps.builder()
                .env(Environment.builder()
                    .account(Demo.TOOLCHAIN_ACCOUNT)
                    .region(Demo.TOOLCHAIN_REGION)
                    .build())
                .stackName(Constants.APP_NAME)
                .build())
            .setGitRepo(Demo.CODECOMMIT_REPO)
            .setGitBranch(Demo.CODECOMMIT_BRANCH)
            .addStage("UAT", 
                EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES, 
                Environment.builder()
                    .account(Demo.COMPONENT_ACCOUNT)
                    .region(Demo.COMPONENT_REGION)
                    .build())
            .addStage("Prod", 
                EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES, 
                Environment.builder()
                    .account(Demo.COMPONENT_ACCOUNT)
                    .region(Demo.COMPONENT_REGION)
                    .build())
            .addStage("DR", 
                EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES, 
                Environment.builder()
                    .account(Demo.COMPONENT_DR_ACCOUNT)
                    .region(Demo.COMPONENT_DR_REGION)
                    .build())                    
            .build();    
    }
}

```

Instances of `Toolchain` create self-mutating pipelines. This means that changes to the pipeline code that are added to the repository will be reflected to the existing pipeline next time it runs the stage `UpdatePipeline`. This is a convenience for adding stages as new environments need to be created. 

Self-Mutating pipelines promote the notion of a self-contained solution where the toolchain code, microservice infrastructure code and microservice runtime code are all maintained inside the same Git repository. For more information, please check [this](https://aws.amazon.com/pt/blogs/developer/cdk-pipelines-continuous-delivery-for-aws-cdk-applications/) blog about CDK Pipelines.

The image below shows an example pipeline created with a deployment stage named `UAT`:

<img src="/imgs/pipeline-1.png" width=100% >
<img src="/imgs/pipeline-2.png" width=100% >

## **Stacks Created**

In a minimal deployment scenario, AWS CloudFormation will display two stacks: `DemoToolchain` and `DemoService-UAT`. CDKPipelines takes care of configuring permissions to CodeDeploy, KMS and S3 (pipeline artifacts). The `DemoToolchain` stack deploys the pipeline and the `DemoService-UAT` stack deploys the component in the `UAT` environment. In this case, pipeline and `UAT` were deployed in the same account and region.

&nbsp;<img src="/imgs/stacks.png" >
## <a name="cleanup"></a> Clean up 

- Clean the S3 bucket used to store the pipeline artifacts. Bucket name should be similar to the one from the example below:
```
aws s3 rm --recursive s3://demotoolchain-pipelinedemopipelineartifactsbucket-1e9tkte03ib30
```
<!--
- Manually delete any images inside the ECR repositories in the accounts and regions where the microservice was deployed. The repository names will follow the pattern ```cdk-hnb659fds-container-assets-ACCOUNT_NUMBER-REGION```
-->
- Destroy the stacks:

```
npx cdk destroy "**"  #Includes the deployments done by the pipeline
```
If, for some reason, the destroy fails, just wait for it to finish and try again.

- Delete the repository:    

```
aws codecommit delete-repository --repository-name DemoService
```
## Testing 

Once the deployment of the blue task is complete, you can find the public URL of the application load balancer in the Outputs tab of the CloudFormation stack named `DemoService-UAT` (image below) to test the application. If you open the application before the green deployment is completed, you can see the rollout live. 

<img src="/imgs/app-url.png" width=80%>

Once acessed, thhe service displays a hello-world screen with some coloured circules representing the version of the service. At this point, refreshing the page repeatedly will show the different versions of the same service. The Blue and Green versions will appear as in the images below:

<img src="/imgs/app-blue.png" width=50% height=50%><img src="/imgs/app-green.png" width=50% height=50%>

At the same time, you can view the deployment details using the console of the CodeDeploy: for that, Sign in to the AWS Management Console and open the CodeDeploy console at https://console.aws.amazon.com/codedeploy. In the navigation pane, expand **Deploy**, and then choose **Deployments**. Click to view the details of the deployment from application **DemoService-UAT** and you will be able to see the deployment status and traffic shifting progress (image below) :

<img src="/imgs/codedeploy-deployment.png" width=70%>

## Update log location

The deployment model using AWS CodeDeploy will require changes to the properties of the task to be added into the `template-taskdef.json` file, located inside the directory `/src/main/java/com/example/demo/toolchain/codedeploy`. As an example, to update the log location for the microservice, your file will look like the following:

```json
{
   "executionRoleArn": "TASK_EXEC_ROLE",   
   "containerDefinitions": [ 
      { 
         "essential": true,
         "image": "<IMAGE1_NAME>",
         "logConfiguration": { 
	         "logDriver": "awslogs",
	         "options": { 
	            "awslogs-group" : "/ecs/Service",
	            "awslogs-region": "us-east-1",
	            "awslogs-create-group": "true",
	            "awslogs-stream-prefix": "ecs"
	         }
         },           
         "name": "APPLICATION",
         "portMappings": [ 
            { 
               "containerPort": 8080,
               "hostPort": 8080,
               "protocol": "tcp"
            }
         ]
      }
   ],
   "cpu": "256",
   "family": "fargate-task-definition",
   "memory": "512",
   "networkMode": "awsvpc",
   "requiresCompatibilities": [ 
       "FARGATE" 
    ]
}
```

## License

This project is licensed under the [MIT-0](LICENSE) license.
