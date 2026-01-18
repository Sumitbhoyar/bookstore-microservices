package com.bookstore.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Product Catalog Service Application
 *
 * Handles product management, inventory, pricing, and catalog operations for the bookstore system.
 * Provides REST APIs for product data that other services can consume.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ProductCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductCatalogApplication.class, args);
    }
}