package org.solrmarc.index.utils;

import org.solrmarc.driver.BootableMain;
import org.solrmarc.driver.LoggerDelegator;
import org.solrmarc.index.extractor.AbstractValueExtractorFactory;
import org.solrmarc.index.extractor.impl.custom.Mixin;
import org.solrmarc.index.mapping.AbstractValueMappingFactory;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.matchprocessor.InterfaceMatchProcessor;
import io.github.lukehutch.fastclasspathscanner.matchprocessor.SubclassMatchProcessor;

import java.util.LinkedHashSet;
import java.util.Set;

public class FastClasspathUtils extends ClasspathUtils
{
    public static final LoggerDelegator logger = new LoggerDelegator(FastClasspathUtils.class);

    private void getMatchingClasses()
    {
        extractors = new LinkedHashSet<>();
        mappers = new LinkedHashSet<>();
        mixins = new LinkedHashSet<>();
        try
        {
            Class.forName("bsh.Interpreter");
        }
        catch (ClassNotFoundException e)
        {
            logger.warn("Cannot find BeanShell Interpreter class:  any index specification that uses BeanShell scripts will cause an error:" + e.getMessage());
        }
        FastClasspathScanner scanner = new FastClasspathScanner()
            .matchSubclassesOf(AbstractValueExtractorFactory.class, new SubclassMatchProcessor<AbstractValueExtractorFactory>()
            {
                @Override
                public void processMatch(Class<? extends AbstractValueExtractorFactory> matchingClass)
                {
                    logger.debug("Subclass of AbstractValueExtractorFactory: " + matchingClass);
                    extractors.add(matchingClass);
                }
            })
            .matchSubclassesOf(AbstractValueMappingFactory.class, new SubclassMatchProcessor<AbstractValueMappingFactory>()
            {
                @Override
                public void processMatch(Class<? extends AbstractValueMappingFactory> matchingClass)
                {
                    logger.debug("Subclass of AbstractValueMappingFactory: " + matchingClass);
                    mappers.add(matchingClass);
                }
            })
            .matchClassesImplementing(Mixin.class, new InterfaceMatchProcessor<Mixin>()
            {
                @Override
                public void processMatch(Class<? extends Mixin> matchingClass)
                {
                    logger.debug("Subclass of Mixin: " + matchingClass);
                    mixins.add(matchingClass);
                }
            });
        scanner.scan();
    }

    @Override
    public Set<Class<? extends AbstractValueExtractorFactory>> getExtractorFactoryClasses()
    {
        if (extractors == null)
        {
            getMatchingClasses();
        }
        return extractors;
    }

    @Override
    public Set<Class<? extends AbstractValueMappingFactory>> getMappingFactoryClasses()
    {
        if (mappers == null)
        {
            getMatchingClasses();
        }
        return mappers;
    }

    @Override
    public Set<Class<? extends Mixin>> getMixinClasses()
    {
        if (mixins == null)
        {
            getMatchingClasses();
        }
        return mixins;
    }

    private void getMatchingBootableClasses()
    {
        bootables = new LinkedHashSet<>();
        FastClasspathScanner scanner = new FastClasspathScanner()
            .matchSubclassesOf(BootableMain.class, new SubclassMatchProcessor<BootableMain>()
            {
                @Override
                public void processMatch(Class<? extends BootableMain> matchingClass)
                {
                    logger.debug("Subclass of BootableMain: " + matchingClass);
                    bootables.add(matchingClass);
                }
            });
        scanner.scan();
    }

    @Override
    public Set<Class<? extends BootableMain>> getBootableMainClasses()
    {
        if (bootables == null)
        {
            getMatchingBootableClasses();
        }
        return bootables;
    }
}
