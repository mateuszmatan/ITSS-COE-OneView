package com.bbh.itss.coe.oneview.application.catalog.port.out;

import com.bbh.itss.coe.oneview.domain.catalog.Product;
import com.bbh.itss.coe.oneview.domain.catalog.ProductDirectory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductRepositoryPort extends ProductDirectory {

    Optional<Product> load(long id);

    Optional<Product> findByServiceId(long serviceId);

    List<Product> findAll();

    List<Product> findByDepartmentId(long departmentId);

    List<ProductSummary> summaries();

    Map<Long, Long> servicesPerProduct();

    Product save(Product product);

    void delete(long id);
}
