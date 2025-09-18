package org.solrmarc.index.extractor.methodcall;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SingleValueExtractorMethodCall extends AbstractExtractorMethodCall<String>
{
    private final Object mixin;
    private final Method method;
    private final Method perRecordInit;
    private final Method perRecordCleanup;

    public SingleValueExtractorMethodCall(final Object mixin, final Method method, final Method perRecordInit, final Method perRecordCleanup, int numParameters)
    {
        super(mixin.getClass().getName(), method.getName(), perRecordInit != null, perRecordCleanup != null, numParameters);
        this.mixin = mixin;
        this.method = method;
        this.perRecordInit = perRecordInit;
        this.perRecordCleanup = perRecordCleanup;

        if (!String.class.isAssignableFrom(this.method.getReturnType()))
        {
            throw new IllegalArgumentException(
                    "The method's return type has to be assignable to String:\nMixin class:  "
                            + mixin.getClass().getName() + "\nMixin method: " + method.toString());
        }
    }

    private SingleValueExtractorMethodCall(SingleValueExtractorMethodCall toClone)
    {
        super(toClone.mixin.getClass().getName(), toClone.method.getName(), toClone.perRecordInit != null, toClone.perRecordCleanup != null, toClone.getNumParameters());
        this.mixin = AbstractMethodCallFactory.createThreadLocalObjectForSpecifiedClass(toClone.mixin.getClass());
        this.method = toClone.method;
        this.perRecordInit = toClone.perRecordInit;
        this.perRecordCleanup = toClone.perRecordCleanup;
    }

    @Override
    protected boolean perRecordInitCalled(Object[] record)
    {
        return (MethodCallManager.instance().alreadyCalledForInit(this.mixin, record[0]));
    }

    @Override
    public void invokePerRecordInit(Object[] record) throws Exception
    {
        perRecordInit.invoke(mixin, record);
    }

    @Override
    public String invoke(final Object[] parameters) throws Exception
    {
        return (String) method.invoke(mixin, parameters);
    }

    @Override
    protected boolean perRecordCleanupCalled(Object[] record)
    {
        return (MethodCallManager.instance().alreadyCalledForCleanup(this.mixin, record[0]));
    }

    @Override
    public void invokePerRecordCleanup(Object[] record) throws Exception
    {
        perRecordCleanup.invoke(mixin, record);
    }

    @Override
    public boolean isThreadSafe()
    {
        if (perRecordInit != null) return false;
        if (perRecordCleanup != null) return false;
        try
        {
            Method isThreadSafe = mixin.getClass().getMethod("isThreadSafe");
            if (isThreadSafe.getReturnType() != boolean.class)
                return(false);
            boolean isSafe = (boolean) isThreadSafe.invoke(mixin);
            return(isSafe);
        }
        catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            return(false);
        }
    }

    @Override
    public Object makeThreadSafeCopy()
    {
        return new SingleValueExtractorMethodCall(this);
    }

    @Override
    public Class<?> getObjectClass()
    {
        return(mixin.getClass());
    }
}
