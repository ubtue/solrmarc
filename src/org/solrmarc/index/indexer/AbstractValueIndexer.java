package org.solrmarc.index.indexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import org.marc4j.marc.Record;
import org.solrmarc.index.collector.MultiValueCollector;
import org.solrmarc.index.extractor.AbstractValueExtractor;
import org.solrmarc.index.mapping.AbstractValueMapping;

public abstract class AbstractValueIndexer<T>
{
    private String solrFieldNamesStr;
    private Collection<String> solrFieldNames;
    protected final AbstractValueExtractor<T> extractor;
    protected AbstractValueMapping<T>[] mappings;
    protected final MultiValueCollector collector;
    private String specLabel;
    protected AtomicLong totalElapsedTime;

    public AbstractValueIndexer(final String solrFieldNamesStr, final AbstractValueExtractor<T> extractor,
            final AbstractValueMapping<T>[] mappings, final MultiValueCollector collector)
    {
        setSolrFieldNamesStr(solrFieldNamesStr);
        this.extractor = extractor;
        this.mappings = mappings;
        this.collector = collector;
        totalElapsedTime = new AtomicLong(0);
    }

    public String getSpecLabel()
    {
        return specLabel;
    }

    public void setSpecLabel(String specLabel)
    {
        this.specLabel = specLabel;
    }

    @SuppressWarnings("unchecked")
    public Collection<String> getFieldData(Record record) throws Exception
    {
        long start = System.nanoTime();
        if (extractor == null) return Collections.emptyList();
        T values = extractor.extract(record);
        if (values == null)
        {
            return  Collections.emptyList();
        }
        for (final AbstractValueMapping<T> mapping : mappings)
        {
            values = mapping.map(values);
        }
        Collection<String> result = null;
        if (values instanceof Collection)
            result = collector.collect((Collection<String>)values);
        else if (values instanceof String)
            result = collector.collect(Collections.singletonList((String)values));
        long end = System.nanoTime();
        totalElapsedTime.addAndGet(end - start);
        return (result);
    }

    @SuppressWarnings("unchecked")
    public void getFieldData(Record record, Collection<String> result) throws Exception
    {
        long start = System.nanoTime();
        if (extractor == null) return;
        T values = extractor.extract(record);
        if (values == null)
        {
            return;
        }
        for (final AbstractValueMapping<T> mapping : mappings)
        {
            values = mapping.map(values);
        }
        if (values instanceof Collection)
            result.addAll(collector.collect((Collection<String>)values));
        else if (values instanceof String)
            result.addAll(collector.collect(Collections.singletonList((String)values)));
        long end = System.nanoTime();
        totalElapsedTime.addAndGet(end - start);
    }

    public Long getTotalElapsedTime()
    {
        return totalElapsedTime.get();
    }

    public Collection<String> getSolrFieldNames()
    {
        return solrFieldNames;
    }

    public String getSolrFieldNamesStr()
    {
        return solrFieldNamesStr;
    }

    public void setSolrFieldNamesStr(String solrFieldNamesStr)
    {
        this.solrFieldNamesStr = solrFieldNamesStr;
        this.solrFieldNames = splitFieldNameStr(solrFieldNamesStr);
    }

    protected static Collection<String> splitFieldNameStr(String solrFieldNamesStr)
    {
        Collection<String> result = new ArrayList<String>();
        if (result != null)
        {
            // The trim and whitespace in the pattern for split may well be unnecessary since the string
            // should have had all whitespace removed,  but just in case.
            String[] fieldNames = solrFieldNamesStr.trim().split("[ \\t]*,[ \\t]*");
            for (String fName : fieldNames)
            {
                result.add(fName);
            }
        }
        return(result);
    }

    public abstract void setIfEmpty();
    public abstract boolean getOnlyIfEmpty();

    public abstract void setIfUnique();
    public abstract boolean getOnlyIfUnique();

}
