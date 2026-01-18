package com.bookstore.user.service;

import com.bookstore.user.domain.UserAddress;
import com.bookstore.user.domain.UserProfile;
import com.bookstore.user.repository.UserAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user addresses.
 */
@Service
@Transactional
public class UserAddressService {

    private static final Logger logger = LoggerFactory.getLogger(UserAddressService.class);

    private final UserAddressRepository userAddressRepository;
    private final UserProfileService userProfileService;
    private final AuditService auditService;

    @Autowired
    public UserAddressService(UserAddressRepository userAddressRepository,
                            UserProfileService userProfileService,
                            AuditService auditService) {
        this.userAddressRepository = userAddressRepository;
        this.userProfileService = userProfileService;
        this.auditService = auditService;
    }

    /**
     * Get all addresses for a user.
     */
    public List<UserAddress> getUserAddresses(UUID userId, String correlationId) {
        logger.debug("[{}] Getting addresses for user: {}", correlationId, userId);

        // Verify user exists
        userProfileService.getUserProfileByUserId(userId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userAddressRepository.findByUserId(userId);
    }

    /**
     * Get address by ID.
     */
    public Optional<UserAddress> getAddressById(UUID addressId, String correlationId) {
        logger.debug("[{}] Getting address: {}", correlationId, addressId);
        return userAddressRepository.findById(addressId);
    }

    /**
     * Create a new address for user.
     */
    public UserAddress createUserAddress(UUID userId, UserAddress address, String correlationId) {
        logger.info("[{}] Creating address for user: {}", correlationId, userId);

        // Get user profile
        UserProfile userProfile = userProfileService.getUserProfileByUserId(userId, correlationId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Validate address
        validateAddress(address);

        // If this is set as default, clear other defaults for this type
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressRepository.clearDefaultForType(userId, address.getAddressType());
        }

        // Set user
        address.setUser(userProfile);

        UserAddress savedAddress = userAddressRepository.save(address);

        auditService.logAddressCreated(userId, address.getAddressType().toString(), correlationId);

        logger.info("[{}] Created address: {} for user: {}", correlationId, savedAddress.getId(), userId);
        return savedAddress;
    }

    /**
     * Update an existing address.
     */
    public UserAddress updateUserAddress(UUID addressId, UserAddress updatedAddress, String correlationId) {
        logger.info("[{}] Updating address: {}", correlationId, addressId);

        UserAddress existingAddress = userAddressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        // Validate address
        validateAddress(updatedAddress);

        // If changing to default, clear other defaults for this type
        if (Boolean.TRUE.equals(updatedAddress.getIsDefault()) &&
            !Boolean.TRUE.equals(existingAddress.getIsDefault())) {
            userAddressRepository.clearDefaultForType(
                existingAddress.getUser().getUserId(),
                existingAddress.getAddressType());
        }

        // Update fields
        existingAddress.setAddressType(updatedAddress.getAddressType());
        existingAddress.setIsDefault(updatedAddress.getIsDefault());
        existingAddress.setFirstName(updatedAddress.getFirstName());
        existingAddress.setLastName(updatedAddress.getLastName());
        existingAddress.setCompany(updatedAddress.getCompany());
        existingAddress.setStreetAddress(updatedAddress.getStreetAddress());
        existingAddress.setApartment(updatedAddress.getApartment());
        existingAddress.setCity(updatedAddress.getCity());
        existingAddress.setState(updatedAddress.getState());
        existingAddress.setPostalCode(updatedAddress.getPostalCode());
        existingAddress.setCountry(updatedAddress.getCountry());
        existingAddress.setPhone(updatedAddress.getPhone());

        UserAddress savedAddress = userAddressRepository.save(existingAddress);

        auditService.logAddressUpdated(addressId, correlationId);

        logger.info("[{}] Updated address: {}", correlationId, addressId);
        return savedAddress;
    }

    /**
     * Delete an address.
     */
    public void deleteUserAddress(UUID addressId, String correlationId) {
        logger.info("[{}] Deleting address: {}", correlationId, addressId);

        UserAddress address = userAddressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        userAddressRepository.deleteById(addressId);

        auditService.logAddressDeleted(address.getUser().getUserId(), address.getAddressType().toString(), correlationId);

        logger.info("[{}] Deleted address: {}", correlationId, addressId);
    }

    /**
     * Set address as default for its type.
     */
    public void setDefaultAddress(UUID userId, UUID addressId, String correlationId) {
        logger.info("[{}] Setting default address: {} for user: {}", correlationId, addressId, userId);

        UserAddress address = userAddressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        // Verify address belongs to user
        if (!address.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Address does not belong to user");
        }

        // Clear other defaults for this type
        userAddressRepository.clearDefaultForType(userId, address.getAddressType());

        // Set this address as default
        userAddressRepository.setAsDefault(addressId);

        auditService.logDefaultAddressSet(userId, address.getAddressType().toString(), correlationId);

        logger.info("[{}] Set address as default: {} for user: {}", correlationId, addressId, userId);
    }

    /**
     * Get default address for user and type.
     */
    public Optional<UserAddress> getDefaultAddress(UUID userId, UserAddress.AddressType addressType, String correlationId) {
        logger.debug("[{}] Getting default {} address for user: {}", correlationId, addressType, userId);
        return userAddressRepository.findByUserIdAndAddressTypeAndIsDefault(userId, addressType, true);
    }

    /**
     * Get addresses by type for user.
     */
    public List<UserAddress> getAddressesByType(UUID userId, UserAddress.AddressType addressType, String correlationId) {
        logger.debug("[{}] Getting {} addresses for user: {}", correlationId, addressType, userId);
        return userAddressRepository.findByUserIdAndAddressType(userId, addressType);
    }

    /**
     * Get address analytics.
     */
    public AddressAnalytics getAddressAnalytics(String correlationId) {
        logger.debug("[{}] Getting address analytics", correlationId);

        List<Object[]> countryStats = userAddressRepository.countAddressesByCountry();
        List<Object[]> stateStats = userAddressRepository.countAddressesByState();

        return new AddressAnalytics(countryStats, stateStats);
    }

    /**
     * Validate address data.
     */
    private void validateAddress(UserAddress address) {
        if (address.getStreetAddress() == null || address.getStreetAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("Street address is required");
        }
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            throw new IllegalArgumentException("City is required");
        }
        if (address.getPostalCode() == null || address.getPostalCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Postal code is required");
        }
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            throw new IllegalArgumentException("Country is required");
        }
    }

    /**
     * Result classes
     */
    public static class AddressAnalytics {
        private final List<Object[]> countryDistribution;
        private final List<Object[]> stateDistribution;

        public AddressAnalytics(List<Object[]> countryDistribution, List<Object[]> stateDistribution) {
            this.countryDistribution = countryDistribution;
            this.stateDistribution = stateDistribution;
        }

        // Getters
        public List<Object[]> getCountryDistribution() { return countryDistribution; }
        public List<Object[]> getStateDistribution() { return stateDistribution; }
    }
}