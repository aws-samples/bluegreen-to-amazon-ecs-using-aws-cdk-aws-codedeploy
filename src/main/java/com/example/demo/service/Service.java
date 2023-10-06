/* (C)2023 */
package com.example.demo.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.EcsApplication;
import software.amazon.awscdk.services.codedeploy.EcsBlueGreenDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentConfig;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.DeploymentController;
import software.amazon.awscdk.services.ecs.DeploymentControllerType;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

public class Service extends Stack {

    private static final String ECS_TASK_CPU = "1024";
    private static final String ECS_TASK_MEMORY = "2048";
    private static final Integer ECS_CONTAINER_MEMORY_RESERVATION = 256;
    private static final Integer ECS_CONTAINER_MEMORY_LIMIT = 512;
    private static final Integer ECS_TASK_CONTAINER_PORT = 8080;
    private static final Integer ECS_TASK_CONTAINER_HOST_PORT = 8080;

    ApplicationTargetGroup tgGreen = null;
    ApplicationListener listenerGreen = null;

    public Service(Construct scope, String id, IEcsDeploymentConfig deploymentConfig, StackProps props) {

        super(scope, id, props);

        // uploading the green application to the ECR
        // maven default build dir is /target. Dockerfile is moved to /target so it can find the application jar (see
        // pom.xml)
        DockerImageAsset.Builder.create(this, "GreenContainer" + id)
                .directory("./target")
                .build();

        // L3 ECS Pattern
        ApplicationLoadBalancedFargateService albService = ApplicationLoadBalancedFargateService.Builder.create(
                        this, "Service")
                .desiredCount(2)
                .serviceName(id)
                .deploymentController(DeploymentController.builder()
                        .type(DeploymentControllerType.CODE_DEPLOY)
                        .build())
                .taskDefinition(
                        createECSTask(new HashMap<String, String>(), id, createTaskRole(id), createTaskExecutionRole(id)))
                .loadBalancerName("Alb" + id)
                .listenerPort(80)
                .build();

        createGreenListener(albService, id);

        // configure AWS CodeDeploy Application and DeploymentGroup
        EcsApplication app = EcsApplication.Builder.create(this, "BlueGreenApplication")
                .applicationName(id)
                .build();

        EcsDeploymentGroup.Builder.create(this, "BlueGreenDeploymentGroup")
                .deploymentGroupName(id)
                .application(app)
                .service(albService.getService())
                .role(createCodeDeployExecutionRole(id))
                .blueGreenDeploymentConfig(EcsBlueGreenDeploymentConfig.builder()
                        .blueTargetGroup(albService.getTargetGroup())
                        .greenTargetGroup(tgGreen)
                        .listener(albService.getListener())
                        .testListener(listenerGreen)
                        .terminationWaitTime(Duration.minutes(15))
                        .build())
                .deploymentConfig(deploymentConfig)
                .build();

        CfnOutput.Builder.create(this, "VPC")
                .description("Arn of the VPC ")
                .value(albService.getCluster().getVpc().getVpcArn())
                .build();

        CfnOutput.Builder.create(this, "ECSCluster")
                .description("Name of the ECS Cluster ")
                .value(albService.getCluster().getClusterName())
                .build();

        CfnOutput.Builder.create(this, "TaskRole")
                .description("Role name of the Task being executed ")
                .value(albService.getService().getTaskDefinition().getTaskRole().getRoleName())
                .build();

        CfnOutput.Builder.create(this, "ExecutionRole")
                .description("Execution Role name of the Task being executed ")
                .value(albService
                        .getService()
                        .getTaskDefinition()
                        .getExecutionRole()
                        .getRoleName())
                .build();

        CfnOutput.Builder.create(this, "ServiceURL")
                .description("Application is acessible from this url")
                .value("http://" + albService.getLoadBalancer().getLoadBalancerDnsName())
                .build();
    }

    public FargateTaskDefinition createECSTask(
            Map<String, String> env, String serviceName, IRole taskRole, IRole executionRole) {

        FargateTaskDefinition taskDef = null;

        taskDef = FargateTaskDefinition.Builder.create(this, "EcsTaskDef" + serviceName)
                .taskRole(taskRole)
                .executionRole(executionRole)
                .cpu(Integer.parseInt(Service.ECS_TASK_CPU))
                .memoryLimitMiB(Integer.parseInt(Service.ECS_TASK_MEMORY))
                .family(serviceName)
                .build();

        taskDef.addContainer(
                "App" + serviceName,
                ContainerDefinitionOptions.builder()
                        .containerName(serviceName)
                        .memoryReservationMiB(ECS_CONTAINER_MEMORY_RESERVATION)
                        .memoryLimitMiB(ECS_CONTAINER_MEMORY_LIMIT)
                        .image(ContainerImage.fromDockerImageAsset(
                                DockerImageAsset.Builder.create(this, "BlueContainer" + serviceName)
                                        .directory(getPathDockerfile())
                                        .build()))
                        .essential(Boolean.TRUE)
                        .portMappings(Arrays.asList(PortMapping.builder()
                                .containerPort(Service.ECS_TASK_CONTAINER_PORT)
                                .hostPort(Service.ECS_TASK_CONTAINER_HOST_PORT)
                                .protocol(Protocol.TCP)
                                .build()))
                        .environment(env)
                        .build());

        return taskDef;
    }

    /**
     * The Dockerfile of the blue version of the application is inside
     * a directory relative to this classpath (./compute/runtime-bootstrap)
     * It gets loaded from /target/classes after project is built. This is the
     * default build dir for java/maven
     */
    private String getPathDockerfile() {

        String path = "./target/classes/";
        path += this.getClass()
                .getName()
                .substring(0, this.getClass().getName().lastIndexOf("."))
                .replace(".", "/");
        path += "/api-bootstrap";

        return path;
    }

    Role createTaskRole(final String id) {

        return Role.Builder.create(this, "EcsTaskRole" + id)
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com")
                        .build())
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("AWSAppMeshEnvoyAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")))
                .build();
    }

    Role createTaskExecutionRole(final String id) {

        return Role.Builder.create(this, "EcsExecutionRole" + id)
                .roleName(id)
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com")
                        .build())
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromManagedPolicyArn(
                                this,
                                "ecsTaskExecutionManagedPolicy",
                                "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")))
                .build();
    }

    private Role createCodeDeployExecutionRole(final String id) {

        return Role.Builder.create(this, "CodeDeployExecRole" + id)
                .assumedBy(ServicePrincipal.Builder.create("codedeploy.amazonaws.com")
                        .build())
                .description("CodeBuild Execution Role for " + id)
                .path("/")
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("AWSCodeBuildDeveloperAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonEC2ContainerRegistryFullAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonECS_FullAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchLogsFullAccess"),
                        ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployRoleForECS"),
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")))
                .build();
    }

    public void createGreenListener(ApplicationLoadBalancedFargateService albService, String id) {

        // create the green listener and target group
        String tgGreenName = "GreenTG" + id;
        tgGreenName = tgGreenName.length() > 32 ? tgGreenName.substring(tgGreenName.length() - 32) : tgGreenName;

        ApplicationTargetGroup tgGreen = ApplicationTargetGroup.Builder.create(this, "GreenTg" + id)
                .protocol(ApplicationProtocol.HTTP)
                .targetGroupName(tgGreenName)
                .targetType(TargetType.IP)
                .vpc(albService.getCluster().getVpc())
                .build();

        ApplicationListener listenerGreen = albService
                .getLoadBalancer()
                .addListener(
                        "GreenListener",
                        BaseApplicationListenerProps.builder()
                                .port(8080)
                                .defaultTargetGroups(Arrays.asList(tgGreen))
                                .protocol(ApplicationProtocol.HTTP)
                                .build());

        listenerGreen.addAction(
                "GreenListenerAction" + id,
                AddApplicationActionProps.builder()
                        .action(ListenerAction.forward(Arrays.asList(tgGreen)))
                        .build());

        this.tgGreen = tgGreen;
        this.listenerGreen = listenerGreen;
    }
}
