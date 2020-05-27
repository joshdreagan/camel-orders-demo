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

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileUploaderConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);
  
  @Autowired
  private SplitterProperties props;
  
  @Override
  public void configure() throws Exception {
    
    rest("/files")
      .post("/")
        .consumes("multipart/form-data")
        .produces("text/plain")
        .bindingMode(RestBindingMode.off)
        .to("direct:fileUpload")
    ;
    
    from("direct:fileUpload")
      .log(LoggingLevel.INFO, log, "Uploading file...")
      .unmarshal().mimeMultipart()
      .convertBodyTo(byte[].class)
      .setHeader(Exchange.FILE_NAME, simple("upload-${exchangeId}.xml"))
      .toF("file:%s", props.getDir())
      .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
      .setBody(header(Exchange.FILE_NAME))
    ;
  }
}
