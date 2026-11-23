package com.bbh.itss.coe.oneview.adapter.out.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.domain.Persistable;

import java.util.Objects;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.staleVersion;

/** A row that belongs to one product and has the product's ID as its key, such as its profile in an area. */
@MappedSuperclass
public abstract class ProductOwnedEntity extends AuditedEntity implements Persistable<Long> {

    @Id
    private Long productId;

    protected ProductOwnedEntity() {
    }

    protected ProductOwnedEntity(long productId) {
        this.productId = productId;
    }

    @Override
    public Long getId() {
        return productId;
    }

    @Override
    public boolean isNew() {
        return createdAt() == null;
    }

    void requireVersion(Long expected) {
        if (!Objects.equals(isNew() ? null : version(), expected)) {
            throw staleVersion();
        }
    }
}
