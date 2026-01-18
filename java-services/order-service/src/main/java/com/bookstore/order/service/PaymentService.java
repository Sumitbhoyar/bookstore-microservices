package com.bookstore.order.service;

import com.bookstore.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for handling payment processing.
 * Integrates with Payment Processor Service.
 */
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    /**
     * Process payment for order.
     */
    public void processPayment(Order order, String correlationId) {
        logger.info("[{}] Processing payment for order: {} amount: {}",
                   correlationId, order.getId(), order.getTotalAmount());

        // In a real implementation, this would call the Payment Processor Service
        // For now, simulate payment processing
        try {
            Thread.sleep(100); // Simulate API call delay

            // Simulate payment success (90% success rate for demo)
            if (Math.random() > 0.1) {
                // Payment successful
                logger.info("[{}] Payment processed successfully for order: {}", correlationId, order.getId());
            } else {
                // Payment failed
                throw new RuntimeException("Payment declined by payment processor");
            }

        } catch (Exception e) {
            logger.error("[{}] Payment processing failed for order: {} - {}",
                        correlationId, order.getId(), e.getMessage());
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    /**
     * Process refund for order.
     */
    public void processRefund(Order order, String reason, String correlationId) {
        logger.info("[{}] Processing refund for order: {} reason: {}",
                   correlationId, order.getId(), reason);

        // In a real implementation, this would call the Payment Processor Service
        // For now, simulate refund processing
        try {
            Thread.sleep(100); // Simulate API call delay

            logger.info("[{}] Refund processed successfully for order: {}", correlationId, order.getId());

        } catch (Exception e) {
            logger.error("[{}] Refund processing failed for order: {} - {}",
                        correlationId, order.getId(), e.getMessage());
            throw new RuntimeException("Refund processing failed", e);
        }
    }
}