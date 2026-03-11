package io.redlink.more.data.transformers.polar;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.polar.PolarDataType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public abstract class AbstractPolarTransformer {

    public abstract PolarDataType getSupportedType();

    public abstract String getObservationType();

    /**
     * Transform a single raw Polar data item (one entry from the incoming batch list)
     * into one or more {@link DataPoint}s.
     *
     * @param observations     observations active for this participant that match this transformer's type
     * @param rawDataItem      one item from the incoming Polar batch
     * @param participantStart start of the participant's study window
     * @param participantEnd   end of the participant's study window
     */
    public abstract List<DataPoint> transform(
            List<Observation> observations,
            Map<String, Object> rawDataItem,
            Instant participantStart,
            Instant participantEnd
    );
}
