/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.builders.PointBuilder;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This mapper add a new sub fields
 * .bin Binary type
 * .bool Boolean type
 * .point GeoPoint type
 * .shape GeoShape type
 */
public class ExternalMapper extends ParametrizedFieldMapper {

    public static class Names {
        public static final String FIELD_BIN = "bin";
        public static final String FIELD_BOOL = "bool";
        public static final String FIELD_POINT = "point";
        public static final String FIELD_SHAPE = "shape";
    }

    public static class Builder extends ParametrizedFieldMapper.Builder {

        private final BinaryFieldMapper.Builder binBuilder = new BinaryFieldMapper.Builder(Names.FIELD_BIN);
        private final BooleanFieldMapper.Builder boolBuilder = new BooleanFieldMapper.Builder(Names.FIELD_BOOL);
        private final GeoPointFieldMapper.Builder latLonPointBuilder = new GeoPointFieldMapper.Builder(Names.FIELD_POINT);
        private final GeoShapeFieldMapper.Builder shapeBuilder = new GeoShapeFieldMapper.Builder(Names.FIELD_SHAPE);
        private final Mapper.Builder<?> stringBuilder;
        private final String generatedValue;
        private final String mapperName;

        public Builder(String name, String generatedValue, String mapperName) {
            super(name);
            this.stringBuilder = new TextFieldMapper.Builder(name).store(false);
            this.generatedValue = generatedValue;
            this.mapperName = mapperName;
        }

        @Override
        protected List<Parameter<?>> getParameters() {
            return Collections.emptyList();
        }

        @Override
        public ExternalMapper build(BuilderContext context) {
            context.path().add(name);
            BinaryFieldMapper binMapper = binBuilder.build(context);
            BooleanFieldMapper boolMapper = boolBuilder.build(context);
            GeoPointFieldMapper pointMapper = (GeoPointFieldMapper) latLonPointBuilder.build(context);
            AbstractShapeGeometryFieldMapper<?, ?> shapeMapper = shapeBuilder.build(context);
            FieldMapper stringMapper = (FieldMapper)stringBuilder.build(context);
            context.path().remove();

            return new ExternalMapper(name, buildFullName(context), generatedValue, mapperName, binMapper, boolMapper,
                pointMapper, shapeMapper, stringMapper, multiFieldsBuilder.build(this, context), copyTo.build());
        }
    }

    public static TypeParser parser(String mapperName, String generatedValue) {
        return new TypeParser((n, c) -> new Builder(n, generatedValue, mapperName));
    }

    static class ExternalFieldType extends TermBasedFieldType {

        private ExternalFieldType(String name, boolean indexed, boolean stored, boolean hasDocValues) {
            super(name, indexed, stored, hasDocValues, TextSearchInfo.SIMPLE_MATCH_ONLY, Collections.emptyMap());
        }

        @Override
        public String typeName() {
            return "faketype";
        }
    }

    private final String generatedValue;
    private final String mapperName;

    private final BinaryFieldMapper binMapper;
    private final BooleanFieldMapper boolMapper;
    private final GeoPointFieldMapper pointMapper;
    private final AbstractShapeGeometryFieldMapper<?, ?> shapeMapper;
    private final FieldMapper stringMapper;

    public ExternalMapper(String simpleName, String contextName,
                          String generatedValue, String mapperName,
                          BinaryFieldMapper binMapper, BooleanFieldMapper boolMapper, GeoPointFieldMapper pointMapper,
                          AbstractShapeGeometryFieldMapper<?, ?> shapeMapper, FieldMapper stringMapper,
                          MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, new ExternalFieldType(contextName, true, true, false), multiFields, copyTo);
        this.generatedValue = generatedValue;
        this.mapperName = mapperName;
        this.binMapper = binMapper;
        this.boolMapper = boolMapper;
        this.pointMapper = pointMapper;
        this.shapeMapper = shapeMapper;
        this.stringMapper = stringMapper;
    }

    @Override
    public void parse(ParseContext context) throws IOException {
        byte[] bytes = "Hello world".getBytes(Charset.defaultCharset());
        binMapper.parse(context.createExternalValueContext(bytes));

        boolMapper.parse(context.createExternalValueContext(true));

        // Let's add a Dummy Point
        double lat = 42.0;
        double lng = 51.0;
        ArrayList<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(lat, lng));
        pointMapper.parse(context.createExternalValueContext(points));

        // Let's add a Dummy Shape
        if (shapeMapper instanceof GeoShapeFieldMapper) {
            shapeMapper.parse(context.createExternalValueContext(new Point(-100, 45)));
        } else {
            PointBuilder pb = new PointBuilder(-100, 45);
            shapeMapper.parse(context.createExternalValueContext(pb.buildS4J()));
        }

        context = context.createExternalValueContext(generatedValue);

        // Let's add a Original String
        stringMapper.parse(context);

        multiFields.parse(this, context);
    }

    @Override
    protected void parseCreateField(ParseContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValueFetcher valueFetcher(MapperService mapperService, SearchLookup searchLookup, String format) {
        return new SourceValueFetcher(name(), mapperService, parsesArrayValue()) {
            @Override
            protected Object parseSourceValue(Object value) {
                return value;
            }
        };
    }

    @Override
    public Iterator<Mapper> iterator() {
        return Iterators.concat(super.iterator(), Arrays.asList(binMapper, boolMapper, pointMapper, shapeMapper, stringMapper).iterator());
    }

    @Override
    public ParametrizedFieldMapper.Builder getMergeBuilder() {
        return new Builder(simpleName(), generatedValue, mapperName);
    }

    @Override
    protected String contentType() {
        return mapperName;
    }
}
