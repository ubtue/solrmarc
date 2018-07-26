package org.solrmarc.index.extractor.methodcall;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.solrmarc.index.extractor.AbstractValueExtractor;
import org.solrmarc.index.extractor.AbstractValueExtractorFactory;
import org.solrmarc.index.indexer.IndexerSpecException;
import org.solrmarc.index.indexer.ValueIndexerFactory;

public abstract class AbstractMethodCallFactory extends AbstractValueExtractorFactory
{
    protected final MethodCallManager methodCallManager;
    protected boolean haveShownKnownMethods = false;

    public AbstractMethodCallFactory()
    {
        this(MethodCallManager.instance());
    }

    public AbstractMethodCallFactory(final MethodCallManager methodCallManager)
    {
        this.methodCallManager = methodCallManager;
    }

    public void addMethodsFromClasses(Collection<Class<?>> classes)
    {
        for (final Class<?> aClass : classes)
        {
            Object instance = createThreadLocalObjectForSpecifiedClass(aClass);
            if (instance != null)
                methodCallManager.add(instance);
       }
    }

    /* 
     *  Warning here there be Dragons! 
     *  
     * This Map and the subsequent method creates object instances for potentially non-thread-safe 
     * externally loaded objects containing custom methods.  Each indexing Thread will have a separate instance of  
     * of the object to avoid the problem of ensuring the external methods are thread safe. 
     * 
     */

    private final static ThreadLocal<Map<Class<?>, Object>> threadLocalObjectMap = new ThreadLocal<Map<Class<?>, Object>>() 
        {
            @Override 
            protected Map<Class<?>, Object> initialValue() 
            {
                return new LinkedHashMap<>();
            }
        };

    static public Object createThreadLocalObjectForSpecifiedClass(Class<?> aClass)
    {
        Map<Class<?>, Object> instanceMap = threadLocalObjectMap.get();
        if (instanceMap.containsKey(aClass))
        {
            return(instanceMap.get(aClass));
        }
        Object toReturn;
        try
        {
            Constructor<?> ctor = aClass.getConstructor();
            toReturn = ctor.newInstance();
        }
        catch (NoSuchMethodException | SecurityException e)
        {
            // can't call no-args constructor, check whether class extends org.solrmarc.index.SolrIndexer
            // for backwards compatibility sake.
            Class<?> solrIndexerClass = org.solrmarc.index.SolrIndexer.class;
            if (solrIndexerClass.isAssignableFrom(aClass))
            {
                try
                {
                    Constructor<?> ctor2 = aClass.getConstructor(String.class, String[].class);
                    // the placeholder stub implementation for org.solrmarc.index.SolrIndexer 
                    // doesn't look at use the parameters that the Constructor requires, so fake values are used
                    toReturn = ctor2.newInstance("", ValueIndexerFactory.instance().getHomeDirs());
                }
                catch (NoSuchMethodException  | SecurityException e1)
                {
                    throw new RuntimeException("Cannot call constructor for legacy class derived from old SolrIndexer, you'll need to edit your source code", e);
                }
                catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e2)
                {
                    throw new RuntimeException("Cannot call constructor for legacy class derived from old SolrIndexer, you'll need to edit your source code", e);
                }
            }
            else 
            { 
                 /* dynamically loaded class that has no default constructor, but also is not a legacy class that extends SolrIndexer, 
                  * therefore don't look for custom extractor methods or custom mapping methods in the class
                  */
                toReturn = null;
            }
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }
        instanceMap.put(aClass, toReturn);
        return(toReturn);
    }

    public AbstractValueExtractor<?> createExtractor(final String solrFieldName, MethodCallContext context)
    {
        final AbstractExtractorMethodCall<?> methodCall = methodCallManager.getExtractorMethodCallForContext(context);
        if (methodCall != null)
        {
            return createExtractorForMethodCall(methodCall, context);
        }
        // most of the rest of this method is figuring out how to describe what went wrong to the user.
        else if (methodCall == null && context.getObjectName() == null)
        {
            List<AbstractExtractorMethodCall<?>> matches = methodCallManager.getLoadedExtractorMixinsMatches(null, context.getMethodName(), context.getParameterTypes().length);
            if (matches.size() > 1)
            {
                // This finds instances where a superclass and a subclass both implement the same method, and automatically selects the subclass implementation.
                // Note that if the subclass doesn't implement a particular method, it will still be listed as an available method for the subclass, 
                // and it will be called on the subclass object, which will then pass the call to the superclass implementation.
                if (matches.size() == 2)
                {
                    Class<?> class0 = matches.get(0).getObjectClass();
                    Class<?> class1 = matches.get(1).getObjectClass();
                    if (!class0.equals(class1) && class0.isAssignableFrom(class1)) // class0 is superclass of class1
                    {
                        AbstractExtractorMethodCall<?> derivedMethodCall = matches.get(1);
                        return createExtractorForMethodCall(derivedMethodCall, context);
                    }
                    else if (!class0.equals(class1) && class1.isAssignableFrom(class0))  // class1 is superclass of class0
                    {
                        AbstractExtractorMethodCall<?> derivedMethodCall = matches.get(0);
                        return createExtractorForMethodCall(derivedMethodCall, context);
                    }
                }
                // If there is more than one match, and the value of the matches is the same as the value set for defaultCustomClassname, than use that method 
                if (ValueIndexerFactory.instance().getDefaultCustomClassname() != null)
                {
                    for (AbstractExtractorMethodCall<?> curMethodCall : matches)
                    {
                        if (curMethodCall.getObjectClass().getName().equals(ValueIndexerFactory.instance().getDefaultCustomClassname()))
                        {
                            return createExtractorForMethodCall(curMethodCall, context);
                        }
                    }
                }
                throw new IndexerSpecException("Multiple methods with name: " + context.getMethodName() + " you must specify the class of the method you intend to use.  Known methods are: \n"
                    + methodCallManager.loadedExtractorMixinsToString(matches));
            }
            else if (matches.size() == 0)
            {
                List<AbstractExtractorMethodCall<?>> matchesParmWildcard = methodCallManager.getLoadedExtractorMixinsMatches(null, context.getMethodName(), -1);
                if (matchesParmWildcard.size() == 1)
                {
                    int num = (matchesParmWildcard.iterator().next().getNumParameters()-1);
                    throw new IndexerSpecException("Incorrect number of parameters to method: " + context.getMethodName() + " The known method "+methodCallManager.loadedExtractorMixinsToString(matchesParmWildcard)+
                                                   " requires "+ num  + " parameter" +((num == 1) ? "" : "s") + "\n");
                }
                else if (matchesParmWildcard.size() > 1)
                {
                    throw new IndexerSpecException("Multiple methods with name: " + context.getMethodName() + " but none of them require " + context.getParameterTypes().length + " parameters.  Known methods are: \n"
                            + methodCallManager.loadedExtractorMixinsToString(matchesParmWildcard));
                }
            }
            if (!haveShownKnownMethods)
            {
                haveShownKnownMethods = true;
                throw new IndexerSpecException("Unknown extractor method: " + context.toString() + ". Known methods are: \n"
                        + methodCallManager.loadedExtractorMixinsToString());
            }
            else
            {
                throw new IndexerSpecException("Unknown extractor method: " + context.toString());
            }
        }
        else
        {
            List<AbstractExtractorMethodCall<?>> matchesOtherContext = methodCallManager.getLoadedExtractorMixinsMatches(null, context.getMethodName(), context.getParameterTypes().length);
            if (matchesOtherContext.size() == 1)
            {
                String objName = matchesOtherContext.iterator().next().getObjectName();
                throw new IndexerSpecException("Method not found in specified class: " + context.getObjectName() + " A known method does exist in the class : " + objName + "\n");
            }
            List<AbstractExtractorMethodCall<?>> matchesParmWildcard = methodCallManager.getLoadedExtractorMixinsMatches(context.getObjectName(), context.getMethodName(), -1);
            if (matchesParmWildcard.size() == 1)
            {
                int num = (matchesParmWildcard.iterator().next().getNumParameters()-1);
                throw new IndexerSpecException("Incorrect number of parameters to method: " + context.getMethodName() + " The known method "+methodCallManager.loadedExtractorMixinsToString(matchesParmWildcard)+
                        " requires "+ num  + " parameter" +((num == 1) ? "" : "s") + "\n");
            }
            List<AbstractExtractorMethodCall<?>> matchesOtherContextParmWildCard = methodCallManager.getLoadedExtractorMixinsMatches(null, context.getMethodName(), -1);
            if (matchesOtherContextParmWildCard.size() == 1)
            {
                @SuppressWarnings("unused")
                AbstractExtractorMethodCall<?> match = matchesOtherContextParmWildCard.iterator().next();
                throw new IndexerSpecException("Specified method with name: " + context.getMethodName() + " not found.  Closest match is: \n"
                        + methodCallManager.loadedExtractorMixinsToString(matchesOtherContextParmWildCard));
            }
            else if (matchesOtherContextParmWildCard.size() > 1)
            {
                throw new IndexerSpecException("Multiple methods with name: " + context.getMethodName() + " but none of them require " + context.getParameterTypes().length + " parameters.  Known methods are: \n"
                        + methodCallManager.loadedExtractorMixinsToString(matchesOtherContextParmWildCard));
            }
            else if (!haveShownKnownMethods)
            {
                haveShownKnownMethods = true;
                throw new IndexerSpecException("Unknown extractor method: " + context.toString() + ". Known methods are: \n"
                        + methodCallManager.loadedExtractorMixinsToString());
            }
            else
            {
                throw new IndexerSpecException("Unknown extractor method: " + context.toString());
            }

        }
    }

    private AbstractValueExtractor<?> createExtractorForMethodCall(AbstractExtractorMethodCall<?> methodCall, MethodCallContext context)
    {
        if (methodCall instanceof MultiValueExtractorMethodCall)
        {
            return new MethodCallMultiValueExtractor((MultiValueExtractorMethodCall) methodCall, context.getParameters());
        }
        else if (methodCall instanceof SingleValueExtractorMethodCall)
        {
            return new MethodCallSingleValueExtractor((SingleValueExtractorMethodCall) methodCall, context.getParameters());
        }
        return(null);
    }

    @Override
    public AbstractValueExtractor<?> createExtractor(final String solrFieldName, final String[] parts)
    {
        MethodCallContext context = MethodCallContext.parseContextFromExtractorParts(parts);
        return createExtractor(solrFieldName, context);
    }
}
