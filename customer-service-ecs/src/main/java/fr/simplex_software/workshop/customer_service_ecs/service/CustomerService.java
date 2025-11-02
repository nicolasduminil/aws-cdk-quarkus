package fr.simplex_software.workshop.customer_service_ecs.service;

import fr.simplex_software.workshop.customer_service_ecs.entity.*;
import io.quarkus.redis.datasource.*;
import io.quarkus.redis.datasource.value.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;

import java.util.*;

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
    return Objects.requireNonNullElseGet(cached, () -> {
      Customer customer = Customer.findById(id);
      if (customer != null)
        cache.setex("customer:" + id, 300, customer);
      return customer;
    });
  }

  @Transactional
  public Customer update(Long id, Customer updates)
  {
    Customer customer = Customer.findById(id);
    if (customer != null)
    {
      customer.updateFrom(updates);
      invalidateCache("customer:" + id);
      invalidateCache("customers:all");
    }
    return customer;
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
