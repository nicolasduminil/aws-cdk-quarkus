package fr.simplex_software.workshop.customer_service_api.entity;

import io.quarkus.hibernate.orm.panache.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

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

  public Customer()
  {
  }

  public Customer(String firstName, String lastName, String email, String phone, String address)
  {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.phone = phone;
    this.address = address;
  }

  public void updateFrom(Customer customer)
  {
    this.firstName = customer.firstName;
    this.lastName = customer.lastName;
    this.email = customer.email;
    this.phone = customer.phone;
    this.address = customer.address;
  }
}
