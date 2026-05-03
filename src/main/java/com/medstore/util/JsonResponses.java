package com.medstore.util;

import com.google.gson.Gson;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonResponses {

    private static final Gson gson = new Gson();

    private JsonResponses() {
    }

    public static Gson gson() {
        return gson;
    }

    public static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/json");
        gson.toJson(body, resp.getWriter());
    }

    public static void error(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, java.util.Map.of("error", message));
    }
}
