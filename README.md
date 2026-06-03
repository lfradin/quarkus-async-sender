# reactive-sender

This project run reactive stream calls to a target service. It exhibits a memory leak occurring in quarkus 3.21.1 and above.

## Running the application in dev mode

Before you run the application, you should run the docker compose file running the necessary
services for the test (an lgtm image for Grafana dashboards, and an nginx service as a mock target
for the REST calls the application makes).

```shell script
docker compose -f compose-run-manually.yaml up -d
```

You can run your application in dev mode that enables live coding using:
(we limit the memory usage to show the memory leak)
```shell script
./mvnw quarkus:dev -Djvm.args="-Xmx100m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError"
```
With quarkus 3.21.1 and above, the application will end up with an out-of-memory error after about 120000 calls.

With quarkus 3.21.0 and below, the application can run indefinitely (at least since quarkus 2.3.0-Final).

Taking a heap dump shows that there are about as many DuplicatedContext as there are calls from the application. 

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -Xmx100m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -Xmx100m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -jar target/*-runner.jar`.

