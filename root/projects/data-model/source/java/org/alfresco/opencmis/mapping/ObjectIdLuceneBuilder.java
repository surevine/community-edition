/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.opencmis.mapping;

import java.io.Serializable;
import java.util.Collection;

import org.alfresco.repo.search.impl.lucene.AnalysisMode;
import org.alfresco.repo.search.impl.lucene.LuceneFunction;
import org.alfresco.repo.search.impl.lucene.LuceneQueryParser;
import org.alfresco.repo.search.impl.querymodel.PredicateMode;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

/**
 * Lucene Builder for CMIS object id property.
 * 
 * @author andyh
 * @author dward
 */
public class ObjectIdLuceneBuilder extends AbstractLuceneBuilder
{
    private DictionaryService dictionaryService;
    
    /**
     * Construct
     * 
     * @param serviceRegistry
     */
    public ObjectIdLuceneBuilder(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

    @Override
    public String getLuceneFieldName()
    {
        return "ID";
    }

    private String getValueAsString(Serializable value)
    {
        Object converted = DefaultTypeConverter.INSTANCE.convert(dictionaryService.getDataType(DataTypeDefinition.NODE_REF), value);
        String asString = DefaultTypeConverter.INSTANCE.convert(String.class, converted);
        return asString;
    }

    @Override
    public Query buildLuceneEquality(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        String field = getLuceneFieldName();
        String stringValue = getValueAsString(value);
        return lqp.getFieldQuery(field, stringValue, AnalysisMode.IDENTIFIER, luceneFunction);
    }

    @Override
    public Query buildLuceneExists(LuceneQueryParser lqp, Boolean not) throws ParseException
    {
        if (not)
        {
            return new TermQuery(new Term("NO_TOKENS", "__"));
        } else
        {
            return new MatchAllDocsQuery();
        }
    }

    @Override
    public Query buildLuceneGreaterThan(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        throw new CmisInvalidArgumentException("Property " + PropertyIds.OBJECT_ID + " can not be used in a 'greater than' comparison");
    }

    @Override
    public Query buildLuceneGreaterThanOrEquals(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        throw new CmisInvalidArgumentException("Property " + PropertyIds.OBJECT_ID
                + " can not be used in a 'greater than or equals' comparison");
    }

    @Override
    public Query buildLuceneIn(LuceneQueryParser lqp, Collection<Serializable> values, Boolean not, PredicateMode mode)
            throws ParseException
    {
        String field = getLuceneFieldName();

        // Check type conversion

        @SuppressWarnings("unused")
        Object converted = DefaultTypeConverter.INSTANCE.convert(dictionaryService.getDataType(DataTypeDefinition.NODE_REF), values);
        Collection<String> asStrings = DefaultTypeConverter.INSTANCE.convert(String.class, values);

        if (asStrings.size() == 0)
        {
            if (not)
            {
                return new MatchAllDocsQuery();
            } else
            {
                return new TermQuery(new Term("NO_TOKENS", "__"));
            }
        } else if (asStrings.size() == 1)
        {
            String value = asStrings.iterator().next();
            if (not)
            {
                return lqp.getDoesNotMatchFieldQuery(field, value, AnalysisMode.IDENTIFIER, LuceneFunction.FIELD);
            } else
            {
                return lqp.getFieldQuery(field, value, AnalysisMode.IDENTIFIER, LuceneFunction.FIELD);
            }
        } else
        {
            BooleanQuery booleanQuery = new BooleanQuery();
            if (not)
            {
                booleanQuery.add(new MatchAllDocsQuery(), Occur.MUST);
            }
            for (String value : asStrings)
            {
                Query any = lqp.getFieldQuery(field, value, AnalysisMode.IDENTIFIER, LuceneFunction.FIELD);
                if (not)
                {
                    booleanQuery.add(any, Occur.MUST_NOT);
                } else
                {
                    booleanQuery.add(any, Occur.SHOULD);
                }
            }
            return booleanQuery;
        }
    }

    @Override
    public Query buildLuceneInequality(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        String field = getLuceneFieldName();
        String stringValue = getValueAsString(value);
        return lqp.getDoesNotMatchFieldQuery(field, stringValue, AnalysisMode.IDENTIFIER, luceneFunction);
    }

    @Override
    public Query buildLuceneLessThan(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        throw new CmisInvalidArgumentException("Property " + PropertyIds.OBJECT_ID + " can not be used in a 'less than' comparison");
    }

    @Override
    public Query buildLuceneLessThanOrEquals(LuceneQueryParser lqp, Serializable value, PredicateMode mode,
            LuceneFunction luceneFunction) throws ParseException
    {
        throw new CmisInvalidArgumentException("Property " + PropertyIds.OBJECT_ID + " can not be used in a 'less than or equals' comparison");
    }

    @Override
    public Query buildLuceneLike(LuceneQueryParser lqp, Serializable value, Boolean not) throws ParseException
    {
        String field = getLuceneFieldName();
        String stringValue = getValueAsString(value);

        if (not)
        {
            BooleanQuery booleanQuery = new BooleanQuery();
            booleanQuery.add(new MatchAllDocsQuery(), Occur.MUST);
            booleanQuery.add(lqp.getLikeQuery(field, stringValue, AnalysisMode.IDENTIFIER), Occur.MUST_NOT);
            return booleanQuery;
        } else
        {
            return lqp.getLikeQuery(field, stringValue, AnalysisMode.IDENTIFIER);
        }
    }

    @Override
    public String getLuceneSortField(LuceneQueryParser lqp)
    {
        return getLuceneFieldName();
    }

}
