/* (C)2023 */
package com.example.demo.toolchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.demo.Constants;
import com.example.demo.service.Service;

import software.amazon.awscdk.Arn;
import software.amazon.awscdk.ArnComponents;
import software.amazon.awscdk.ArnFormat;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.pipelines.CodeCommitSourceOptions;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.pipelines.ShellStep;
import software.amazon.awscdk.pipelines.StageDeployment;
import software.amazon.awscdk.pipelines.Step;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codedeploy.EcsApplication;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroupAttributes;
import software.amazon.awscdk.services.codedeploy.IEcsApplication;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentGroup;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitTrigger;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;

public class Toolchain extends Stack {

    private CodePipeline pipeline = null;

    private Toolchain(Construct scope, String id, String gitRepoURL, String gitBranch, StackProps props) {

        super(scope, id, props);
        pipeline = createPipeline(gitRepoURL, gitBranch);
    }

    private CodePipeline createPipeline(String repoURL, String branch) {

        CodePipelineSource source = CodePipelineSource.codeCommit(
                Repository.fromRepositoryName(this, "CodeRepository", repoURL),
                branch,
                CodeCommitSourceOptions.builder()
                        .trigger(CodeCommitTrigger.POLL)
                        .build());

        return CodePipeline.Builder.create(this, "Pipeline-" + Constants.APP_NAME)
                .publishAssetsInParallel(Boolean.FALSE)
                .dockerEnabledForSelfMutation(Boolean.TRUE)
                .crossAccountKeys(Boolean.TRUE)
                .synth(ShellStep.Builder.create(Constants.APP_NAME + "-synth")
                        .input(source)
                        .installCommands(Arrays.asList("npm install"))
                        .commands(Arrays.asList("mvn -B clean package", "npx cdk synth"))
                        .build())
                .build();
    }

    private Toolchain addStage(
            final String stageName,
            final IEcsDeploymentConfig ecsDeploymentConfig,
            final Environment env,
            final Boolean ADD_APPROVAL) {

        // The stage
        Stage stage = Stage.Builder.create(pipeline, stageName).env(env).build();

        final String SERVICE_NAME = Constants.APP_NAME + "Service-" + stageName;

        // My stack
        new Service(
                stage,
                SERVICE_NAME,
                ecsDeploymentConfig,
                StackProps.builder()
                        .stackName(SERVICE_NAME)
                        .description(SERVICE_NAME)
                        .build());

        StageDeployment stageDeployment = pipeline.addStage(stage);

        // Configure AWS CodeDeploy
        Step configureCodeDeployStep = ShellStep.Builder.create("ConfigureBlueGreenDeploy")
                .input(pipeline.getCloudAssemblyFileSet())
                .primaryOutputDirectory("codedeploy")
                .commands(Arrays.asList(new String[] {
                    "chmod a+x ./codedeploy/codedeploy_configuration.sh",
                    "./codedeploy/codedeploy_configuration.sh",
                    String.format(
                            "./codedeploy/codedeploy_configuration.sh %s %s %s %s %s %s",
                            env.getAccount(),
                            env.getRegion(),
                            Constants.APP_NAME,
                            stageName,
                            ((Construct) pipeline).getNode().getId(),
                            SERVICE_NAME)
                }))
                .build();
       
        // At the time the toolchain is deployed, the CodeDeploy deployment action 
        // is created, but the CodeDeploy application and deployment group will not exist. 
        // They will be created when the pipeline runs and deploys the Service stack. 
        // When the pipeline deploys the Service to a remote account, it will create the 
        // CodeDeploy application and deployment group in the correct environment.
        Step deployStep = new CodeDeployStep(
            "codeDeploy"+stageName.toLowerCase(),
            configureCodeDeployStep.getPrimaryOutput(),
            referenceCodeDeployDeploymentGroup(env, SERVICE_NAME, ecsDeploymentConfig, stageName),
            stageName);

        deployStep.addStepDependency(configureCodeDeployStep);

        stageDeployment.addPost(
            configureCodeDeployStep,
            deployStep
        );

                
        return this;
    }

    /**
     * In cross-account scenarios, CDKPipelines creates a cross-account support stack that
     * will deploy the CodeDeploy Action role in the remote account. This cross-account 
     * support stack is defined in a JSON file that needs to be published to the cdk assets
     * bucket in the target account. When self-mutation feature is on (default), the  
     * UpdatePipeline stage will do a cdk deploy to deploy changes to the pipeline. In 
     * cross-account scenarios, this deployment also involves deploying/updating the 
     * cross-account support stack. The UpdatePipeline action needs to assume a 
     * file-publishing and deploy roles in the remote account, but the role associated 
     * with UpdatePipeline project in Amazon CodeBuild will not have these permissions.
     * 
     * CDKPipelines cannot grant these permissions automatically, because the information
     * about the permissions that the cross-account-support stack needs is only available after
     * the CDK app finishes synthetizing. At that point, thhe permissions that the pipeline
     * has are already locked in. Hence, for cross-account scenarios, the toolchain extends
     * the pipeline's UpdatePipeline stage permissions to include the file-publishing and
     * deploy roles.
     */
    private void grantUpdatePipelineCrossAccoutPermissions(Map<String, Environment> stageNameEnvironment) {

        if (!stageNameEnvironment.isEmpty()) {

            this.pipeline.buildPipeline();
            for (String stage : stageNameEnvironment.keySet()) {

                HashMap<String, String[]> condition = new HashMap<>();
                condition.put(
                        "iam:ResourceTag/aws-cdk:bootstrap-role",
                        new String[] {"file-publishing", "deploy"});
                pipeline.getSelfMutationProject()
                        .getRole()
                        .addToPrincipalPolicy(PolicyStatement.Builder.create()
                                .actions(Arrays.asList("sts:AssumeRole"))
                                .effect(Effect.ALLOW)
                                .resources(Arrays.asList("arn:*:iam::"
                                        + stageNameEnvironment.get(stage).getAccount() + ":role/*"))
                                .conditions(new HashMap<String, Object>() {
                                    {
                                        put("ForAnyValue:StringEquals", condition);
                                    }
                                })
                                .build());
            }
        }
    }

    private IEcsDeploymentGroup referenceCodeDeployDeploymentGroup(
            final Environment env, final String serviceName, final IEcsDeploymentConfig ecsDeploymentConfig, final String stageName) {

        IEcsApplication codeDeployApp = EcsApplication.fromEcsApplicationArn(
                this,
                Constants.APP_NAME + "EcsCodeDeployApp-"+stageName,
                Arn.format(ArnComponents.builder()
                        .arnFormat(ArnFormat.COLON_RESOURCE_NAME)
                        .partition("aws")
                        .region(env.getRegion())
                        .service("codedeploy")
                        .account(env.getAccount())
                        .resource("application")
                        .resourceName(serviceName)
                        .build()));

        IEcsDeploymentGroup deploymentGroup = EcsDeploymentGroup.fromEcsDeploymentGroupAttributes(
                this,
                Constants.APP_NAME + "-EcsCodeDeployDG-"+stageName,
                EcsDeploymentGroupAttributes.builder()
                        .deploymentGroupName(serviceName)
                        .application(codeDeployApp)
                        .deploymentConfig(ecsDeploymentConfig)
                        .build());

        return deploymentGroup;
    }

    protected Boolean isSelfMutationEnabled() {
        return pipeline.getSelfMutationEnabled();
    }

    public static final class Builder implements software.amazon.jsii.Builder<software.amazon.awscdk.Stack> {

        private Construct scope;
        private String id;
        private String gitRepoURL;
        private String gitBranch;
        private List<StageConfig> stages = new ArrayList<>();

        private software.amazon.awscdk.StackProps props;

        public void test() {}

        public Builder setGitRepo(String gitRepoURL) {
            this.gitRepoURL = gitRepoURL;
            return this;
        }

        public Builder setGitBranch(String gitBranch) {
            this.gitBranch = gitBranch;
            return this;
        }

        public Builder addStage(String name, IEcsDeploymentConfig deployConfig, Environment env) {
            this.stages.add(new StageConfig(name, deployConfig, env));
            return this;
        }

        public Toolchain build() {

            Map<String, Environment> crossAccountEnvironment = new HashMap<>();

            Toolchain pipeline = new Toolchain(
                    this.scope, this.id, this.gitRepoURL, this.gitBranch, this.props != null ? this.props : null);
            String pipelineAccount = pipeline.getAccount();

            for (StageConfig stageConfig : stages) {

                pipeline.addStage(
                        stageConfig.getStageName(),
                        stageConfig.getEcsDeployConfig(),
                        stageConfig.getEnv(),
                        stageConfig.getApproval());

                // if the pipeline is a self-mutating pipeline we need to add file-publishing
                if (pipeline.isSelfMutationEnabled()
                        && !pipelineAccount.equals(stageConfig.getEnv().getAccount())) {

                    crossAccountEnvironment.put(stageConfig.getStageName(), stageConfig.getEnv());
                }
            }
            if (!crossAccountEnvironment.isEmpty()) {
                pipeline.grantUpdatePipelineCrossAccoutPermissions(crossAccountEnvironment);
            }
            return pipeline;
        }

        private static final class StageConfig {

            String name;
            IEcsDeploymentConfig ecsDeploymentConfig;
            Environment env;
            Boolean approval = Boolean.FALSE;

            private StageConfig(String name, IEcsDeploymentConfig ecsDeploymentConfig, Environment env) {
                this.name = name;
                this.ecsDeploymentConfig = ecsDeploymentConfig;
                this.env = env;
            }

            public String getStageName() {
                return name;
            }

            public IEcsDeploymentConfig getEcsDeployConfig() {
                return ecsDeploymentConfig;
            }

            public Environment getEnv() {
                return env;
            }

            public Boolean getApproval() {
                return approval;
            }
        }

        /**
         * @return a new instance of {@link Builder}.
         * @param scope Parent of this stack, usually an `App` or a `Stage`, but could be any construct.
         * @param id The construct ID of this stack.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create(final software.constructs.Construct scope, final java.lang.String id) {
            return new Builder(scope, id);
        }
        /**
         * @return a new instance of {@link Builder}.
         * @param scope Parent of this stack, usually an `App` or a `Stage`, but could be any construct.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create(final software.constructs.Construct scope) {
            return new Builder(scope, null);
        }
        /**
         * @return a new instance of {@link Builder}.
         */
        @software.amazon.jsii.Stability(software.amazon.jsii.Stability.Level.Stable)
        public static Builder create() {
            return new Builder(null, null);
        }

        private Builder(final software.constructs.Construct scope, final java.lang.String id) {
            this.scope = scope;
            this.id = id;
        }

        public Builder stackProperties(StackProps props) {
            this.props = props;
            return this;
        }
    }
}
