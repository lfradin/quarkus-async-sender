package dev.lfradin.sender;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.GeneratorEmitter;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.function.BiFunction;

@ApplicationScoped
public class SenderApplication {
    private final Counter senderAppSentEvent;
    private Cancellable cancellableTask;

    public SenderApplication(MeterRegistry meterRegistry) {
        senderAppSentEvent = meterRegistry.counter("sender_app_sent_events");
    }

    /**
     * Simple event record
     */
    public record Event(Long id, String message) {
    }

    /**
     * REST endpoint for the service that receives events.
     */
    @Path("/")
    public interface TargetService {
        @POST
        @Consumes("application/json")
        Uni<Response> postEvent(Event event);
    }

    public void initEventPipeline(@Observes StartupEvent ev,
                                  @ConfigProperty(name = "sender-app.target.url") String targetUrl) {
        TargetService eventTarget = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(targetUrl)).build(TargetService.class);

        Multi<Response> processResult =
                // Generate events on demand, starting with id 1
                Multi.createFrom().generator(() -> 1L, eventGenerator())
                // Process every event, sending them on by one to target service
                .onItem().transformToUniAndConcatenate(eventTarget::postEvent)
                .onItem().invoke(() -> senderAppSentEvent.increment())
                .onTermination().invoke(()-> Log.infof("Total events sent: %s", senderAppSentEvent.count()));

        cancellableTask = processResult.subscribe()
                .with(response -> Log.debugf("Event sent with status %s", response.getStatus()),
                        failure -> Log.errorf(failure, "Failure: %s", failure.getMessage()));
    }

    public void onStop(@Observes ShutdownEvent ev) {
        cancellableTask.cancel();
    }

    private static BiFunction<Long, GeneratorEmitter<? super Event>, Long> eventGenerator() {
        return (id, gen) -> {
            gen.emit(new Event(id, "Message " + id));
            return id + 1;
        };
    }

}
