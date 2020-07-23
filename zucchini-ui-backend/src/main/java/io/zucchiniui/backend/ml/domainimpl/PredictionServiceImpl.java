package io.zucchiniui.backend.ml.domainimpl;

import io.zucchiniui.backend.ml.domain.Prediction;
import io.zucchiniui.backend.ml.domain.PredictionInformation;
import io.zucchiniui.backend.ml.domain.PredictionRepository;
import io.zucchiniui.backend.ml.domain.PredictionService;
import io.zucchiniui.backend.ml.rabbitmq.NewPredictionParams;
import io.zucchiniui.backend.ml.rabbitmq.ScenarioQueue;
import io.zucchiniui.backend.scenario.domain.Scenario;
import io.zucchiniui.backend.scenario.domainimpl.ScenarioRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PredictionServiceImpl implements PredictionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictionServiceImpl.class);

    private final PredictionRepository predictionRepository;

    private final ScenarioQueue scenarioQueue;

    private final ScenarioRepositoryImpl scenarioService;

    public PredictionServiceImpl(
        final PredictionRepository predictionRepository,
        final ScenarioQueue scenarioQueue,
        final ScenarioRepositoryImpl scenarioService
    ) {
        this.predictionRepository = predictionRepository;
        this.scenarioQueue = scenarioQueue;
        this.scenarioService = scenarioService;
    }

    @Override
    public void insertNewPrediction(NewPredictionParams newPredictionParams) {
        List<Prediction> predictions = newPredictionParams.getPrediction();
        PredictionInformation predictionInformation = new PredictionInformation(
            predictions,
            newPredictionParams.getClassifierId(),
            newPredictionParams.getZucchiniId(),
            newPredictionParams.getTestRunId(),
            newPredictionParams.getScenarioKey(),
            getPrediction(predictions)
        );
        predictionRepository.save(predictionInformation);
    }

    @Override
    public void makeAPrediction(String scenarioId) {
        makeAPrediction(scenarioService.getById(scenarioId));
    }

    @Override
    public void makeAPrediction(Scenario scenario) {
        if (scenario != null) {
            scenarioQueue.publishNewScenario(scenario);
        }
    }

    @Override
    public PredictionInformation getPrediction(String scenarioId) {
        Scenario scenario = scenarioService.getById(scenarioId);

        return predictionRepository
            .query(q -> q.withScenarioId(scenarioId))
            .tryToFindOne()
            .orElse(new PredictionInformation(
                new ArrayList<>(),
                "",
                scenario.getId(),
                scenario.getTestRunId(),
                scenario.getScenarioKey(),
                ""
                )
            );
    }

    @Override
    public List<PredictionInformation> getPredictionByTestRunId(String testRunId) {
        return predictionRepository.query(q -> q.withTestRunId(testRunId)).find();
    }

    @Override
    public List<PredictionInformation> getPredictionByScenarioKey(String scenarioKey) {
        return predictionRepository.query(q -> q.withScenarioKey(scenarioKey)).find();
    }

    private String getPrediction(List<Prediction> predictions) {
        String label = "";
        float accuracy = -1;
        for (Prediction prediction : predictions) {
            if (prediction.getAccuracy() > accuracy) {
                accuracy = prediction.getAccuracy();
                label = prediction.getLabel();
            }
        }
        return label;
    }
}
