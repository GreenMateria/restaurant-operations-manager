package ca.foodinventory.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LambdaApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ApiRoutes ROUTES = new ApiRoutes();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String method = event.getRequestContext() == null
                || event.getRequestContext().getHttp() == null
                ? ""
                : event.getRequestContext().getHttp().getMethod();
        String stage = event.getRequestContext() == null
                ? ""
                : event.getRequestContext().getStage();
        String path = stripStagePrefix(event.getRawPath(), stage);

        ApiRoutes.ApiResult result = ROUTES.handle(method, path, headers(event), body(event));

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(result.statusCode())
                .withHeaders(Map.of("Content-Type", result.contentType()))
                .withBody(result.body())
                .build();
    }

    private String body(APIGatewayV2HTTPEvent event) {
        if (event.getBody() == null) {
            return "";
        }

        if (Boolean.TRUE.equals(event.getIsBase64Encoded())) {
            return new String(
                    Base64.getDecoder().decode(event.getBody()),
                    StandardCharsets.UTF_8
            );
        }

        return event.getBody();
    }

    private Map<String, List<String>> headers(APIGatewayV2HTTPEvent event) {
        if (event.getHeaders() == null) {
            return Map.of();
        }

        return event.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.of(entry.getValue())
                ));
    }

    private String stripStagePrefix(String path, String stage) {
        if (path == null || stage == null || stage.isBlank()) {
            return path;
        }

        String prefix = "/" + stage;
        if (path.equals(prefix)) {
            return "/";
        }

        if (path.startsWith(prefix + "/")) {
            return path.substring(prefix.length());
        }

        return path;
    }
}
