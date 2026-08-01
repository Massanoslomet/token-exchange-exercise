package no.dnb.exercise.frontend_service;

public class FrontendException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public FrontendException(int statusCode, String responseBody) {
        super(responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}