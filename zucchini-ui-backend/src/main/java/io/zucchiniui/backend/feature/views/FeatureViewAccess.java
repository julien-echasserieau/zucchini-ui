package io.zucchiniui.backend.feature.views;

import io.zucchiniui.backend.feature.dao.FeatureDAO;
import io.zucchiniui.backend.feature.domain.Feature;
import io.zucchiniui.backend.feature.domain.FeatureQuery;
import io.zucchiniui.backend.scenario.views.ScenarioStats;
import io.zucchiniui.backend.scenario.views.ScenarioViewAccess;
import io.zucchiniui.backend.shared.domain.TagSelection;
import io.zucchiniui.backend.support.ddd.morphia.MorphiaUtils;
import io.zucchiniui.backend.testrun.domain.TestRunQuery;
import io.zucchiniui.backend.testrun.domain.TestRunRepository;
import ma.glasnost.orika.BoundMapperFacade;
import dev.morphia.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FeatureViewAccess {

    private final FeatureDAO featureDAO;

    private final ScenarioViewAccess scenarioViewAccess;

    private final TestRunRepository testRunRepository;

    private final BoundMapperFacade<Feature, FeatureHistoryItem> featureToHistoryItemMapper;

    private final BoundMapperFacade<Feature, FeatureListItem> featureToListItemMapper;

    public FeatureViewAccess(
        final FeatureDAO featureDAO,
        final ScenarioViewAccess scenarioViewAccess,
        final TestRunRepository testRunRepository
    ) {
        this.featureDAO = featureDAO;
        this.scenarioViewAccess = scenarioViewAccess;
        this.testRunRepository = testRunRepository;

        final FeatureViewMapper mapper = new FeatureViewMapper();
        featureToHistoryItemMapper = mapper.dedicatedMapperFor(Feature.class, FeatureHistoryItem.class, false);
        featureToListItemMapper = mapper.dedicatedMapperFor(Feature.class, FeatureListItem.class, false);
    }

    public List<FeatureListItem> getFeatureListItems(
        final Consumer<FeatureQuery> preparator,
        final TagSelection tagSelection,
        final boolean withStats
    ) {
        Consumer<FeatureQuery> updatedPreparator = preparator;

        if (tagSelection.isActive()) {
            final Set<String> featureIdsForTags = scenarioViewAccess.getFeatureIdsForTags(tagSelection);
            updatedPreparator = updatedPreparator.andThen(q -> q.withIdIn(featureIdsForTags));
        }

        final Query<Feature> query = featureDAO.prepareTypedQuery(updatedPreparator)
            .project("testRunId", true)
            .project("info", true)
            .project("group", true)
            .project("status", true);

        return MorphiaUtils.streamQuery(query)
            .map(feature -> {
                final FeatureListItem item = featureToListItemMapper.map(feature);

                if (withStats || tagSelection.isActive()) {
                    final ScenarioStats stats = scenarioViewAccess.getStats(q -> q.withFeatureId(feature.getId()).withSelectedTags(tagSelection));
                    item.setStatus(stats.computeFeatureStatus());
                    item.setStats(stats);
                }
                return item;
            })
            .collect(Collectors.toList());
    }

    public List<FeatureHistoryItem> getFeatureHistory(final String featureKey) {
        return testRunRepository.query(TestRunQuery::orderByLatestFirst)
            .stream()
            .flatMap(testRun -> {
                final Feature feature = featureDAO.prepareTypedQuery(q -> q.withTestRunId(testRun.getId()).withFeatureKey(featureKey))
                    .project("status", true)
                    .get();

                if (feature == null) {
                    return Stream.empty();
                }

                final ScenarioStats stats = scenarioViewAccess.getStats(q -> q.withFeatureId(feature.getId()));

                final FeatureHistoryItem item = featureToHistoryItemMapper.map(feature);
                item.setTestRun(testRun);
                item.setStats(stats);

                return Stream.of(item);
            })
            .collect(Collectors.toList());
    }

}
