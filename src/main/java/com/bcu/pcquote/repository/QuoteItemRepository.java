package com.bcu.pcquote.repository;

import com.bcu.pcquote.domain.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {
}
