package com.bookstore.user.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Address entity for managing shipping and billing addresses.
 */
@Entity
@Table(name = "user_addresses", indexes = {
    @Index(name = "idx_user_addresses_user_id", columnList = "user_id"),
    @Index(name = "idx_user_addresses_type_default", columnList = "user_id, address_type, is_default"),
    @Index(name = "idx_user_addresses_country", columnList = "country")
})
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    private AddressType addressType;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Size(max = 100)
    @Column(name = "first_name")
    private String firstName;

    @Size(max = 100)
    @Column(name = "last_name")
    private String lastName;

    @Size(max = 255)
    private String company;

    @NotBlank
    @Size(max = 255)
    @Column(name = "street_address", nullable = false)
    private String streetAddress;

    @Size(max = 100)
    private String apartment;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String city;

    @Size(max = 100)
    private String state;

    @NotBlank
    @Size(max = 20)
    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String country = "US";

    @Size(max = 20)
    private String phone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserAddress() {}

    public UserAddress(UserProfile user, AddressType addressType, String streetAddress,
                      String city, String postalCode) {
        this.user = user;
        this.addressType = addressType;
        this.streetAddress = streetAddress;
        this.city = city;
        this.postalCode = postalCode;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserProfile getUser() {
        return user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getApartment() {
        return apartment;
    }

    public void setApartment(String apartment) {
        this.apartment = apartment;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Business methods
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        address.append(streetAddress);
        if (apartment != null && !apartment.isEmpty()) {
            address.append(", ").append(apartment);
        }
        address.append(", ").append(city);
        if (state != null && !state.isEmpty()) {
            address.append(", ").append(state);
        }
        address.append(" ").append(postalCode);
        if (!"US".equals(country)) {
            address.append(", ").append(country);
        }
        return address.toString();
    }

    public String getRecipientName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return user != null ? user.getFullName() : "Unknown";
        }
    }

    public boolean isComplete() {
        return streetAddress != null && !streetAddress.isEmpty() &&
               city != null && !city.isEmpty() &&
               postalCode != null && !postalCode.isEmpty();
    }

    public enum AddressType {
        HOME,
        WORK,
        SHIPPING,
        BILLING
    }
}