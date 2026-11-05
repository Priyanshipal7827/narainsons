package com.narainsons.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.narainsons.config.SecretsConfig;
import com.narainsons.util.GsonProvider;
import com.narainsons.util.ServletUtil;

public class Fast2SmsService {

    private static final Logger LOG = Logger.getLogger(Fast2SmsService.class.getName());
    private final HttpClient client = HttpClient.newHttpClient();

    public record SmsResult(boolean sent, String providerResponse) {
    }

    public SmsResult sendOtpSms(String mobile10Digit, String otp) {
        String apiKey = SecretsConfig.get("fast2sms.api.key");
        if (ServletUtil.isBlank(apiKey)) {
            LOG.warning("Fast2SMS: secrets.properties mein fast2sms.api.key set karo");
            return new SmsResult(false, "missing_api_key");
        }

        String route = SecretsConfig.get("fast2sms.route");
        if (ServletUtil.isBlank(route)) {
            route = "dlt";
        }

        JsonObject body = new JsonObject();
        body.addProperty("route", route);
        body.addProperty("numbers", mobile10Digit);
        body.addProperty("variables_values", otp);

        if ("dlt".equalsIgnoreCase(route)) {
            body.addProperty("sender_id", SecretsConfig.get("fast2sms.sender.id"));
            // Fast2SMS DLT: "message" = Message ID from DLT Manager (NOT template text, NOT template_id field)
            String messageId = SecretsConfig.get("fast2sms.dlt.message_id");
            if (ServletUtil.isBlank(messageId)) {
                messageId = SecretsConfig.get("fast2sms.dlt.template.id");
            }
            if (ServletUtil.isBlank(messageId)) {
                return new SmsResult(false, "missing fast2sms.dlt.message_id in secrets.properties");
            }
            body.addProperty("message", messageId);
        }

        if ("dlt_manual".equalsIgnoreCase(route)) {
            body.addProperty("sender_id", SecretsConfig.get("fast2sms.sender.id"));
            body.addProperty("message", SecretsConfig.get("fast2sms.dlt.message"));
            body.addProperty("template_id", SecretsConfig.get("fast2sms.dlt.template.id"));
            body.addProperty("entity_id", SecretsConfig.get("fast2sms.dlt.entity_id"));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                    .header("authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GsonProvider.toJson(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body() == null ? "" : response.body();
            LOG.info(() -> "Fast2SMS status=" + response.statusCode() + " body=" + respBody);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new SmsResult(false, "http_" + response.statusCode() + ": " + respBody);
            }
            if (ServletUtil.isBlank(respBody)) {
                return new SmsResult(false, "empty_response");
            }
            JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
            if (json.has("return") && json.get("return").getAsBoolean()) {
                return new SmsResult(true, respBody);
            }
            return new SmsResult(false, respBody);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Fast2SMS send fail", e);
            return new SmsResult(false, e.getMessage());
        }
    }
}
