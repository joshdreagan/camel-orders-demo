# camel-orders-demo

![Demo Architecture](./images/demo_architecture.png)

## Requirements

- [Apache Maven 3.x](http://maven.apache.org)
- [Red Hat AMQ Streams 2.x](https://developers.redhat.com/products/amq/overview)
- [MySQL 8](https://www.mysql.com/oem/)
  - [Docker Image](https://hub.docker.com/_/mysql)

## Preparing

Install and run Red Hat AMQ Streams [https://access.redhat.com/documentation/en-us/red_hat_amq_streams/2.4/html/getting_started_with_amq_streams_on_openshift]

Install and run MySQL [https://dev.mysql.com/doc/refman/8.0/en/installing.html]

_Note: For my tests, I chose to run the docker image [https://hub.docker.com/_/mysql]. You can run it using the command `podman run --name mysql -e MYSQL_DATABASE=sampledb -e MYSQL_ROOT_PASSWORD=Abcd1234 -p 3306:3306 -d mysql:8`. You can then connect and run SQL statements using the command `podman exec -it mysql mysql -uroot -p`._

Build the project source code

```
cd $PROJECT_ROOT
mvn clean install
```

## Running the example standalone (should be done in 3 separate terminal windows/tabs)

```
cd $PROJECT_ROOT/camel-splitter
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
cd $PROJECT_ROOT/camel-processor
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=7070"
cd $PROJECT_ROOT/camel-aggregator
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

## Running the example in OpenShift

```
oc new-project demo
cd $PROJECT_ROOT/camel-splitter
mvn -P openshift clean install oc:deploy
cd $PROJECT_ROOT/camel-processor
mvn -P openshift clean install oc:deploy
cd $PROJECT_ROOT/camel-aggregator
mvn -P openshift clean install oc:deploy
```

## Testing the code

_Note: If running on OpenShift, replace the URLs below with the OpenShift route._

To upload order data you can use `curl` (as seen below), or you can use the upload form at 'http://localhost:8080/upload.html'.

```
curl -X POST -F '@file=@./src/test/data/orders-01.xml' 'http://localhost:8080/camel/files/'
```

To list the processed files you can use `curl` (as seen below), or you can use the files page at 'http://localhost:9090/files.html':

```
# To list all files
curl -X GET -H 'Accept: text/plain' 'http://localhost:9090/camel/files/'
# To see the contents of a file (replace the file name in the url)
curl -X GET -H 'Accept: application/json' 'http://localhost:9090/camel/files/orders-x-xxxxxxxxxx.json'
```
