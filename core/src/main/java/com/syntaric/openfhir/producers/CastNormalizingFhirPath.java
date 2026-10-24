package com.syntaric.openfhir.producers;

import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IBase;

/**
 * {@link IFhirPath} that normalizes the type of every cast in an expression before HAPI evaluates it, so that a
 * mapping's {@code as(string)}, {@code as(String)}, {@code as(FHIR.string)} and {@code as(StringType)} all select
 * the same elements, as they all build the same element in the openEHR -> FHIR direction.
 *
 * @see OpenFhirStringUtils#normalizeFhirPathCasts(String)
 */
public class CastNormalizingFhirPath implements IFhirPath {

    private final IFhirPath delegate;

    public CastNormalizingFhirPath(final IFhirPath delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T extends IBase> List<T> evaluate(final IBase theInput, final String thePath, final Class<T> theReturnType) {
        return delegate.evaluate(theInput, OpenFhirStringUtils.normalizeFhirPathCasts(thePath), theReturnType);
    }

    @Override
    public <T extends IBase> List<T> evaluate(final IBase theInput, final IParsedExpression theParsedExpression,
                                              final Class<T> theReturnType) {
        return delegate.evaluate(theInput, theParsedExpression, theReturnType);
    }

    @Override
    public <T extends IBase> Optional<T> evaluateFirst(final IBase theInput, final String thePath,
                                                       final Class<T> theReturnType) {
        return delegate.evaluateFirst(theInput, OpenFhirStringUtils.normalizeFhirPathCasts(thePath), theReturnType);
    }

    @Override
    public <T extends IBase> Optional<T> evaluateFirst(final IBase theInput,
                                                       final IParsedExpression theParsedExpression,
                                                       final Class<T> theReturnType) {
        return delegate.evaluateFirst(theInput, theParsedExpression, theReturnType);
    }

    @Override
    public IParsedExpression parse(final String theExpression) throws Exception {
        return delegate.parse(OpenFhirStringUtils.normalizeFhirPathCasts(theExpression));
    }

    @Override
    public void setEvaluationContext(final IFhirPathEvaluationContext theEvaluationContext) {
        delegate.setEvaluationContext(theEvaluationContext);
    }
}
