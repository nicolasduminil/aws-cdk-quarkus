package fr.simplex_software.workshop.customer_service_eks;

import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.logs.*;

import java.util.*;

@ApplicationScoped
public class MonitoringStack extends Stack
{
  @Inject
  public MonitoringStack(App app, EksClusterStack eksStack)
  {
    super(app, "MonitoringStack");
    addDependency(eksStack);
  }

  public void initStack()
  {
    LogGroup.Builder.create(this, "EksLogGroup")
      .logGroupName("/aws/eks/customer-service")
      .retention(RetentionDays.ONE_WEEK)
      .build();

    Dashboard dashboard = Dashboard.Builder.create(this, "CustomerServiceDashboard")
      .dashboardName("customer-service-eks")
      .build();

    dashboard.addWidgets(
      GraphWidget.Builder.create()
        .title("Pod CPU Utilization")
        .left(List.of(Metric.Builder.create()
          .namespace("AWS/EKS")
          .metricName("pod_cpu_utilization")
          .build()))
        .build()
    );
  }
}
