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