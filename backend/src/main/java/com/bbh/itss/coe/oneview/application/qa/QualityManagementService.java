package com.bbh.itss.coe.oneview.application.qa;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductSummary;
import com.bbh.itss.coe.oneview.application.qa.port.in.QualityApplicationView;
import com.bbh.itss.coe.oneview.application.qa.port.in.QualityManagementUseCase;
import com.bbh.itss.coe.oneview.application.qa.port.out.QualityRepositoryPort;
import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.notFound;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class QualityManagementService implements QualityManagementUseCase {

    private final QualityRepositoryPort repository;
    private final ProductRepositoryPort products;

    @Override
    @ReadOnly
    public QualityDefaults defaults() {
        return repository.loadDefaults().orElse(QualityDefaults.INITIAL);
    }

    @Override
    public QualityDefaults changeDefaults(Long version, QualityTargets targets) {
        return repository.saveDefaults(defaults().change(version, targets));
    }

    @Override
    @ReadOnly
    public List<QualityApplicationView> applications() {
        QualityDefaults defaults = defaults();
        Map<Long, QualityProfile> profiles = repository.findProfiles().stream()
                .collect(toMap(QualityProfile::productId, Function.identity()));
        return products.summaries().stream()
                .sorted(ProductSummary.BY_DEPARTMENT_AND_NAME)
                .map(product -> view(product, profiles.getOrDefault(product.id(), QualityProfile.none(product.id())),
                        defaults))
                .toList();
    }

    @Override
    @ReadOnly
    public QualityApplicationView application(long productId) {
        return view(product(productId), profile(productId), defaults());
    }

    @Override
    public QualityApplicationView change(long productId, Long version, QualityProfile changed) {
        ProductSummary product = product(productId);
        QualityProfile saved = repository.saveProfile(profile(productId).change(version, changed));
        return view(product, saved, defaults());
    }

    private QualityProfile profile(long productId) {
        return repository.loadProfile(productId).orElse(QualityProfile.none(productId));
    }

    private ProductSummary product(long productId) {
        return products.summaries().stream().filter(product -> product.id() == productId).findFirst()
                .orElseThrow(() -> notFound("Product", productId));
    }

    private static QualityApplicationView view(ProductSummary product, QualityProfile profile,
                                               QualityDefaults defaults) {
        return new QualityApplicationView(product.id(), product.code(), product.name(), product.departmentId(),
                product.departmentName(), profile, profile.effectiveTargets(defaults));
    }
}
