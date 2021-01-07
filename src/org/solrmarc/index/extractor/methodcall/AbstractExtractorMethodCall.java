package org.solrmarc.index.extractor.methodcall;

import org.marc4j.marc.Record;
import org.solrmarc.index.extractor.ExternalMethod;

public abstract class AbstractExtractorMethodCall<T> implements ExternalMethod
{
    private final String objectName;
    private final String methodName;
    private final int numParameters;
    private final boolean hasPerRecordInit;

    protected AbstractExtractorMethodCall(final String objectName, final String methodName, final boolean hasPerRecordInit, int numParameters)
    {
        this.objectName = objectName;
        this.methodName = methodName;
        this.hasPerRecordInit = hasPerRecordInit;
        this.numParameters = numParameters;
    }

    /**
     * The parameters[0] will be overridden with the record!
     *
     * @param record
     *            current record
     * @param parameters
     *            the parameters of this call.
     * @return the return value of this call.
     * @throws Exception
     *            in case of error
     */
    public T invoke(final Record record, final Object[] parameters) throws Exception
    {
        parameters[0] = record;
        if (hasPerRecordInit && !perRecordInitCalled(new Object[]{record}))
        {
            invokePerRecordInit(new Object[]{record});
        }
        return invoke(parameters);
    }

    protected abstract boolean perRecordInitCalled(Object[] record);

    protected abstract void invokePerRecordInit(Object[] record) throws Exception;

    public abstract T invoke(final Object[] parameters) throws Exception;

    public String getObjectName()
    {
        return objectName;
    }

    public abstract Class<?> getObjectClass();

    public String getMethodName()
    {
        return methodName;
    }

    public int getNumParameters()
    {
        return numParameters;
    }
}
