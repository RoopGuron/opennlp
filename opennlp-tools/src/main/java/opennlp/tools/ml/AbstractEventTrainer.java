/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.ml;

import java.io.IOException;

import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.DataIndexerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.HashSumEventStream;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * A basic {@link EventTrainer} implementation.
 */
public abstract class AbstractEventTrainer extends AbstractTrainer implements EventTrainer {

  public static final String DATA_INDEXER_PARAM = "DataIndexer";
  public static final String DATA_INDEXER_ONE_PASS_VALUE = "OnePass";
  public static final String DATA_INDEXER_TWO_PASS_VALUE = "TwoPass";
  public static final String DATA_INDEXER_ONE_PASS_REAL_VALUE = "OnePassRealValue";

  public AbstractEventTrainer() {
  }

  public AbstractEventTrainer(TrainingParameters parameters) {
    super(parameters);
  }

  @Override
  public void validate() {
    super.validate();
  }

  /**
   * @return {@code true} if the validation of the internal configuration succeeds,
   *         {@code false} otherwise.
   * @deprecated Use {@link #validate()} instead.
   */
  @Deprecated
  @Override
  public boolean isValid() {
    return super.isValid();
  }

  public abstract boolean isSortAndMerge();

  public DataIndexer getDataIndexer(ObjectStream<Event> events) throws IOException {

    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, isSortAndMerge());
    // If the cutoff was set, don't overwrite the value.
    if (trainingParameters.getIntParameter(CUTOFF_PARAM, -1) == -1) {
      trainingParameters.put(CUTOFF_PARAM, 5);
    }
    
    DataIndexer indexer = DataIndexerFactory.getDataIndexer(trainingParameters, reportMap);
    indexer.index(events);
    return indexer;
  }

  public abstract MaxentModel doTrain(DataIndexer indexer) throws IOException;

  @Override
  public final MaxentModel train(DataIndexer indexer) throws IOException {
    validate();

    if (indexer.getOutcomeLabels().length <= 1) {
      throw new InsufficientTrainingDataException("Training data must contain more than one outcome");
    }

    MaxentModel model = doTrain(indexer);
    addToReport(AbstractTrainer.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    return model;
  }

  @Override
  public final MaxentModel train(ObjectStream<Event> events) throws IOException {
    validate();

    HashSumEventStream hses = new HashSumEventStream(events);
    DataIndexer indexer = getDataIndexer(hses);

    addToReport("Training-Eventhash", hses.calculateHashSum().toString(16));
    return train(indexer);
  }
}
