package ma.mobility.railbridge;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Supplier;

/**
 * CORBA n'est pas instrumente automatiquement par l'agent OpenTelemetry :
 * on cree NOUS-MEMES un span autour de chaque appel IIOP, pour que la trace
 * d'une reservation montre bien le passage par le monde CORBA.
 */
final class Traced {
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("rail-bridge");

    private Traced() {}

    static <T> T run(String method, Supplier<T> body) {
        Span span = TRACER.spanBuilder("RailBooking/" + method)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "corba")
                .setAttribute("rpc.service", "mobility.rail.RailBooking")
                .setAttribute("rpc.method", method)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }
}
