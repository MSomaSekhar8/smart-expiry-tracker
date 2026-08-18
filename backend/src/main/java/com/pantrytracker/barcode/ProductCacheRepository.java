package com.pantrytracker.barcode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCacheRepository extends JpaRepository<ProductCache, String> {
}