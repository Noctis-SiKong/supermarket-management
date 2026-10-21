package nz.ac.aut.pdc.supermarket.fixture;

import java.util.ArrayList;
import java.util.List;

import nz.ac.aut.pdc.supermarket.stock.SubmissionGateway;

/** Capturing submission test double; never persists production data.
  */
public final class FakeSubmissionGateway implements SubmissionGateway {

    private final List<SubmissionRequest> requests = new ArrayList<>();

    @Override
    public SubmitOutcome submit(SubmissionRequest request) {
        requests.add(request);
        return new SubmitOutcome.Success(request.requestKey(), requests.size());
    }

    public List<SubmissionRequest> requests() {
        return List.copyOf(requests);
    }
}
