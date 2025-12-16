PIPELINE=$(aws codepipeline list-pipelines --region eu-west-3 --query 'pipelines[?starts_with(name, `CiCdPipelineStack`)].name' --output text)
DEPLOY_PROJECT=$(aws codepipeline get-pipeline --name $PIPELINE --region eu-west-3 --query 'pipeline.stages[?name==`Deploy`].actions[0].configuration.ProjectName' --output text)
aws logs tail /aws/codebuild/$DEPLOY_PROJECT --since 30m --follow --region eu-west-3
