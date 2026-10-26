package edu.sjsu.cmpe172.scheduler.notification;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;

/**
 * Calls the notification service over HTTP: POST {base-url}/api/notifications with the
 * {@link NotificationRequest} as JSON. Every call has a connect and a read timeout, so a
 * slow or dead service costs at most a few seconds on a background thread.
 */
@Component
public class RestNotificationGateway implements NotificationGateway {

    private final RestClient http;
    private final Environment env;

    public RestNotificationGateway(RestClient.Builder builder, NotificationProperties props, Environment env) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        this.http = builder.requestFactory(factory).build();
        this.env = env;
    }

    @Override
    public NotificationReceipt send(NotificationRequest request) {
        try {
            NotificationReceipt receipt = http.post()
                    .uri(baseUrl() + "/api/notifications")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(NotificationReceipt.class);
            // A 2xx answer means the service accepted it, even if it sent no body.
            return receipt != null ? receipt : new NotificationReceipt(null, "ACCEPTED", null);
        } catch (ResourceAccessException e) {
            // Connection refused, unknown host, or a connect/read timeout.
            throw new NotificationException("Notification service unreachable: " + e.getMessage(), true, e);
        } catch (HttpClientErrorException e) {
            throw new NotificationException("Notification service rejected the request: " + e.getStatusCode(), false, e);
        } catch (RestClientResponseException e) {
            throw new NotificationException("Notification service error: " + e.getStatusCode(), true, e);
        } catch (RestClientException e) {
            // A read timeout can also surface here, while the answer is being read.
            if (causedByTimeout(e)) {
                throw new NotificationException("Notification service timed out", true, e);
            }
            throw new NotificationException("Notification call failed: " + e.getMessage(), true, e);
        }
    }

    private static boolean causedByTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read on every call, not once at startup: the default points at this same app
     * (${local.server.port}), which is only known after the web server has started.
     */
    private String baseUrl() {
        return env.getRequiredProperty("notification.base-url");
    }
}
