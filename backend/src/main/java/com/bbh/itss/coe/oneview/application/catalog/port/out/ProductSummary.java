package com.bbh.itss.coe.oneview.application.catalog.port.out;

import com.bbh.itss.coe.oneview.domain.shared.Text;
import lombok.Builder;

import java.time.Instant;
import java.util.Comparator;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;

@Builder
public record ProductSummary(long id, String code, String name, String description, String ownerTeam,
                             Long departmentId, String departmentName, Instant updatedAt) {

    /** By the name of the business service, the products of none last, then by name, in any letter case. */
    public static final Comparator<ProductSummary> BY_DEPARTMENT_AND_NAME =
            comparing(ProductSummary::departmentName, nullsLast(CASE_INSENSITIVE_ORDER))
                    .thenComparing(ProductSummary::name, CASE_INSENSITIVE_ORDER);

    public boolean matches(String search) {
        return Text.matches(search, name, code, ownerTeam, departmentName, description);
    }
}
