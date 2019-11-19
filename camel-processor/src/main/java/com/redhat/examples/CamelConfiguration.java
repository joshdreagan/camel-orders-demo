/*
 * Copyright 2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.examples;

import com.redhat.examples.json.ProcessedOrder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);
  
  @Bean
  private AggregationStrategy descriptionEnrichmentStrategy() {
    return (Exchange original, Exchange resource) -> {
      if (resource.getIn().getBody() != null) {
        original.getIn().getBody(ProcessedOrder.class).setDescription(resource.getIn().getBody(String.class));
      }
      return original;
    };
  }
  
  @Override
  public void configure() throws Exception {
    from("amqp:queue:raw?connectionFactory=#pooledJmsConnectionFactory&acknowledgementModeName=CLIENT_ACKNOWLEDGE")
      .log(LoggingLevel.INFO, log, "Picked up raw order: [${body}]")
      .unmarshal().jaxb("com.redhat.examples.xml")
      .to("dozer:rawToProcessed?sourceModel=com.redhat.examples.xml.RawOrder&targetModel=com.redhat.examples.json.ProcessedOrder")
      .enrich()
        .constant("direct:fetchDescription")
        .aggregationStrategyRef("descriptionEnrichmentStrategy")
      .end()
      .marshal().json(JsonLibrary.Jackson, false)
      .log(LoggingLevel.INFO, log, "Sending processed order: [${body}]")
      .to(ExchangePattern.InOnly, "amqp:queue:processed?connectionFactory=#pooledJmsConnectionFactory")
    ;
    
    from("direct:fetchDescription")
      .to("sql:select description from ITEM_DESCRIPTION where id=:#${body.item}?dataSource=#dataSource&outputType=SelectOne")
    ;
  }
}
