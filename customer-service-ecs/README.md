# Building a Containerized Quarkus API on AWS ECS/Fargate with CDK

In a three articles series published recently on this site ([Part 1](https://dzone.com/articles/aws-cdk-infrastructure-as-abstract-data-types),
[Part 2](https://dzone.com/articles/aws-cdk-infrastructure-as-abstract-data-types-pt-2), [Part 3](https://dzone.com/articles/aws-cdk-infrastructure-as-abstract-data-types-3)),
I've been demonstrating the power of the AWS Cloud Development Kit (CDK) in the
Infrastructure as Code (IaC) area, especially when coupled with the ubiquitous 
Java and its supersonic / subatomic cloud-native stack: Quarkus.

While focusing on the CDK fundamentals in Java, like `Stack` and `Construct`, 
together with their Quarkus implementations, this series was a bit frugal as far
as the infrastructure elements were concerned. Indeed, for the sake of the clarity
and simplification, the infrastructure used to illustrate how to use the CDK with
Java and Quarkus was inherently consensual. Hence, the idea of a new series, of
which this article is the first one, a series less concerned by CDK internals 
and more dedicated to the infrastructure itself.

This first article demonstrates how to build and deploy a modern, cloud-native 
customer management system using Quarkus, AWS CDK, and ECS/Fargate. It covers 
the complete journey from application development to infrastructure as code, 
containerization and comprehensive testing strategies. Once again, it doesn't 
emphasise on the exposed API and its possible business value, but rather on the
infrastructure elements required in order to provide the global solution in practice.

## Architecture Overview

The diagram below shows an overview of the project's architecture:

![Architecture Diagram](./architecture.png)

This presented solution implements the following architecture layers:
  - Presentation Layer : A Quarkus REST API exposing, as an example, a couple of simple customer management endpoints.
  - Application Layer : A Quarkus main application running on ECS Fargate
  - Data Layer : PostgreSQL (RDS) for persistence, Redis (ElastiCache) for caching
  - Infrastructure Layer (Iaas): The AWS CDK-managed cloud infrastructure implemented in Quarkus

Let's try now to look in more details at these layers.

### The Presentation Layer

This layer is a Quarkus REST API which exposes a couple os simple endpoints to 
CRUD customers. More than a real business API, this one is an example allowing 
to illustrate how containerized applications could be deployed and hosted in an
AWS ECS (Elastic Container Service).

In order to separate concerns, our Maven project is structured in two modules:

  - the `customer-service-ecs-api` module which implements the Quarkus REST API to be deployed and executed as a Docker image in the AWS ECS service;
  - the `customer-service-exce-cdk` module whch bootstraps the CDK and creates the required elements in order to implement the cloud infrastructure presented in the figure above.

The Presentation Layer is contained in the `customer-service-ecs-api` module. 
The exposed REST API is simple and consists in the following endpoints to CRUD 
`Customer` entities:

  - `GET /customers`: returns a response containing the list of the currently existent customers;
  - `POST /customers`: creates a new customer by persisting the entity passed in the request's body;
  - `PUT /customers/{id}`: updates the existent customer having the ID equal to the one passed as the `id` parameter. If such a customer doesn't exist then HTTP 404 is returned.
  - `GET /customers/{id}`: returns a response containing the customer having the ID equal to the one passed as the `id` parameter. If such a customer doesn't exist then HTTP 404 is returned.
  - `DELETE /customers/{id}`: deletes the customer having the ID equal to the one passed as the `id` parameter. If such a customer doesn't exist then HTTP 404 is returned.

The endpoints above are implemented in the class `CustomerResource` which is a 
CDI (Context Dependency Injection) bean, annotated with `@ApplicationScoped`. This
is a very realistic example of using CDI in AWS deployed infrastructure elements.

### The Application Layer

This layer is the "brain" of the system, the place where the actual customer 
management business logic resides, separated from how it is exposed by the 
presentation layer. In our project it is included in the module `customer-service
-ecs-api` as well and it consists in:

  - the `Customer` entity which is the domain model representing the business object;
  - the `CustomerService` class containing the core business logic to CRUD operations;
  - the caching strategies using Redis;
  - the transaction management;
  - the business rules and validation logic;

We mentioned precedently that the `CustomerResource` class, as the pilar of the
presentation layer, is a CDI bean and, as such, it injects another CDI bean, the
`CustomerService` class, which performs the effective CRUD operations on `Customer`
business objects, using Quarkus Panache. The listing below shows the `Customer`
entity:

    @Entity
    @Table(name = "customers")
    public class Customer extends PanacheEntity
    {
      @NotBlank
      public String firstName;
      @NotBlank
      public String lastName;
      @Email
      @NotBlank
      public String email;
      public String phone;
      public String address;

      public Customer(){}
      ...
    }

As you can see, the validation rules are expressed using Jakarta Validation 
constraints.

Given this very simplified representation of a customer, the `CustomerService` 
class uses the `PanacheEntity` methods to CRUD customers, as shown below:

    @ApplicationScoped
    public class CustomerService
    {
      @Inject
      RedisDataSource redisDS;

      @Transactional
      public Customer create(Customer customer)
      {
        customer.persist();
        invalidateCache("customers:all");
        return customer;
      }

      public List<Customer> findAll()
      {
        return Customer.listAll();
      }

      public Customer findById(Long id)
      {
        ValueCommands<String, Customer> cache = redisDS.value(Customer.class);
        Customer cached = cache.get("customer:" + id);
        return Optional.ofNullable(cached).orElseGet(() -> {
          Customer customer = Customer.findById(id);
          if (customer != null)
            cache.setex("customer:" + id, 300, customer);
          return customer;
        });
      }

      @Transactional
      public Customer update(Long id, Customer updates)
      {
        return Optional.ofNullable((Customer) Customer.findById(id))
          .map(customer ->
          {
            customer.updateFrom(updates);
            invalidateCache("customer:" + id);
            invalidateCache("customers:all");
            return customer;
          })
          .orElse(null);
      }

      @Transactional
      public boolean delete(Long id)
      {
        boolean deleted = Customer.deleteById(id);
        if (deleted)
        {
          invalidateCache("customer:" + id);
          invalidateCache("customers:all");
        }
        return deleted;
      }

      private void invalidateCache(String key)
      {
        redisDS.key().del(key);
      }
    }

Nothing very spectacular here, just an usual Quarkus Panache service to CRUD 
customers. As you can see, the transaction management that we mentioned previously
are implemented on the behalf of the `@Transactional` annotation provided by 
the Jakarta Transaction specification, implemented by Quarkus.

The application layer isn't directly invoked but through the API endpoints, in the
`CustomerResource` class, for example:

      ...
      @POST
      public Response create(@Valid Customer customer)
      {
        return Response.status(Response.Status.CREATED)
          .entity(customerService.create(customer)).build();
      }
      ...

The endpoint above is invoked through HTTP by a REST client and, in turn, it calls
`CustomerService`. And talking about REST clients, we also provide a MicroProfile
(MP) REST Client, which aims at facilitating the integration, by giving the API 
consumers an easy and practical way to invoke it. Look at the interface 
`CustomerClient` below:

    @RegisterRestClient(configKey = "customers-api")
    @Path("/customers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface CustomerClient
    {
      @POST
      Response createCustomer(Customer customer);
      @GET
      @Path("/{id}")
      Response getCustomer(@PathParam("id") Long id);
      @GET
      public Response getCustomers();
      @PUT
      @Path("/{id}")
      Response updateCustomer(@PathParam("id") Long id, @Valid Customer customer);
      @DELETE
      @Path("/{id}")
      Response delete(@PathParam("id") Long id);
    }

For those not yet familiar with the MP REST Client specification and its Quarkus
implementation, this interface is all you need in order to probe your API. I'll
come back later to it when we'll discuss testing.

Let's look now at the infrastructure layer.

### The Infrastructure Layer

This layer makes the object of the 2nd project's module: `customer-service-ecs-cdk`.
It consists of a Quarkus main class, named `CustomerManagementMain`, shown below:

    @QuarkusMain
    public class CustomerManagementMain
    {
      public static void main(String... args)
      {
        Quarkus.run(CustomerManagementApp.class, args);
      }
    }

This class is the entry point class that bootstraps the Quarkus CDK application.
It uses `@QuarkusMain` to define the main method and delegates to Quarkus runtime
to run the `CustomerManagementApp` class, shown below;

    @ApplicationScoped
    public class CustomerManagementApp implements QuarkusApplication
    {
      private CustomerManagementStack customerManagementStack;
      private App app;

      @Inject
      public CustomerManagementApp (App app, CustomerManagementStack customerManagementStack)
      {
        this.app = app;
        this.customerManagementStack = customerManagementStack;
      }

      @Override
      public int run(String... args) throws Exception
      {
        Tags.of(app).add("project", "Containerized Customer Management Application on ECS/Fargate");
        Tags.of(app).add("environment", "development");
        Tags.of(app).add("application", "CustomerManagementApp");
        customerManagementStack.initStack();
        app.synth();
        return 0;
      }
    }

This class is the main application class, as opposed to the Quarkus main class.
It orchestrates the CDK stack creation by:

The main application class implementing QuarkusApplication. It orchestrates the CDK stack creation by:

  - injecting the CDK App and CustomerManagementStack via CDI;
  - adding global tags to the CDK app for project identification;
  - initializing the stack infrastructure;
  - synthesizing the CloudFormation templates;

The class `CustomerManagementStack`, too long to be reproduced here, defines the
CDK stack to be deployed. This stack consists in the following AWS infrastructure:

  - a VPC (*Virtual Private Cloud*) with a public and a private subnet across multiple AZs (*Availability Zone*);
  - a NAT (*Network Address Translation*) gateway to outbound the internet access for private resources;
  - an RDS (*Relational Database Service*) with a PostfreSQL database with automated backups and secrets' management;
  - a Redis cluster using AWS ElastiCache for in-memory caching and performance optimization;
  - an ECS (*Elastic Container Service*) Fargate serverless container hosting platform;
  - an ALB (*Application Load Balancer*) for the trafic distribution and health checking;
  - a Secrets Manager for the secure credential store and rotation;
  - all the required security groups and network level access control;
  - a CloudWatch log group for monitoring;
  - the required IAM (*Identity and Access Management*) roles for the fine-grained permission management;

The Java CDK provides the familiar pattern Builder which makes easy to instantiate
complex structures and class hierarchies. The code excerpt below provides an example:

        ApplicationLoadBalancedFargateService fargateService =
          ApplicationLoadBalancedFargateService.Builder.create(this, "CustomerService")
            .cluster(cluster)
            .cpu(config.ecs().cpu())
            .memoryLimitMiB(config.ecs().memoryLimitMiB())
            .desiredCount(config.ecs().desiredCount())
            .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
              .image(ContainerImage.fromRegistry(imageName))
              .containerPort(containerPort)
              .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                .logGroup(logGroup)
                .streamPrefix(config.logging().streamPrefix())
                .build()))
              .environment(Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", 
                  "jdbc:postgresql://" + database.getInstanceEndpoint().getHostname() +
                  ":5432/" + config.database().databaseName(),
                "QUARKUS_REDIS_HOSTS", "redis://" + redis.getPrimaryEndpoint() + ":6379"
              ))
              .secrets(Map.of(
                "QUARKUS_DATASOURCE_USERNAME", 
                  Secret.fromSecretsManager(database.getSecret(), "username"),
                "QUARKUS_DATASOURCE_PASSWORD", 
                  Secret.fromSecretsManager(database.getSecret(), "password")
              ))
              .build())
            .publicLoadBalancer(true)
            .healthCheckGracePeriod(Duration.seconds(config.ecs().healthCheckGracePeriodSeconds()))
            .serviceName(config.ecs().serviceName())
            .minHealthyPercent(100)
            .build();

This code sequence uses different builders in order to instantiate a full ECS 
Fargate serverless hosting platform. Given the high number of parameters that 
this process requires, the `InfrastructureConfig` interface, here below, provides
a type-safe Quarkus `@ConfigMap`.

    @ConfigMapping(prefix = "cdk.infrastructure")
    public interface InfrastructureConfig
    {
      VpcConfig vpc();
      EcsConfig ecs();
      DatabaseConfig database();
      RedisConfig redis();
      LoggingConfig logging();
      interface VpcConfig 
      {
        @WithDefault("2")
        int maxAzs();
        @WithDefault("1")
        int natGateways();
      }
      interface EcsConfig 
      {
        @WithDefault("256")
        int cpu();
        @WithDefault("512")
        int memoryLimitMiB();
        @WithDefault("2")
        int desiredCount();
        @WithDefault("60")
        int healthCheckGracePeriodSeconds();
        @WithDefault("customer-service")
        String serviceName();
      }
      interface DatabaseConfig 
      {
        @WithDefault("BURSTABLE3")
        String instanceClass();
        @WithDefault("MICRO")
        String instanceSize();
        @WithDefault("customers")
        String databaseName();
        @WithDefault("postgres")
        String secretUsername();
        @WithDefault("false")
        boolean deletionProtection();
      }
      interface RedisConfig 
      {
        @WithDefault("cache.t3.micro")
        String nodeType();
        @WithDefault("1")
        int numNodes();
        @WithDefault("customer-cache")
        String clusterId();
        @WithDefault("Redis cache for customer service")
        String description();
      }
      interface LoggingConfig 
      {
        @WithDefault("/ecs/customer-service")
        String logGroupName();
        @WithDefault("ONE_WEEK")
        String retentionDays();
        @WithDefault("ecs")
        String streamPrefix();
      }
    }

This `@ConfigMap` defines nested configuration structures for different 
infrastructure components and `@WithDefault` annotations for default values and
provides compile-time configuration validation while organizing settings into 
logical groups like VPC, ECS, aatabase, Redis, and logging.

