package com.bookstore.user.repository;

import com.bookstore.user.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserAddress entity operations.
 */
@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    /**
     * Find all addresses for a user.
     */
    List<UserAddress> findByUserId(UUID userId);

    /**
     * Find addresses by user and type.
     */
    List<UserAddress> findByUserIdAndAddressType(UUID userId, UserAddress.AddressType addressType);

    /**
     * Find default address for user and type.
     */
    Optional<UserAddress> findByUserIdAndAddressTypeAndIsDefault(UUID userId,
                                                                UserAddress.AddressType addressType,
                                                                boolean isDefault);

    /**
     * Find default shipping address for user.
     */
    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId AND a.addressType = 'SHIPPING' AND a.isDefault = true")
    Optional<UserAddress> findDefaultShippingAddress(@Param("userId") UUID userId);

    /**
     * Find default billing address for user.
     */
    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId AND a.addressType = 'BILLING' AND a.isDefault = true")
    Optional<UserAddress> findDefaultBillingAddress(@Param("userId") UUID userId);

    /**
     * Count addresses for user.
     */
    long countByUserId(UUID userId);

    /**
     * Clear default flag for all addresses of specific type for a user.
     */
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.id = :userId AND a.addressType = :addressType")
    int clearDefaultForType(@Param("userId") UUID userId, @Param("addressType") UserAddress.AddressType addressType);

    /**
     * Set address as default for its type.
     */
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = true WHERE a.id = :addressId")
    int setAsDefault(@Param("addressId") UUID addressId);

    /**
     * Delete all addresses for a user.
     */
    @Modifying
    @Query("DELETE FROM UserAddress a WHERE a.user.id = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * Find addresses by country for analytics.
     */
    @Query("SELECT a.country, COUNT(a) FROM UserAddress a GROUP BY a.country ORDER BY COUNT(a) DESC")
    List<Object[]> countAddressesByCountry();

    /**
     * Find addresses by state/province for analytics.
     */
    @Query("SELECT a.state, COUNT(a) FROM UserAddress a WHERE a.state IS NOT NULL GROUP BY a.state ORDER BY COUNT(a) DESC")
    List<Object[]> countAddressesByState();

    /**
     * Check if user has any addresses.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Check if user has default address for type.
     */
    boolean existsByUserIdAndAddressTypeAndIsDefault(UUID userId,
                                                   UserAddress.AddressType addressType,
                                                   boolean isDefault);
}