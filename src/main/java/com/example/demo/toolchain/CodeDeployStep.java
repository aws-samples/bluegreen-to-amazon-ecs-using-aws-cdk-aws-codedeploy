/* (C)2023 */
package com.example.demo.toolchain;

import java.util.Arrays;

import software.amazon.awscdk.pipelines.CodePipelineActionFactoryResult;
import software.amazon.awscdk.pipelines.FileSet;
import software.amazon.awscdk.pipelines.ICodePipelineActionFactory;
import software.amazon.awscdk.pipelines.ProduceActionOptions;
import software.amazon.awscdk.pipelines.Step;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentGroup;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.IStage;
import software.amazon.awscdk.services.codepipeline.actions.CodeDeployEcsContainerImageInput;
import software.amazon.awscdk.services.codepipeline.actions.CodeDeployEcsDeployAction;

class CodeDeployStep extends Step implements ICodePipelineActionFactory {

    FileSet fileSet = null;
    IEcsDeploymentGroup deploymentGroup = null;
    String envType = null;

    public CodeDeployStep(String id, FileSet fileSet, IEcsDeploymentGroup deploymentGroup, String stageName) {
        super(id);
        this.fileSet = fileSet;
        this.deploymentGroup = deploymentGroup;
        this.envType = stageName;
    }

    @Override
    public CodePipelineActionFactoryResult produceAction(IStage stage, ProduceActionOptions options) {

        Artifact artifact = options.getArtifacts().toCodePipeline(fileSet);

        stage.addAction(CodeDeployEcsDeployAction.Builder.create()
                .actionName("Deploy")
                .appSpecTemplateInput(artifact)
                .taskDefinitionTemplateInput(artifact)
                .runOrder(options.getRunOrder())
                .containerImageInputs(Arrays.asList(CodeDeployEcsContainerImageInput.builder()
                        .input(artifact)
                        .taskDefinitionPlaceholder("IMAGE1_NAME")
                        .build()))
                .deploymentGroup(deploymentGroup)
                .variablesNamespace("deployment-" + envType)
                .build());

        return CodePipelineActionFactoryResult.builder().runOrdersConsumed(1).build();
    }
}
