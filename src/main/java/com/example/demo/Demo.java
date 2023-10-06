/* (C)2023 */
package com.example.demo;

import com.example.demo.toolchain.Toolchain;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentConfig;

/**
 * The application includes a Toolchain stack. This stack
 * creates a continuous deployment pipeline that builds and deploys
 * the Service component into one or multiple environments. It uses 
 * AWS CodePipeline, AWS CodeBuild and AWS CodeDeploy to implement a
 * Blue/Green deployment. The Service component is part of a Demo
 * application that belongs to Example.com.
 * 
 * The Blue/Green pipeline supports the single-account and
 * cross-account deployment models.
 *
 * See prerequisites (README.md) before running the application.
 */
public class Demo {

    // This is the account and region for the toolchain
    private static final String TOOLCHAIN_ACCOUNT = "742584497250";
    private static final String TOOLCHAIN_REGION = "us-east-1";
    // CodeCommit account is the same as the toolchain account
    public static final String CODECOMMIT_REPO = Constants.APP_NAME;
    public static final String CODECOMMIT_BRANCH = "main";
    // This  is the account and region for thhe service
    public static final String SERVICE_ACCOUNT = Demo.TOOLCHAIN_ACCOUNT;
    public static final String SERVICE_REGION = Demo.TOOLCHAIN_REGION;

    public static void main(String args[]) throws Exception {

        App app = new App();

        // note that the Toolchain build() method encapsulates
        // implementaton details for adding role permissions in cross-account scenarios
        Toolchain.Builder.create(app, Constants.APP_NAME+"Toolchain")
                .stackProperties(StackProps.builder()
                        .env(Environment.builder()
                                .account(Demo.TOOLCHAIN_ACCOUNT)
                                .region(Demo.TOOLCHAIN_REGION)
                                .build())
                        .build())
                .setGitRepo(Demo.CODECOMMIT_REPO)
                .setGitBranch(Demo.CODECOMMIT_BRANCH)
                .addStage(
                        "UAT",
                        EcsDeploymentConfig.CANARY_10_PERCENT_5_MINUTES,
                        Environment.builder()
                                .account(Demo.SERVICE_ACCOUNT)
                                .region("us-east-2")
                                .build())      
                .build();

        app.synth();
    }
}

