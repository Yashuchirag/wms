package com.warehouse.wms.repository;

import com.warehouse.wms.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findBySku(String sku);

    List<Item> findByCategory(String category);

    List<Item> findByPriority(Item.ItemPriority priority);

    List<Item> findByTurnoverRateGreaterThan(Integer rate);
}