PIPELINE=$(aws codepipeline list-pipelines --region eu-west-3 --query 'pipelines[?starts_with(name, `CiCdPipelineStack`)].name' --output text)
BUILD_PROJECT=$(aws codepipeline get-pipeline --name $PIPELINE --region eu-west-3 --query 'pipeline.stages[?name==`Build`].actions[0].configuration.ProjectName' --output text)
aws logs tail /aws/codebuild/$BUILD_PROJECT --since 30m --follow --region eu-west-3
