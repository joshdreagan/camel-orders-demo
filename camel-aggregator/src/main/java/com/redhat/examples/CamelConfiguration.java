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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.toolbox.AggregationStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);
  
  @Autowired
  private AggregatorProperties props;
  
  @Bean
  private AggregationStrategy orderAggregationStrategy() {
    return AggregationStrategies
            .flexible()
            .accumulateInCollection(ArrayList.class)
            .storeInBody()
            .pick(new SimpleExpression("${body}"))
          ;
  }
  
  @Override
  public void configure() throws Exception {
    
    rest("/files")
      .get("/")
        .produces("text/plain")
        .bindingMode(RestBindingMode.off)
        .to("direct:listFiles")
      .get("/{filename}")
        .produces("application/json")
        .bindingMode(RestBindingMode.off)
        .to("direct:getFile")
    ;
    
    from("direct:listFiles")
      .log(LoggingLevel.INFO, log, String.format("Listing files in [%s]", props.getDir()))
      .process((Exchange exchange) -> { 
        if (Files.exists(Paths.get(props.getDir()))) {
          Stream<Path> walk = Files.walk(Paths.get(props.getDir()));
          List<String> files = walk.filter(Files::isRegularFile).map(x -> x.getFileName().toString()).collect(Collectors.toList());
          exchange.getIn().setBody(String.join("\n", files)); 
        }
      })
      .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
    ;
    
    from("direct:getFile")
      .log(LoggingLevel.INFO, log, "Getting file [${header.filename}]")
      .routingSlip(simpleF("language:constant:file:%s${headers.filename}?contentCache=false", props.getDir()))
      .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    ;
    
    from("amqp:queue:processed?connectionFactory=#pooledJmsConnectionFactory&acknowledgementModeName=CLIENT_ACKNOWLEDGE")
      .log(LoggingLevel.INFO, log, "Picked up processed order: [${body}]")
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .aggregate()
        .simple("${body[customer]}")
        .aggregationStrategyRef("orderAggregationStrategy")
        .completionTimeout(5000L)
        .completionSize(10)
          .log(LoggingLevel.INFO, log, "Completing aggregate order: [${exchangeProperty.CamelAggregatedCorrelationKey}]")
          .transform().groovy("['orders':request.body]")
          .marshal().json(JsonLibrary.Jackson, true)
          .setHeader("CurrentTimeMillis", method(System.class, "currentTimeMillis"))
          .to(ExchangePattern.InOnly, String.format("file:%s?fileName=order-${exchangeProperty.CamelAggregatedCorrelationKey}-${header.CurrentTimeMillis}.json", props.getDir()))
      .end()
    ;
  }
}
