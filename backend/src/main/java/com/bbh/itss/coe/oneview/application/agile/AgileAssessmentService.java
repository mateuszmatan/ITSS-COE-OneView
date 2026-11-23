package com.bbh.itss.coe.oneview.application.agile;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.agile.port.in.AgileApplicationView;
import com.bbh.itss.coe.oneview.application.agile.port.in.AgileAssessmentUseCase;
import com.bbh.itss.coe.oneview.application.agile.port.out.AgileRepositoryPort;
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.coe.oneview.application.catalog.port.out.ProductSummary;
import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.notFound;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class AgileAssessmentService implements AgileAssessmentUseCase {

    /**
     * The earliest time zone in the world. An assessment can be made on any day up to today there, so the today of a
     * browser anywhere is never refused as a day in the future.
     */
    static final ZoneOffset EARLIEST_TIME_ZONE = ZoneOffset.ofHours(14);

    private final AgileRepositoryPort repository;
    private final ProductRepositoryPort products;
    private final Clock clock;

    @Override
    @ReadOnly
    public List<AgileApplicationView> applications() {
        Map<Long, AgileProfile> profiles = repository.findProfiles().stream()
                .collect(toMap(AgileProfile::productId, Function.identity()));
        Map<Long, List<AgileAssessment>> assessments = repository.findAssessments().stream()
                .collect(groupingBy(AgileAssessment::productId));
        return products.summaries().stream()
                .sorted(ProductSummary.BY_DEPARTMENT_AND_NAME)
                .map(product -> view(product, profiles.getOrDefault(product.id(), AgileProfile.none(product.id())),
                        assessments.getOrDefault(product.id(), List.of())))
                .toList();
    }

    @Override
    @ReadOnly
    public AgileApplicationView application(long productId) {
        return view(product(productId), profile(productId), repository.findAssessments(productId));
    }

    @Override
    public AgileApplicationView changeProfile(long productId, Long version, AgileProfile changed) {
        ProductSummary product = product(productId);
        AgileProfile saved = repository.saveProfile(profile(productId).change(version, changed));
        return view(product, saved, repository.findAssessments(productId));
    }

    @Override
    public AgileAssessment recordAssessment(long productId, LocalDate assessedOn, String assessor,
                                            Map<AgileDimension, Integer> levels, String notes) {
        ProductSummary product = product(productId);
        AgileProfile profile = profile(productId);
        if (!profile.enabled()) {
            throw new IllegalStateException((profile.version() == null
                    ? "Set up the team of " + product.name() + " first"
                    : "Edit the team of " + product.name() + " and tick Takes part first")
                    + ": only a team that takes part is assessed.");
        }
        return repository.saveAssessment(AgileAssessment.record(productId, assessedOn, assessor, levels, notes,
                LocalDate.now(clock.withZone(EARLIEST_TIME_ZONE))));
    }

    @Override
    @ReadOnly
    public AgileAssessment assessment(long assessmentId) {
        return repository.loadAssessment(assessmentId).orElseThrow(() -> notFound("Assessment", assessmentId));
    }

    @Override
    public void deleteAssessment(long assessmentId) {
        repository.deleteAssessment(assessment(assessmentId).id());
    }

    private AgileProfile profile(long productId) {
        return repository.loadProfile(productId).orElse(AgileProfile.none(productId));
    }

    private ProductSummary product(long productId) {
        return products.summaries().stream().filter(product -> product.id() == productId).findFirst()
                .orElseThrow(() -> notFound("Product", productId));
    }

    private static AgileApplicationView view(ProductSummary product, AgileProfile profile,
                                             List<AgileAssessment> assessments) {
        return new AgileApplicationView(product.id(), product.code(), product.name(), product.departmentId(),
                product.departmentName(), profile, assessments);
    }
}
