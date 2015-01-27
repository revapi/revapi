/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.basic;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.ValidationResult;

import org.jboss.dmr.ModelNode;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurableElementFilterTest {

    @Test
    public void testInvalidConfig_noFilters() throws Exception {
        ConfigurationValidator validator = new ConfigurationValidator();

        String json = "{\"revapi\" : {\"filter\" : { }}}";

        ValidationResult result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());

        Assert.assertFalse(result.isSuccessful());
    }

    @Test
    public void testInvalidConfig_noDefsForFilter() throws Exception {
        ConfigurationValidator validator = new ConfigurationValidator();

        String json = "{\"revapi\" : {\"filter\" : { \"elements\" : { \"include\" : [] }}}}";
        ValidationResult result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());

        json = "{\"revapi\" : {\"filter\" : { \"elements\" : { \"exclude\" : [] }}}}";
        result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());

        json = "{\"revapi\" : {\"filter\" : { \"archives\" : { \"exclude\" : {} }}}}";
        result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());
    }

    //TODO add tests
}
