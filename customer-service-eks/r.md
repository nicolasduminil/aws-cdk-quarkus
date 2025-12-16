1. VPC Usage
   Not mandatory - you can use the default VPC:

// Option 1: Use default VPC (simpler)
Vpc vpc = Vpc.fromLookup(this, "DefaultVpc", VpcLookupOptions.builder()
.isDefault(true)
.build());

// Option 2: Create custom VPC (more control)
Vpc vpc = Vpc.Builder.create(this, "EksVpc")
.maxAzs(2)
.build();

Copy
java
Default VPC pros: Simpler, no additional networking costs
Custom VPC pros: Better isolation, custom CIDR blocks, more subnets

2. Fargate Profile vs Alternatives
   Fargate Profile: Serverless containers (no EC2 management)

Alternatives:

// Option 1: Managed Node Groups (EC2 instances)
cluster.addNodegroupCapacity("CustomerServiceNodes",
NodegroupOptions.builder()
.instanceTypes(List.of(InstanceType.of(InstanceClass.T3, InstanceSize.MEDIUM)))
.minSize(1)
.maxSize(3)
.build());

// Option 2: Self-managed nodes
cluster.addAutoScalingGroupCapacity("CustomerServiceASG",
AutoScalingGroupCapacityOptions.builder()
.instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MEDIUM))
.minCapacity(1)
.maxCapacity(3)
.build());

Copy
java
Fargate: Serverless, pay-per-pod, no node management
Node Groups: More control, potentially cheaper for sustained workloads

3. Helm Charts - Not Mandatory
   Alternatives to Helm:

// Option 1: Raw Kubernetes manifests
cluster.addManifest("CustomerServiceDeployment", Map.of(
"apiVersion", "apps/v1",
"kind", "Deployment",
"metadata", Map.of("name", "customer-service"),
"spec", Map.of(
"replicas", 2,
"selector", Map.of("matchLabels", Map.of("app", "customer-service")),
"template", Map.of(
"metadata", Map.of("labels", Map.of("app", "customer-service")),
"spec", Map.of("containers", List.of(Map.of(
"name", "customer-service",
"image", "your-ecr-repo/customer-service:latest",
"ports", List.of(Map.of("containerPort", 8080))
)))
)
)
));

// Option 2: CDK8s (programmatic manifests)
// Option 3: Kustomize
// Option 4: Plain YAML files

Copy
java
Helm pros: Package management, templating, easier updates
Raw manifests pros: Simpler, no Helm dependency, direct control

For your project, I'd recommend:

Default VPC (simpler)

Fargate (serverless, fits your ECS comparison)

Raw manifests (simpler than Helm for this use case)

-------------------------------------------------------------------------------

# Get the exact cluster name
aws eks list-clusters --region eu-west-3

# Update kubeconfig (replace with your actual cluster name)
aws eks update-kubeconfig --region eu-west-3 --name CustomerServiceCluster497EB85D-6a37efea91704703b328f9f437f9356f

# Verify connection
kubectl get nodes

--------------------------------------------------------------
Now that all your stacks are deployed, here's a comprehensive test scenario that validates the complete EKS infrastructure with CI/CD and monitoring:

Complete Test Scenario
1. Verify EKS Cluster Access
# Get cluster name and update kubeconfig
aws eks list-clusters --region eu-west-3
aws eks update-kubeconfig --region eu-west-3 --name customer-service-cluster

# Verify cluster connectivity
kubectl get nodes
kubectl get namespaces

Copy
2. Test CI/CD Pipeline End-to-End
# Trigger pipeline by pushing a change to your GitHub repo
cd /path/to/your/github/repo
echo "# Pipeline test $(date)" >> README.md
git add README.md
git commit -m "Test CI/CD pipeline deployment"
git push origin main

# Monitor pipeline execution
aws codepipeline list-pipelines --region eu-west-3
aws codepipeline get-pipeline-state --name <pipeline-name> --region eu-west-3

Copy
bash
3. Verify Application Deployment
# Check if application deployed successfully
kubectl get deployments -n customer-service
kubectl get pods -n customer-service
kubectl get services -n customer-service

# Get service endpoint
kubectl get service customer-service -n customer-service -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

Copy
bash
4. Test Application Functionality
# Get the load balancer URL
LB_URL=$(kubectl get service customer-service -n customer-service -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# Test health endpoint
curl http://$LB_URL:8080/q/health

# Test customer API endpoints
curl -X POST http://$LB_URL:8080/customers \
-H "Content-Type: application/json" \
-d '{"firstName":"John","lastName":"Doe","email":"john.doe@example.com"}'

curl http://$LB_URL:8080/customers

Copy
bash
5. Validate Monitoring Stack
# Check CloudWatch log groups
aws logs describe-log-groups --region eu-west-3 | grep customer-service

# View recent logs
aws logs describe-log-streams --log-group-name /aws/eks/customer-service-cluster/cluster --region eu-west-3

Copy
bash
6. Test Scaling and Resilience
# Scale deployment
kubectl scale deployment customer-service --replicas=3 -n customer-service

# Verify scaling
kubectl get pods -n customer-service -w

# Test pod recovery (delete a pod)
kubectl delete pod <pod-name> -n customer-service
kubectl get pods -n customer-service

Copy
bash
7. Monitor Pipeline Logs
# Get build project logs
aws logs describe-log-groups --region eu-west-3 | grep CustomerServiceBuild
aws logs tail /aws/codebuild/CustomerServiceBuild --region eu-west-3 --follow

Copy
bash
This scenario tests:

✅ EKS cluster functionality

✅ CI/CD pipeline automation

✅ Application deployment via Kubernetes

✅ Load balancer and service discovery

✅ Monitoring and logging

✅ Auto-scaling capabilities

✅ Infrastructure resilience

The test validates your complete containerized microservices architecture on EKS with automated deployment and monitoring.

----------------------------------------------------
You need to run update-kubeconfig because:

kubectl Configuration Required
kubectl needs to know how to authenticate and where to connect to your EKS cluster. The update-kubeconfig command:

Downloads cluster credentials from AWS

Configures authentication using your AWS credentials

Sets the cluster endpoint in your local ~/.kube/config

Enables kubectl commands to work with your specific EKS cluster

Without update-kubeconfig:
kubectl get nodes
# Error: The connection to the server localhost:8080 was refused

Copy
bash
After update-kubeconfig:
aws eks update-kubeconfig --region eu-west-3 --name customer-service-cluster
kubectl get nodes
# NAME                                        STATUS   ROLES    AGE   VERSION
# fargate-ip-10-0-1-234.eu-west-3.compute... Ready    <none>   5m    v1.28.3-eks-4f4795d

Copy
bash
What it does internally:
Retrieves cluster CA certificate

Sets up AWS IAM authentication

Configures cluster API server endpoint

Merges configuration into your kubeconfig file

This is a one-time setup per cluster. Once configured, kubectl remembers the connection details until you switch contexts or the cluster changes.

Root Cause
When you use cluster.addManifest() in CDK, it creates a Lambda function that applies Kubernetes resources. AWS Lambda has API rate limits, and your deployment is hitting them because:

Multiple Kubernetes resources (Namespace, ServiceAccount, ConfigMap, Deployment, Service)

CDK makes multiple Lambda invocations to apply each resource

AWS throttles the requests when they exceed the rate limit

Solutions (Ordered by Recommendation)
Option 1: Add Delays Between Manifest Applications (Recommended)
Modify EksClusterStack to add dependencies between manifests, forcing sequential application with natural delays:

// In EksClusterStack.initStack()
KubernetesManifest namespace = cluster.addManifest("CustomerServiceNamespace", namespaceManifest);
KubernetesManifest serviceAccount = cluster.addManifest("CustomerServiceAccount", serviceAccountManifest);
serviceAccount.getNode().addDependency(namespace);

KubernetesManifest configMap = cluster.addManifest("CustomerServiceConfigMap", configMapManifest);
configMap.getNode().addDependency(serviceAccount);

KubernetesManifest deployment = cluster.addManifest("CustomerServiceDeployment", deploymentManifest);
deployment.getNode().addDependency(configMap);

KubernetesManifest service = cluster.addManifest("CustomerServiceService", serviceManifest);
service.getNode().addDependency(deployment)

🎉 SUCCESS! Your CI/CD pipeline is FULLY WORKING! 🎉
What just happened:

✅ Source stage : Pulled code from GitHub

✅ Build stage : Built Docker image with Quarkus + Jib, pushed to ECR

✅ Deploy stage :

Updated Kubernetes deployment with new image

Performed rolling update (1 pod at a time)

Waited for rollout to complete

Zero downtime deployment!

Key success messages:

deployment.apps/customer-service-api-deployment image updated
deployment "customer-service-api-deployment" successfully rolled out
Phase complete: BUILD State: SUCCEEDED

Copy
Your complete CI/CD workflow is now:

Push code to GitHub → Webhook triggers pipeline

CodeBuild builds & pushes Docker image to ECR

CodeBuild updates EKS deployment with new image

Kubernetes performs rolling update with zero downtime

New version is live!

Verify the deployment:

kubectl get pods -n customer-service
kubectl describe deployment customer-service-api-deployment -n customer-service | grep Image

Copy
bash
You should see the new image with the latest commit hash!